package com.icecream.kwklasplus.core.web

object KlasWebAutomationScripts {
    fun openLecture(yearSemester: String, subjectId: String): WebScript = WebScript(
        "appModule.goLctrum(${JavaScriptEncoder.encodeText(yearSemester)},${JavaScriptEncoder.encodeText(subjectId)});",
    )

    fun reloadPage(): WebScript = WebScript("pageReload();")

    fun closeBottomSheet(): WebScript = WebScript("window.closeWebViewBottomSheet();")

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
            "if(noticeParts[1]&&pdsParts[1])Android.getBoardPath(" +
            "noticeParts[1].split('/')[0],pdsParts[1].split('/')[0]);})();",
    )

    fun styleViewerPage(): WebScript = WebScript(
        "(function(){var style=document.createElement('style');" +
            "style.innerHTML='.antopbak { display: none; } #appHeaderSubj { display: none; }';" +
            "document.head.appendChild(style);window.scroll(0,0);})();",
    )

    fun monitorLectureProgress(): WebScript = WebScript(
        "(function(){function report(){var root=document.querySelector('.antopbak');" +
            "if(root&&root.children[0]&&root.children[1])Android.receiveVideoData(" +
            "root.children[0].innerHTML,root.children[1].innerHTML);}" +
            "report();clearInterval(window.__klasPlusLectureProgressInterval);" +
            "window.__klasPlusLectureProgressInterval=setInterval(report,10000);})();",
    )

    fun reportViewerVideoUrl(): WebScript = WebScript(
        "(function(){var source=String(chkOpen);var parts=source.split('<EMBED src =\\\"');" +
            "if(parts[1])Android.receiveVideoURL(parts[1].split('\\\"')[0]);})();",
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
            "Android.receiveInitSpeed(speed);clearInterval(window.__klasPlusPlayerStateInterval);" +
            "window.__klasPlusPlayerStateInterval=setInterval(function(){\$('#content-metadata').remove();" +
            "var player=bcPlayController.getPlayController();Android.receivePlayerStates(" +
            "String(player._currTime),String(player._duration),String(player._isMuted)," +
            "String(player._isPlaying),String(player._isFullScreen));},200);})();",
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
