package com.icecream.kwklasplus.core.web

object KlasWebAutomationScripts {
    fun openLecture(yearSemester: String, subjectId: String): WebScript = WebScript(
        "appModule.goLctrum(${JavaScriptEncoder.encodeText(yearSemester)},${JavaScriptEncoder.encodeText(subjectId)});",
    )

    fun openLectureWhenReady(
        yearSemester: String,
        subjectId: String,
        maxRetries: Int = 20,
        intervalMs: Int = 250,
    ): WebScript {
        require(maxRetries > 0)
        require(intervalMs > 0)
        val call = openLecture(yearSemester, subjectId).reveal()
        return WebScript(
            "(function(){function go(n){" +
                "if(window.appModule&&typeof appModule.goLctrum==='function'){$call;return;}" +
                "if(n>0)setTimeout(function(){go(n-1);},$intervalMs);}" +
                "go($maxRetries);})();",
        )
    }

    fun reloadPage(): WebScript = WebScript("pageReload();")

    fun closeBottomSheet(): WebScript = WebScript("window.closeWebViewBottomSheet();")

    fun notifyViewportChanged(): WebScript = WebScript(
        "(function(){function notify(){window.dispatchEvent(new Event('resize'));" +
            "if(window.visualViewport)window.visualViewport.dispatchEvent(new Event('resize'));}" +
            "if(window.requestAnimationFrame){window.requestAnimationFrame(function(){" +
            "window.requestAnimationFrame(notify);});}else{setTimeout(notify,0);}})();",
    )

    fun updateCalendarBottomSheetFooterInset(bottomInsetCssPx: Int): WebScript {
        require(bottomInsetCssPx >= 0)
        return WebScript(
            "(function(offset){var root=document.documentElement;" +
                "root.style.setProperty('--klas-calendar-footer-inset',offset+'px');" +
                "var id='klas-calendar-footer-inset-style';var style=document.getElementById(id);" +
                "if(!style){style=document.createElement('style');style.id=id;" +
                "style.textContent='.bottom-sheet-footer{bottom:var(--klas-calendar-footer-inset,0px)!important;}';" +
                "document.head.appendChild(style);}window.dispatchEvent(new Event('resize'));" +
                "if(window.visualViewport)window.visualViewport.dispatchEvent(new Event('resize'));" +
                "})($bottomInsetCssPx);",
        )
    }

    fun styleContentPage(hideSubjectHeader: Boolean = true): WebScript {
        val subjectHeader = if (hideSubjectHeader) " #appHeaderSubj { display: none; }" else ""
        return WebScript(
            "(function(){var style=document.createElement('style');" +
                "style.innerHTML='header { display: none; } .selectsemester { display: none; } " +
                ".card { border-radius: 15px !important; } .container { margin-top: -10px } " +
                "button { border-radius: 10px !important } .board_view_header { border: none !important; " +
                "border-radius: 15px; }$subjectHeader';document.head.appendChild(style);window.scroll(0,0);})();",
        )
    }

    fun styleOnlineContentsPage(): WebScript = WebScript(
        "(function(){var style=document.createElement('style');" +
            "style.innerHTML='header { display: none; } .selectsemester { display: none; } " +
            ".card { border-radius: 15px !important; } .contsubtitle { display: none; } " +
            ".container { margin-top: -10px } button { border-radius: 10px !important } " +
            ".board_view_header { border: none !important; border-radius: 15px; }';" +
            "document.head.appendChild(style);" +
            "var appModule=document.querySelector('#appModule');" +
            "if(appModule&&appModule.childNodes[2])appModule.childNodes[2].style.display='none';" +
            "window.scroll(0,0);})();",
    )

