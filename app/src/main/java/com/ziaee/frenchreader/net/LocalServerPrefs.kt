package com.ziaee.frenchreader.net

import android.content.Context
import com.ziaee.frenchreader.translate.llm.LlmSecrets
import org.json.JSONObject

data class PairedComputer(val id: String, val name: String, val host: String, val port: Int)
internal data class ComputerInfo(val id: String, val name: String, val llm: Boolean, val tts: Boolean)

internal fun computerDisplayName(name: String): String = name.removeSuffix(" (French Reader)")
internal fun validComputerCode(code: String): Boolean = Regex("[0-9]{6}").matches(code)
internal fun computerBaseUrl(host: String, port: Int, service: String): String {
    require(service == "llm" || service == "tts")
    // An IPv6 link-local zone ("fe80::1%wlan0") must be escaped inside a URL.
    val address = if (':' in host && !host.startsWith('[')) "[${host.replace("%", "%25")}]" else host
    return "http://$address:$port/$service"
}
internal fun parseComputerInfo(json: String): ComputerInfo {
    val obj = JSONObject(json)
    require(obj.getInt("version") == 1)
    val id = obj.getString("id").also { require(it.isNotBlank()) }
    val services = obj.getJSONObject("services")
    return ComputerInfo(id, computerDisplayName(obj.getString("name")), services.getBoolean("llm"), services.getBoolean("tts"))
}

/** Pairing metadata is deliberately absent from LocalBackup's preference allowlist. */
class LocalServerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("local_server_prefs", Context.MODE_PRIVATE)
    private val secrets = LlmSecrets(context)
    val paired: PairedComputer?
        get() {
            val id = prefs.getString("pairedId", null) ?: return null
            val host = prefs.getString("host", null) ?: return null
            return PairedComputer(id, prefs.getString("pairedName", "").orEmpty(), host, prefs.getInt("port", 8765))
        }
    fun pair(computer: FoundComputer, code: String) {
        require(validComputerCode(code))
        secrets.set("computer_code", code)
        prefs.edit().putString("pairedId", computer.id).putString("pairedName", computer.name)
            .putString("host", computer.host).putInt("port", computer.port).apply()
    }
    fun update(host: String, port: Int) {
        prefs.edit().putString("host", host).putInt("port", port).apply()
    }
    fun forget() {
        prefs.edit().clear().apply()
        secrets.remove("computer_code")
    }
    fun baseUrl(service: String): String? = paired?.let { computerBaseUrl(it.host, it.port, service) }
}
