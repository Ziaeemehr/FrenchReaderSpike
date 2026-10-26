package com.ziaee.frenchreader.ui.activity

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ziaee.frenchreader.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Final flushes outlive a screen's composition and ViewModel.
private val activityWrites = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal enum class ScreenActivity { READING, WATCHING }

internal class ScreenActivityTimer(
    private val timer: ActivityTimer,
    private val write: (Map<String, Long>) -> Unit
) {
    private var resumed = false
    private var eligible = false

    fun setResumed(value: Boolean) {
        resumed = value
        timer.setEnabled(resumed && eligible)
        if (!value) flush()
    }

    fun setEligible(value: Boolean) {
        eligible = value
        timer.setEnabled(resumed && eligible)
    }

    fun interact() = timer.interact()
    fun flush() = write(timer.drain())
}

@Composable
internal fun rememberScreenActivityTimer(key: Any, activity: ScreenActivity, eligible: Boolean): ScreenActivityTimer {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val timer = remember(key, activity, context) {
        val dao = AppDatabase.get(context).activityLogDao()
        ScreenActivityTimer(ActivityTimer(activity == ScreenActivity.READING, SystemClock::elapsedRealtime, System::currentTimeMillis)) { rows ->
            if (rows.isNotEmpty()) activityWrites.launch {
                rows.forEach { (date, ms) ->
                    if (activity == ScreenActivity.READING) dao.addReading(date, ms) else dao.addWatching(date, ms)
                }
            }
        }
    }
    SideEffect { timer.setEligible(eligible) }
    DisposableEffect(timer, lifecycle) {
        timer.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ ->
            timer.setResumed(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        onDispose {
            timer.setResumed(false)
            lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(timer) {
        while (true) {
            delay(15_000)
            timer.flush()
        }
    }
    return timer
}
