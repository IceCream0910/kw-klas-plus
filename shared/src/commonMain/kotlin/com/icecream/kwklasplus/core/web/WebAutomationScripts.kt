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
                "if(!(window.appModule&&typeof appModule.goLctrum==='function')){" +
                "if(n>0)setTimeout(function(){go(n-1);},$intervalMs);return;}" +
                "var failed=false;var orig=window.alert;" +
                "window.alert=function(msg){" +
                "if(String(msg||'').indexOf('오류가 발생')!==-1){failed=true;return;}" +
                "return orig.apply(this,arguments);};" +
                "try{$call}finally{window.alert=orig;}" +
                "if(failed&&n>0)setTimeout(function(){go(n-1);},$intervalMs);" +
                "}go($maxRetries);})();",
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

    fun collectLectureBoardPaths(): WebScript = WebScript(
        "(function(){var notice=\$(\"a:contains('강의 공지사항')\").attr('onclick');" +
            "var pds=\$(\"a:contains('강의 자료실')\").attr('onclick');" +
            "if(!notice||!pds)return;" +
            "var noticeParts=notice.split(\"linkUrl('/std/lis/sport/\");" +
            "var pdsParts=pds.split(\"linkUrl('/std/lis/sport/\");" +
            "if(noticeParts[1]&&pdsParts[1])" + klasNativeBridgeCall(
                "getBoardPath",
                "noticeParts[1].split('/')[0]",
                "pdsParts[1].split('/')[0]",
            ) + ";})();",
    )

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
            PlayerPlaybackCommand.CLOSE_FULL_SCREEN ->
                "bcPlayController.getPlayController()._eventTarget.fire(VCPlayControllerEvent.CLOSE_FULL_SCREEN);"
        }
        return WebScript(source)
    }

    fun monitorState(): WebScript = WebScript(
        "(function(){var speed=\$('.vc-pctrl-playback-rate-toggle-btn').text().replace('x ','');" +
            klasNativeBridgeCall("receiveInitSpeed", "speed") +
            ";clearInterval(window.__klasPlusPlayerStateInterval);" +
            "window.__klasPlusPlayerStateInterval=setInterval(function(){\$('#content-metadata').remove();" +
            "var player=bcPlayController.getPlayController();" + klasNativeBridgeCall(
                "receivePlayerStates",
                "String(player._currTime)",
                "String(player._duration)",
                "String(player._isMuted)",
                "String(player._isPlaying)",
                "String(player._isFullScreen)",
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

    fun openOnlineContent(request: OnlineContentRequest): WebScript {
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
        if (request.progress != 100) values += JavaScriptEncoder.encodeText("C")
        values += JavaScriptEncoder.encodeText(request.playTime)
        val function = if (request.progress == 100) "appModule.goViewCntnts" else "lrnCerti.checkCerti"
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