    /// Android WebView는 `supportMultipleWindows=false`일 때 `window.open` 호출 시 현재 웹뷰에서 바로 이동합니다.
    /// 반면 iOS WKWebView는 네이티브(evaluateJavaScript)에서 간접 트리거된 `window.open` 팝업을 보안의 이유로 차단합니다.
    /// 따라서 새 창 대신 현재 웹뷰(top frame)에서 페이지가 이동하도록 모든 프레임의 `window.open`을 가로챕니다.
    fun redirectWindowOpenToSameFrame(): WebScript = WebScript(
        "(function(root){function go(url){var href=url==null||url===undefined?'':String(url);" +
            "if(href&&href!=='about:blank'){try{root.top.location.href=href;}catch(e){" +
            "try{root.location.href=href;}catch(e2){}}}return root.top||root;}" +
            "function install(w){if(!w)return;try{w.open=go;}catch(e){}" +
            "try{var frames=w.frames;for(var i=0;i<frames.length;i++)try{install(frames[i]);}catch(e){}}" +
            "catch(e){}}install(root);})(window);",
    )

    fun collectLectureBoardPaths(
        maxRetries: Int = 20,
        intervalMs: Int = 250,
    ): WebScript {
        require(maxRetries > 0)
        require(intervalMs > 0)
        return WebScript(
            "(function(){var marker='/std/lis/sport/';var lastNotice='';var lastPds='';" +
                "function findPath(label){var links=document.querySelectorAll('a[onclick],a[href]');" +
                "for(var i=0;i<links.length;i++){var link=links[i];" +
                "if((link.textContent||'').replace(/\\s/g,'').indexOf(label)<0)continue;" +
                "var source=link.getAttribute('onclick')||link.getAttribute('href')||'';" +
                "var start=source.indexOf(marker);if(start<0)continue;" +
                "var path=source.substring(start+marker.length).split(/[/'\"?\\s)]/)[0];" +
                "if(path)return path;}return '';}" +
                "function collect(remaining){var notice=findPath('공지사항');var pds=findPath('자료실');" +
                "if((notice||pds)&&(notice!==lastNotice||pds!==lastPds)){lastNotice=notice;lastPds=pds;" +
                klasNativeBridgeCall("getBoardPath", "notice", "pds") + ";}" +
                "if(notice&&pds)return;if(remaining>0)setTimeout(function(){collect(remaining-1);},$intervalMs);}" +
                "collect($maxRetries);})();",
        )
    }

    fun styleViewerPage(): WebScript = WebScript(
        "(function(){var style=document.createElement('style');" +
            "style.innerHTML='.antopbak { display: none; } #appHeaderSubj { display: none; }';" +
            "document.head.appendChild(style);window.scroll(0,0);})();",
    )

    fun monitorLectureProgress(): WebScript = WebScript(
        "(function(){function report(){var root=document.querySelector('.antopbak');" +
            "if(root&&root.children[0]&&root.children[1])" + klasNativeBridgeCall(
                "receiveVideoData",
                "root.children[0].innerHTML",
                "root.children[1].innerHTML",
            ) + ";}" +
            "report();clearInterval(window.__klasPlusLectureProgressInterval);" +
            "window.__klasPlusLectureProgressInterval=setInterval(report,10000);})();",
    )

    fun reportViewerVideoUrl(): WebScript = WebScript(
        "(function(){var source=String(chkOpen);var parts=source.split('<EMBED src =\\\"');" +
            "if(parts[1])" + klasNativeBridgeCall(
                "receiveVideoURL",
                "parts[1].split('\\\"')[0]",
            ) + ";})();",
    )

    fun configureMemberNumberRecoveryPage(): WebScript = WebScript(
        "document.querySelectorAll('[data-page-btn=\"close\"]').forEach(function(button){" +
            "button.onclick=function(e){e.preventDefault();e.stopPropagation();window.close();};});" +
            "var container=document.querySelector('.ax-search-tbl > div:first-child');" +
            "if(container){container.style.display='flex';container.style.flexDirection='column';" +
            "Array.prototype.slice.call(container.children,0,2).forEach(function(child){" +
            "child.style.width='100%';child.style.boxSizing='border-box';});}",
    )

    fun configurePasswordRecoveryPage(): WebScript = WebScript(
        "var closeButton=document.querySelector('.closeB');if(closeButton){" +
            "closeButton.onclick=function(e){e.preventDefault();e.stopPropagation();window.close();};}",
    )

    fun makeNoticeScrollable(): WebScript = WebScript(
        "var contents=document.querySelector('.contents');if(contents)contents.style.overflowY='scroll';",
    )
}

