package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.bridge.*

class DemoBridgeCommandHandler(private val fallback: BridgeCommandHandler, private val present: (String) -> Unit = {}) : ResettableBridgeCommandHandler {
    private val demoGeneration = DemoSession.generation.takeIf { DemoSession.active }

    private fun unavailable() = present("window.dispatchEvent(new CustomEvent('klas-demo-settings',{detail:{type:'unavailable'}}));")

    override fun reset() { (fallback as? ResettableBridgeCommandHandler)?.reset() }

    override suspend fun handle(command: ValidatedBridgeCommand): BridgeHandlerResult {
        if (demoGeneration != null && (!DemoSession.active || !DemoSession.hasSession || DemoSession.generation != demoGeneration)) {
            return BridgeHandlerResult.Failure(BridgeErrorCode.HANDLER_FAILURE)
        }
        if (!DemoSession.active) return fallback.handle(command)
        val method = command.method.name
        when (method) {
            "openLibraryQRSettingsModal" -> present("window.dispatchEvent(new CustomEvent('klas-demo-settings',{detail:{type:'library'}}));")
            "evaluteKLASScript" -> {
                val script = (command.arguments.firstOrNull() as? BridgeValue.Text)?.value.orEmpty()
                val feature = mapOf("goTask" to "task", "goPrjct" to "project", "goQuiz" to "quiz", "goExam" to "exam", "goSurvey" to "survey", "goDscsn" to "discussion").entries.firstOrNull { script.trim() == "appModule.${it.key}()" }?.value
                if (feature != null) present("window.location.assign('/demo?feature=$feature');") else unavailable()
            }
            "openInKLAS" -> unavailable()
            "openPage", "openExternalPage", "openExternalLink" -> {
                val url = (command.arguments.firstOrNull() as? BridgeValue.Text)?.value.orEmpty()
                if (DemoWebScripts.isSchoolUrl(url)) unavailable() else return fallback.handle(command)
            }
            "syncCalendar" -> return BridgeHandlerResult.Success()
            else -> return fallback.handle(command)
        }
        return BridgeHandlerResult.Success()
    }
}
