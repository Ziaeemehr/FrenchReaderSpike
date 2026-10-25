package com.ziaee.frenchreader.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class FoundComputer(val id: String, val name: String, val host: String, val port: Int)

object LocalServerFinder {
    private val resolveMutex = Mutex()
    private val refindScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var flight: Deferred<List<FoundComputer>>? = null

    suspend fun refind(context: Context, id: String): FoundComputer? {
        val shared = synchronized(this) {
            flight?.takeIf { !it.isCompleted } ?: refindScope.async {
                discover(context.applicationContext, 4000)
            }.also { flight = it }
        }
        return shared.await().firstOrNull { it.id == id }
    }

    // One native resolve at a time: a second one while Android still runs the first fails with
    // FAILURE_ALREADY_ACTIVE. A resolve that outlives the caller is stopped where Android allows it
    // (API 34+); on older versions the permit is held until its callback, or at most 10 s.
    @Suppress("DEPRECATION")
    private suspend fun resolve(manager: NsdManager, service: NsdServiceInfo): NsdServiceInfo? {
        resolveMutex.lock()
        val released = java.util.concurrent.atomic.AtomicBoolean(false)
        fun release() { if (released.compareAndSet(false, true)) resolveMutex.unlock() }
        var listener: NsdManager.ResolveListener? = null
        try {
            return withTimeoutOrNull(3_000) {
                suspendCancellableCoroutine { continuation ->
                    val callback = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                            release()
                            if (continuation.isActive) continuation.resume(null)
                        }
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            release()
                            if (continuation.isActive) continuation.resume(info)
                        }
                        override fun onResolutionStopped(info: NsdServiceInfo) {
                            release()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                    listener = callback
                    try {
                        manager.resolveService(service, callback)
                    } catch (error: Exception) {
                        release()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        } finally {
            if (!released.get()) {
                val pending = listener
                if (pending != null && android.os.Build.VERSION.SDK_INT >= 34) {
                    // onResolutionStopped releases; the delayed release covers a stop that never reports.
                    runCatching { manager.stopServiceResolution(pending) }
                }
                refindScope.launch { delay(10_000); release() }
            }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun discover(context: Context, timeoutMs: Long = 4000): List<FoundComputer> = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val found = linkedMapOf<String, FoundComputer>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) { queue.close() }
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onServiceFound(service: NsdServiceInfo) { queue.trySend(service) }
        }
        try {
            manager.discoverServices("_frenchreader._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            withTimeoutOrNull(timeoutMs) {
                for (service in queue) {
                    val resolved = resolve(manager, service) ?: continue
                    val host = resolved.host?.hostAddress ?: continue
                    val port = resolved.port
                    val txtId = resolved.attributes["id"]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
                    val info = if (txtId == null) try {
                        readComputerInfo(host, port)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null } else null
                    val id = txtId ?: info?.id ?: continue
                    found[id] = FoundComputer(id, info?.name ?: computerDisplayName(resolved.serviceName), host, port)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Discovery can be unavailable on a network; the manual USB address remains available.
        } finally {
            runCatching { manager.stopServiceDiscovery(listener) }
            queue.close()
        }
        found.values.toList()
    }
}

internal fun readComputerInfo(host: String, port: Int): ComputerInfo {
    val root = computerBaseUrl(host, port, "llm").removeSuffix("/llm")
    val connection = URL("$root/frenchreader/info").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 2000
        connection.readTimeout = 2000
        connection.instanceFollowRedirects = false
        check(connection.responseCode in 200..299)
        return parseComputerInfo(connection.inputStream.bufferedReader().use { it.readText() })
    } finally { connection.disconnect() }
}

internal fun verifyComputer(computer: FoundComputer) {
    check(readComputerInfo(computer.host, computer.port).id == computer.id)
}