enum class PlayerPlaybackCommand {
    PLAY,
    PAUSE,
    MUTE,
    UNMUTE,
    OPEN_FULL_SCREEN,
    CLOSE_FULL_SCREEN,
}

enum class PlayerSeekDirection {
    FORWARD,
    BACKWARD,
}

object PlayerWebScripts {
    data class OnlineContentRequest(
        val groupCode: String,
        val subjectId: String,
        val year: String,
        val semester: String,
        val classNumber: String,
        val module: String,
        val lesson: String,
        val objectId: String,
        val starting: String,
        val contentsType: String,
        val weekNumber: Int,
        val weeklySequence: Int,
        val width: Int,
        val height: Int,
        val today: String,
        val startDate: String,
        val endDate: String,
        val playerType: String,
        val learnTime: String,
        val progress: Int,
        val playTime: String,
    )

    fun changePlaybackRate(speed: Double): WebScript {
        require(speed in 0.25..4.0)
        return WebScript(
            "bcPlayController.getPlayController()._eventTarget.fire(" +
                "VCPlayControllerEvent.CHANGE_PLAYBACK_RATE,Number($speed));",
        )
    }

    fun seekTo(seconds: Double): WebScript {
        require(seconds.isFinite() && seconds >= 0.0)
        return WebScript(
            "bcPlayController.getPlayController()._eventTarget.fire(" +
                "VCPlayControllerEvent.SEEK_END,$seconds);",
        )
    }

    fun playback(command: PlayerPlaybackCommand): WebScript {
        val source = when (command) {
            PlayerPlaybackCommand.PLAY ->
                "bcPlayController._uniPlayerEventTarget.fire(VCPlayControllerEvent.PLAY);"
            PlayerPlaybackCommand.PAUSE ->
                "bcPlayController._uniPlayerEventTarget.fire(VCPlayControllerEvent.PAUSE);"
            PlayerPlaybackCommand.MUTE ->
                "(function(){" +
                    "try{var v=document.querySelector('video');if(v)v.muted=true;}catch(e){}" +
                    "try{if(window.bcPlayController&&bcPlayController.getPlayController){var p=bcPlayController.getPlayController();p._isMuted=true;if(p._eventTarget)p._eventTarget.fire(VCPlayControllerEvent.MUTE);}}catch(e){}" +
                    "})();"
            PlayerPlaybackCommand.UNMUTE ->
                "(function(){" +
                    "try{var v=document.querySelector('video');if(v)v.muted=false;}catch(e){}" +
                    "try{if(window.bcPlayController&&bcPlayController.getPlayController){var p=bcPlayController.getPlayController();p._isMuted=false;if(p._eventTarget)p._eventTarget.fire(VCPlayControllerEvent.UNMUTE);}}catch(e){}" +
                    "})();"
            PlayerPlaybackCommand.OPEN_FULL_SCREEN ->
                "if(window.bcPlayController)bcPlayController.getPlayController()._eventTarget.fire(VCPlayControllerEvent.FULL_SCREEN);"
            PlayerPlaybackCommand.CLOSE_FULL_SCREEN ->
                "if(window.bcPlayController)bcPlayController.getPlayController()._eventTarget.fire(VCPlayControllerEvent.CLOSE_FULL_SCREEN);"
        }
        return WebScript(source)
    }

    fun monitorState(): WebScript = WebScript(
        "(function(){var speed=\$('.vc-pctrl-playback-rate-toggle-btn').text().replace('x ','');" +
            klasNativeBridgeCall("receiveInitSpeed", "speed") +
            ";clearInterval(window.__klasPlusPlayerStateInterval);" +
            "window.__klasPlusPlayerStateInterval=setInterval(function(){\$('#content-metadata').remove();" +
            "var player=(window.bcPlayController&&bcPlayController.getPlayController)?bcPlayController.getPlayController():null;" +
            "var v=document.querySelector('video');" +
            "var isMuted=v?v.muted:(player?player._isMuted===true:false);" +
            "var isPlaying=player&&player._isPlaying!==undefined?player._isPlaying:(v?!v.paused:false);" +
            "var currTime=player&&player._currTime!==undefined?player._currTime:(v?v.currentTime:0);" +
            "var duration=player&&player._duration!==undefined?player._duration:(v?v.duration:0);" +
            "var isFullscreen=player&&player._isFullScreen!==undefined?player._isFullScreen:false;" +
            klasNativeBridgeCall(
                "receivePlayerStates",
                "String(currTime)",
                "String(duration)",
                "String(isMuted)",
                "String(isPlaying)",
                "String(isFullscreen)",
            ) + ";},200);})();",
    )

