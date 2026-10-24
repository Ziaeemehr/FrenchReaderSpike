package com.ziaee.frenchreader.watch

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

class YoutubePlayerController {
    internal var webView: WebView? = null
    internal var ready = false
    private var pendingSeek: Long? = null
    private var pendingRate: Float? = null
    private var pendingGeneration = 0
    private var pendingPlay: Boolean? = null

    fun play() { pendingPlay = true; send("player.playVideo()") }
    fun pause() { pendingPlay = false; send("player.pauseVideo()") }
    fun seekTo(ms: Long, playbackGeneration: Int = 0) {
        pendingGeneration = playbackGeneration
        pendingSeek = ms.coerceAtLeast(0)
        send("playbackGeneration=$pendingGeneration;player.seekTo(${pendingSeek!! / 1000.0},true)")
    }
    fun setPlaybackRate(rate: Float) {
        if (!rate.isFinite() || rate <= 0) return
        pendingRate = rate
        send("player.setPlaybackRate($rate)")
    }
    internal fun onReady() {
        ready = true
        pendingSeek?.let { seekTo(it, pendingGeneration) }
        pendingRate?.let(::setPlaybackRate)
        pendingPlay?.let { if (it) play() else pause() }
    }
    private fun send(script: String) {
        if (ready) webView?.evaluateJavascript(script, null)
    }
}

/** Calls from the player page; they arrive on a WebView thread and are posted to the main thread. */
private class WatchBridge(
    private val controller: YoutubePlayerController,
    private val onTime: (Long, Int) -> Unit,
    private val onState: (Int, Int) -> Unit,
    private val onError: (Int) -> Unit
) {
    @JavascriptInterface fun ready() {
        controller.webView?.post { controller.onReady() }
    }
    @JavascriptInterface fun time(seconds: Double, generation: Int) {
        if (seconds.isFinite() && seconds >= 0) controller.webView?.post { onTime((seconds * 1000).toLong(), generation) }
    }
    @JavascriptInterface fun state(value: Int, generation: Int) {
        controller.webView?.post { onState(value, generation) }
    }
    @JavascriptInterface fun error(code: Int) {
        controller.webView?.post { onError(code) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubePlayerView(
    videoId: String,
    controller: YoutubePlayerController,
    onTime: (Long, Int) -> Unit,
    onState: (Int, Int) -> Unit,
    onError: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeCallback = rememberUpdatedState(onTime)
    val stateCallback = rememberUpdatedState(onState)
    val errorCallback = rememberUpdatedState(onError)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                        request.isForMainFrame
                }
                webChromeClient = WebChromeClient()
                addJavascriptInterface(
                    WatchBridge(controller, { time, generation -> timeCallback.value(time, generation) },
                        { value, generation -> stateCallback.value(value, generation) }, { errorCallback.value(it) }),
                    "WatchBridge"
                )
                controller.webView = this
            }
        },
        update = { view ->
            if (view.tag != videoId) {
                view.tag = videoId
                controller.ready = false
                view.loadDataWithBaseURL("https://www.youtube-nocookie.com", playerHtml(videoId), "text/html", "UTF-8", null)
            }
        },
        onRelease = { view ->
            controller.ready = false
            controller.webView = null
            view.removeJavascriptInterface("WatchBridge")
            view.stopLoading()
            view.destroy()
        }
    )
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) controller.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            controller.pause()
            lifecycle.removeObserver(observer)
        }
    }
}

private fun playerHtml(videoId: String): String {
    require(YoutubeUrl.videoId(videoId) == videoId)
    return """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;background:#000;overflow:hidden}#player{position:fixed;top:0;left:0;width:100%;height:100%;border:0}</style></head>
        <body><div id="player"></div><script>
        var player,playbackGeneration=0;
        function onYouTubeIframeAPIReady(){
          player=new YT.Player('player',{videoId:'$videoId',width:'100%',height:'100%',host:'https://www.youtube-nocookie.com',
            playerVars:{enablejsapi:1,playsinline:1,rel:0,origin:'https://www.youtube-nocookie.com'},
            events:{onReady:function(){WatchBridge.ready();},
              onStateChange:function(e){WatchBridge.state(e.data,playbackGeneration);},
              onError:function(e){WatchBridge.error(e.data);}}});
        }
        setInterval(function(){if(player&&player.getCurrentTime) WatchBridge.time(player.getCurrentTime(),playbackGeneration);},200);
        </script><script src="https://www.youtube.com/iframe_api"></script></body></html>
    """.trimIndent()
}
