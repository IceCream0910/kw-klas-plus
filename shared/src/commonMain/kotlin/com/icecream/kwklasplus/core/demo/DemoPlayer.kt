package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.web.PlayerWebScripts
import com.icecream.kwklasplus.core.web.WebScript
import io.ktor.http.Url

object DemoPlayer {
    fun installController(): WebScript =
        WebScript("""
            (function(){
              var video=document.querySelector('video');if(!video)return;
              var events={PLAY:'play',PAUSE:'pause',MUTE:'mute',UNMUTE:'unmute',SEEK_END:'seek',CHANGE_PLAYBACK_RATE:'rate',FULL_SCREEN:'full',CLOSE_FULL_SCREEN:'inline'};
              var target={fire:function(event,value){switch(event){
                case 'play':video.play().catch(function(){});break;
                case 'pause':video.pause();break;
                case 'mute':video.muted=true;break;
                case 'unmute':video.muted=false;break;
                case 'seek':if(Number.isFinite(video.duration))video.currentTime=Math.max(0,Math.min(video.duration,Number(value)));break;
                case 'rate':video.playbackRate=Number(value);break;
                case 'full':if(video.webkitEnterFullscreen)video.webkitEnterFullscreen();else if(video.requestFullscreen)video.requestFullscreen();break;
                case 'inline':if(video.webkitExitFullscreen)video.webkitExitFullscreen();else if(document.exitFullscreen)document.exitFullscreen();break;
              }}};
              var player={_eventTarget:target,changeCurrTimeManually:function(value){target.fire('seek',value)}};
              Object.defineProperties(player,{
                _duration:{get:function(){return Number.isFinite(video.duration)?video.duration:0}},
                _currTime:{get:function(){return video.currentTime}},
                _isPlaying:{get:function(){return !video.paused&&!video.ended}},
                _isMuted:{get:function(){return video.muted},set:function(value){video.muted=value}},
                _isFullScreen:{get:function(){return !!document.fullscreenElement||!!video.webkitDisplayingFullscreen}}
              });
              window.VCPlayControllerEvent=events;window.VCPlayControllerMedia={MOVING_TIME:10};
              window.bcPlayController={getPlayController:function(){return player},_uniPlayerEventTarget:target};
              if(typeof window.${'$'}!=='function')window.${'$'}=function(){return {text:function(){return String(video.playbackRate)},remove:function(){}}};
            })();
        """.trimIndent() + PlayerWebScripts.monitorStateWhenReady().reveal())

    const val URL = "https://klasplus.yuntae.in/demo/native-player"

    fun isPlayerUrl(url: String): Boolean = runCatching {
        if (url != url.trim() || url.any(Char::isISOControl)) return false
        val parsed = Url(url)
        parsed.protocol.name == "https" && parsed.host == "klasplus.yuntae.in" &&
            parsed.port == 443 && parsed.user == null && parsed.password == null &&
            parsed.encodedPath in setOf("/demo/native-player", "/demo/native-player.html")
    }.getOrDefault(false)

    fun html(): String = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>프로그래밍 시작하기 · 데모</title><style>html,body{margin:0;background:#000;height:100%;overflow:hidden}video{width:100%;height:100%;object-fit:contain}</style></head><body><video playsinline webkit-playsinline autoplay preload="auto" src="https://klasplus.yuntae.in/demo/lecture.mp4"></video><script>
const v=document.querySelector('video');
window.VCPlayControllerEvent={PLAY:'play',PAUSE:'pause',MUTE:'mute',UNMUTE:'unmute',SEEK_END:'seek',CHANGE_PLAYBACK_RATE:'rate',FULL_SCREEN:'full',CLOSE_FULL_SCREEN:'inline'};
window.VCPlayControllerMedia={MOVING_TIME:10};
const target={fire:function(event,value){switch(event){case 'play':v.play().catch(()=>{});break;case 'pause':v.pause();break;case 'mute':v.muted=true;break;case 'unmute':v.muted=false;break;case 'seek':v.currentTime=Math.max(0,Math.min(v.duration||0,value));break;case 'rate':v.playbackRate=value;break;case 'full':if(v.webkitEnterFullscreen)v.webkitEnterFullscreen();else v.requestFullscreen?.();break;case 'inline':document.exitFullscreen?.();break;}}};
const player={_eventTarget:target,changeCurrTimeManually:function(value){target.fire('seek',value)}};
Object.defineProperties(player,{_duration:{get:()=>v.duration||0},_currTime:{get:()=>v.currentTime},_isPlaying:{get:()=>!v.paused},_isMuted:{get:()=>v.muted,set:value=>v.muted=value},_isFullScreen:{get:()=>!!document.fullscreenElement||!!v.webkitDisplayingFullscreen}});
window.bcPlayController={getPlayController:()=>player,_uniPlayerEventTarget:target};
window.${'$'}=function(){return {text:()=>String(v.playbackRate),remove:()=>{}}};
v.addEventListener('loadedmetadata',()=>{const start=Number(new URLSearchParams(location.search).get('start')||0);if(Number.isFinite(start)&&start>0)v.currentTime=Math.min(start,Math.max(0,v.duration-0.1));window.dispatchEvent(new Event('klas-demo-video-ready'));});
</script></body></html>
"""
}