    fun move(direction: PlayerSeekDirection): WebScript {
        val operation = if (direction == PlayerSeekDirection.FORWARD) "+" else "-"
        val boundary = if (direction == PlayerSeekDirection.FORWARD) {
            "position=position>player._duration?player._duration:position;" +
                "if(this._seekLimit)position=position>player._limitTime?player._limitTime:position;"
        } else {
            "position=position<0?0:position;"
        }
        return WebScript(
            "var player=bcPlayController.getPlayController();if(player._duration){" +
                "var position=player._currTime$operation VCPlayControllerMedia.MOVING_TIME;$boundary" +
                "player.changeCurrTimeManually(position,VCPlayControllerEvent.SEEK_END);}",
        )
    }

    fun setControllerVisible(visible: Boolean): WebScript = WebScript(
        "document.head.appendChild(Object.assign(document.createElement('style'),{textContent:" +
            JavaScriptEncoder.encodeText("#play-controller { display: ${if (visible) "block" else "none"} !important; }") +
            "}));" + if (visible) "" else "\$('.vc-pctrl-playback-rate-toggle-btn').remove();",
    )

    fun closeFullScreenIfAvailable(): WebScript = WebScript(
        "if(window.bcPlayController){" + playback(PlayerPlaybackCommand.CLOSE_FULL_SCREEN).reveal() + "}",
    )

    fun openFullScreenIfAvailable(): WebScript = WebScript(
        "if(window.bcPlayController){" + playback(PlayerPlaybackCommand.OPEN_FULL_SCREEN).reveal() + "}",
    )

    fun mute(muted: Boolean): WebScript =
        playback(if (muted) PlayerPlaybackCommand.MUTE else PlayerPlaybackCommand.UNMUTE)

    fun enterPictureInPicture(): WebScript = WebScript(
        "(function(){var video=document.querySelector('video');" +
            "if(!video)return;" +
            "try{if(video.webkitSetPresentationMode){video.webkitSetPresentationMode('picture-in-picture');return;}}catch(e){}" +
            "try{if(typeof video.requestPictureInPicture==='function'){video.requestPictureInPicture();}}catch(e){}})();",
    )

    fun pictureInPicturePresentationMode(): WebScript = WebScript(
        "(function(){var video=document.querySelector('video');" +
            "if(!video)return 'none:paused';" +
            "var mode=(video.webkitPresentationMode)?String(video.webkitPresentationMode):(document.pictureInPictureElement?'picture-in-picture':'inline');" +
            "var isPaused=(video.paused===true)?'paused':'playing';" +
            "return mode+':'+isPaused;})();",
    )

    fun isPictureInPictureSupported(): WebScript = WebScript(
        "(function(){var video=document.querySelector('video');" +
            "if(video&&video.webkitSetPresentationMode)return 'true';" +
            "if(video&&video.webkitSupportsPresentationMode)return video.webkitSupportsPresentationMode('picture-in-picture')?'true':'false';" +
            "return (document.pictureInPictureEnabled||'pictureInPictureEnabled' in document)?'true':'false';})();",
    )

    fun openOnlineContent(request: OnlineContentRequest): WebScript =
        openOnlineContent(request, directViewer = false)

    fun openOnlineContentViewer(request: OnlineContentRequest): WebScript =
        openOnlineContent(request, directViewer = true)

