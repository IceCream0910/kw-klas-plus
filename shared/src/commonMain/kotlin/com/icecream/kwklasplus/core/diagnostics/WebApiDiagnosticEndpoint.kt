package com.icecream.kwklasplus.core.diagnostics

internal enum class WebApiDiagnosticEndpoint(vararg val paths: String) {
    UNKNOWN,
    STUDENT_PROFILE("/std/cps/inqire/AtnlcScreHakjukInfo.do"),
    SEMESTER_GRADES("/std/cps/inqire/AtnlcScreSungjukInfo.do"),
    TOTAL_GRADES("/std/cps/inqire/AtnlcScreSungjukTot.do"),
    GRADE_RANKING("/std/cps/inqire/StandStdList.do"),
    SCHOLARSHIPS("/std/cps/inqire/JanghakHistoryStdList.do"),
    STUDENT_HOME("/std/cmn/frame/StdHome.do"),
    LEARNING_STATUS("/mst/lis/evltn/LrnSttusStdOne.do"),
    ACADEMIC_TERM_SUBJECTS("/std/cmn/frame/YearhakgiAtnlcSbjectList.do"),
    LECTURE_SCHEDULE("/std/cmn/frame/LctrumSchdulInfo.do"),
    LECTURE_HOME("/std/lis/evltn/LctrumHomeStdInfo.do", "/std/lis/evltn/LctrumHomeStdPage.do"),
    ONLINE_LECTURES("/std/lis/evltn/SelectOnlineCntntsStdList.do", "/std/lis/evltn/OnlineCntntsStdPage.do"),
    TASKS("/std/lis/evltn/TaskStdList.do", "/std/lis/evltn/TaskStdPage.do"),
    TEAM_PROJECTS("/std/lis/evltn/PrjctStdList.do", "/std/lis/evltn/PrjctStdPage.do"),
    CALENDAR_LIST("/std/ads/admst/MySchdulMonthTableList.do"),
    CALENDAR_SAVE("/std/ads/admst/MySchdulSave.do"),
    CALENDAR_DELETE("/std/ads/admst/MySchdulDelete.do"),
    LECTURE_PLAN_SEARCH("/std/cps/atnlc/LectrePlanStdList.do", "/std/cps/atnlc/LectrePlanStdPage.do"),
    LECTURE_PLAN_DETAIL(
        "/std/cps/atnlc/popup/LectrePlanStdView.do",
        "/std/cps/atnlc/LectrePlanData.do",
        "/std/cps/atnlc/LectreTimeInfo.do",
        "/std/cps/atnlc/LectreAstnt.do",
        "/std/cps/atnlc/LectreTeam.do",
        "/std/cps/atnlc/LectreEnginerGwamok.do",
        "/std/cps/atnlc/LectrePlanInputTabFourgrid.do",
        "/std/cps/atnlc/LectreBeforeGwamok.do",
        "/std/cps/atnlc/LectrePlanInputTabSixInfo.do",
        "/std/cps/atnlc/popup/LectrePlanStdCrtNum.do",
    ),
    LECTURE_PLAN_SUBJECTS("/std/cps/atnlc/CmmnGamokList.do"),
    LECTURE_PLAN_DEPARTMENTS("/std/cps/atnlc/CmmnHakgwaList.do"),
    LECTURE_PLAN_MAJORS("/std/cps/atnlc/CmmnMagerCodeList.do"),
    KLAS_NOTICES("/mst/sys/optrn/SelectPushMsgHisList.do"),
    BOARD_ATTACHMENTS("/common/file/UploadFileList.do"),
    BOARD_LIST,
    BOARD_DETAIL,
    ;

    companion object {
        private val boardIds = listOf(
            "d052b8f845784c639f036b102fdc3023",
            "573f918c23984ae8a88c398051bb1263",
            "6972896bfe72408eb72926780e85d041",
            "70778131bf7a421aba99dded74b3fb6b",
        )
        private val byPath = buildMap {
            for (endpoint in WebApiDiagnosticEndpoint.entries) for (path in endpoint.paths) put(path, endpoint)
            for (id in boardIds) {
                put("/std/lis/sport/$id/BoardStdList.do", BOARD_LIST)
                put("/std/lis/sport/$id/BoardStdView.do", BOARD_DETAIL)
            }
        }

        fun fromRequestPath(path: String): WebApiDiagnosticEndpoint =
            byPath[path.substringBefore('?')] ?: UNKNOWN
    }
}