    private fun openOnlineContent(
        request: OnlineContentRequest,
        directViewer: Boolean,
    ): WebScript {
        require(request.weekNumber >= 0 && request.weeklySequence >= 0)
        require(request.width >= 0 && request.height >= 0)
        require(request.progress in 0..100)
        val values = listOf(
            request.groupCode,
            request.subjectId,
            request.year,
            request.semester,
            request.classNumber,
            request.module,
            request.lesson,
            request.objectId,
            request.starting,
            request.contentsType,
        ).map(JavaScriptEncoder::encodeText).toMutableList()
        values += request.weekNumber.toString()
        values += request.weeklySequence.toString()
        values += request.width.toString()
        values += request.height.toString()
        values += listOf(
            request.today,
            request.startDate,
            request.endDate,
            request.playerType,
            request.learnTime,
        ).map(JavaScriptEncoder::encodeText)
        values += request.progress.toString()
        val function =
            if (directViewer || request.progress == 100) "appModule.goViewCntnts" else "lrnCerti.checkCerti"
        if (function == "lrnCerti.checkCerti") {
            values += JavaScriptEncoder.encodeText("C")
        }
        values += JavaScriptEncoder.encodeText(request.playTime)
        return WebScript("$function(${values.joinToString(",")});")
    }
}

object KlasNativeBridgeScripts {
    const val NATIVE_OBJECT_NAME = "KlasNativeBridgeNative"
    const val DEFAULT_BRIDGE_TIMEOUT_MILLIS = 15_000

    fun installWebKitTransport(): WebScript = WebScript(
        "(function(global){if(global.KlasNativeBridgeNative&&global.KlasNativeBridgeNative.__klasWebKitTransport)return;" +
            "function nativeHandler(){var handlers=global.webkit&&global.webkit.messageHandlers;" +
            "return handlers&&handlers.KlasNativeBridgeNative;}" +
            "global.KlasNativeBridgeNative={__klasWebKitTransport:true,onmessage:null,postMessage:function(data){" +
            "var self=this,native=nativeHandler();" +
            "if(!native||typeof native.postMessage!=='function'){" +
            "return Promise.reject(new Error('BRIDGE_UNAVAILABLE'));}" +
            "return Promise.resolve(native.postMessage(data)).then(function(response){" +
            "if(typeof self.onmessage==='function')self.onmessage({data:response});" +
            "return response;},function(error){return Promise.reject(error);});}};})(window);",
    )

    fun installAdapter(timeoutMillis: Int = DEFAULT_BRIDGE_TIMEOUT_MILLIS): WebScript {
        require(timeoutMillis > 0) { "bridge timeout must be positive" }
        return WebScript(
            "(function(global){if(global.KlasNativeBridge)return;" +
                "var transport=global.KlasNativeBridgeNative;" +
                "if(!transport||typeof transport.postMessage!=='function')return;" +
                "var pending=Object.create(null),sequence=0;" +
                "transport.onmessage=function(event){var response;" +
                "try{response=typeof event.data==='string'?JSON.parse(event.data):event.data;}catch(_){return;}" +
                "var request=response&&pending[response.id];if(!request)return;" +
                "delete pending[response.id];clearTimeout(request.timer);" +
                "if(response.ok){request.resolve(response.result);return;}" +
                "var code=response.error&&response.error.code||'BRIDGE_ERROR';" +
                "var error=new Error(code);error.code=code;request.reject(error);};" +
                "function call(method,args){return new Promise(function(resolve,reject){" +
                "var id='injected-'+Date.now().toString(36)+'-'+(++sequence).toString(36);" +
                "var timer=setTimeout(function(){delete pending[id];reject(new Error('BRIDGE_TIMEOUT'));},$timeoutMillis);" +
                "pending[id]={resolve:resolve,reject:reject,timer:timer};" +
                "transport.postMessage(JSON.stringify({version:1,id:id,method:method,arguments:args}));});}" +
                "global.KlasNativeBridge=new Proxy({call:call},{get:function(target,property){" +
                "if(property in target)return target[property];if(typeof property!=='string')return undefined;" +
                "return function(){return call(property,Array.prototype.slice.call(arguments));};}});})(window);",
        )
    }
}

private fun klasNativeBridgeCall(method: String, vararg arguments: String): String {
    require(method.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*")))
    return "window.KlasNativeBridge.$method(${arguments.joinToString(",")})"
}
