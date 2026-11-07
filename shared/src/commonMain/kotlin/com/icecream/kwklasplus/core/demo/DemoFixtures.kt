package com.icecream.kwklasplus.core.demo

import io.ktor.util.date.GMTDate
import com.icecream.kwklasplus.core.session.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.concurrent.Volatile

data class DemoResponse(val body: String, val status: Int = 200, val contentType: String = "application/json; charset=UTF-8")

object DemoFixtures {
    private val mutex = Mutex()
    private class State(val generation: Long) {
        val events = linkedMapOf<String, JsonObject>()
        val activities = mutableMapOf<String, JsonObject>()
        var nextId = 1
    }
    @Volatile private var state = State(-1)
    fun reset() { state = State(DemoSession.generation); DemoPlaybackState.reset() }
    val term: String get() = GMTDate().let { "${it.year},${if (it.month.ordinal < 7) 1 else 2}" }
    val subjects: String get() = """[{"value":"DEMO001","name":"컴퓨터프로그래밍","label":"컴퓨터프로그래밍(3학점) - 김교수"},{"value":"DEMO002","name":"자료구조","label":"자료구조(3학점) - 이교수"},{"value":"DEMO003","name":"대학영어","label":"대학영어(2학점) - 박교수"}]"""

    private fun date(now: Long, days: Int = 0): String = GMTDate(now + days * 86_400_000L).let {
        "${it.year}-${(it.month.ordinal + 1).toString().padStart(2, '0')}-${it.dayOfMonth.toString().padStart(2, '0')}"
    }

    suspend fun respond(path: String, payload: String = "", clock: Clock = Clock { GMTDate().timestamp }): DemoResponse = mutex.withLock {
        if (!DemoSession.active || !DemoSession.hasSession) return@withLock DemoResponse("{}", 401)
        val snapshot = state
        val events = snapshot.events
        val activities = snapshot.activities
        val body = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: buildJsonObject {}
        val name = path.substringBefore('?').substringAfterLast('/')
        val now = clock.nowEpochMillis()
        val term = GMTDate(now).let { "${it.year},${if (it.month.ordinal < 7) 1 else 2}" }
        val today = date(now)
        val start = date(now, -1)
        val lectureEnd = date(now, 1)
        val end = date(now, if (name == "PrjctStdList.do") 3 else 2)
        val notice = """{"boardNo":1,"masterNo":1,"title":"수업 및 과제 안내","userNm":"김교수","registDt":"$today 09:00:00","readCnt":12,"content":"<p>KLAS+ 데모 수업입니다. 공지와 자료를 확인하고 과제 및 강의를 체험할 수 있어요.</p>"}"""
        val progress = activities["lecture"]?.get("progress")?.jsonPrimitive?.doubleOrNull ?: DemoPlaybackState.progress
        val submitted = activities[if (name == "PrjctStdList.do") "project" else "task"]?.get("submitted")?.jsonPrimitive?.booleanOrNull == true
        val lectures = """[{"grcode":"DEMO","subj":"DEMO001","yearhakgi":"$term","weeklyseq":1,"cntntsSeq":1,"cntntsId":"demo-lecture","evltnSe":"lesson","sbjt":"프로그래밍 시작하기","startDate":"$start 00:00","endDate":"$lectureEnd 23:59","lrnPd":"$start ~ $lectureEnd","prog":$progress,"learnTime":0,"ptime":10,"starting":"","weekly":1}]"""
        val tasks = """[{"taskNo":1,"taskSeq":1,"title":"첫 번째 프로그래밍 과제","taskTitle":"첫 번째 프로그래밍 과제","startdate":"$start 00:00:00","expiredate":"$end 23:59:59","submityn":"${if (submitted) "Y" else "N"}"}]"""
        val result = when (name) {
            "DemoLockSave.do" -> {
                DemoSession.appLockEnabled = true
                "{\"success\":true}"
            }
            "DemoState.do", "DemoTaskSave.do", "DemoLectureProgress.do" -> {
                val feature = body.text("feature") ?: "task"
                if (feature !in setOf("task", "project", "lecture", "quiz", "exam", "discussion", "survey", "plan", "qr")) return@withLock DemoResponse("{}", 400)
                val previous = activities[feature] ?: buildJsonObject { put("submitted", false); put("progress", 0); put("text", "") }
                if (name == "DemoState.do") previous.toString() else {
                    val text = body.text("text").orEmpty()
                    val value = body.text("progress")?.toDoubleOrNull() ?: 0.0
                    if (text.length > 10_000 || !value.isFinite() || value !in 0.0..100.0) return@withLock DemoResponse("{}", 400)
                    buildJsonObject {
                        put("submitted", name == "DemoTaskSave.do"); put("progress", value); put("text", text)
                    }.also { activities[feature] = it }.toString()
                }
            }
            "PublicNotices.do" -> {
                val items = buildJsonArray { add(buildJsonObject {
                    put("number", 1); put("category", "학사"); put("title", "데모 학사 안내")
                    put("link", "/demo?feature=plan"); put("createdDate", today); put("author", "학사팀"); put("views", 10)
                }) }
                if (body.text("pageInfo") == "1") buildJsonObject { put("items", items); put("more", false) }.toString() else items.toString()
            }
            "PublicCafeteria.do" -> buildJsonObject {
                val menus = buildJsonArray { add(buildJsonObject {
                    put("date", today); put("day", "오늘"); put("menu", "데모 정식\r\n밥 · 국 · 반찬")
                }) }
                put("restaurants", buildJsonArray { add(buildJsonObject {
                    put("name", "함지마루"); put("price", "예시 메뉴"); put("time", "11:30~14:00"); put("weeklyMenu", menus)
                }) }); put("weeklyMenu", menus)
            }.toString()
            "info" -> """{"logoutCountDownSec":60,"sessionNotiSec":60,"remainingTime":7200}"""
            "UpdateSession.do" -> "{}"
            "YearhakgiAtnlcSbjectList.do" -> """[{"value":"$term","name":"${term.replace(',', ' ')}학기","subjList":$subjects}]"""
            "TimetableStdList.do" -> """[{"wtHasSchedule":"Y","wtTime":1,"wtSubj_1":"DEMO001","wtSubjNm_1":"컴퓨터프로그래밍","wtLocHname_1":"새빛관101","wtProfNm_1":"김교수","wtSpan_1":1,"wtSubj_3":"DEMO002","wtSubjNm_3":"자료구조","wtLocHname_3":"비마관201","wtProfNm_3":"이교수","wtSpan_3":1},{"wtHasSchedule":"Y","wtTime":4,"wtSubj_2":"DEMO003","wtSubjNm_2":"대학영어","wtLocHname_2":"한울관301","wtProfNm_2":"박교수","wtSpan_2":1}]"""
            "SelectOnlineCntntsStdList.do" -> lectures
            "TaskStdList.do", "PrjctStdList.do" -> tasks
            "StdHome.do" -> """{"rspnsblProfsr":"김교수"}"""
            "AtnlcScreHakjukInfo.do" -> """{"hakbun":"0000000000","kname":"김광운","name":"김광운","hakgwa":"컴퓨터정보공학부","hakgwaNm":"컴퓨터정보공학부","grade":"2","hakjukStatu":"재학","college":"인공지능융합대학","hakjukInfo":{"kname":"김광운"}}"""
            "LrnSttusStdOne.do" -> """{"kname":"김광운","hakbun":"0000000000","hakgwa":"컴퓨터정보공학부","hakgwaNm":"컴퓨터정보공학부","compNm":"광운대학교","grade":"2","fileUrl":""}"""
            "LctrumSchdulInfo.do" -> JsonPrimitive("월 1교시/새빛관101").toString()
            "LctrumHomeStdInfo.do" -> """{"noticeList":[$notice],"cntntCmpltCnt":0,"cntntList":$lectures,"taskPrsntCnt":0,"taskCnt":1,"quizPrsntCnt":0,"quizCnt":0,"pdsCnt":1,"pdsNewCnt":1,"examPrsntCnt":0,"examCnt":0,"prjctPrsntCnt":0,"prjctCnt":1,"dscsnJoinCnt":0,"dscsnCnt":0,"surveyPrsntCnt":0,"surveyCnt":0,"taskTop":$tasks,"examTop":[],"anQuizTop":[],"prjctTop":[],"dscsnTop":[],"atendSubList":[{"weeklyseq":1,"pgr1":"O","pgr2":"O","pgr3":"O","pgr4":"O"},{"weeklyseq":2,"pgr1":"O","pgr2":"O","pgr3":"O","pgr4":"O"},{"weeklyseq":3,"pgr1":"O","pgr2":"O","pgr3":"O","pgr4":"O"}],"attendInfo":{},"attendCnt":3,"absentCnt":0,"lateCnt":0}"""
            "BoardStdList.do" -> """{"list":[$notice],"page":{"totalPages":1,"totalElements":1}}"""
            "BoardStdView.do" -> """{"board":$notice,"boardPre":null,"boardNex":null}"""
            "UploadFileList.do", "JanghakHistoryStdList.do", "StandStdList.do", "SelectPushMsgHisList.do" -> "[]"
            "AtnlcScreSungjukInfo.do" -> """[{"thisYear":"${GMTDate(now).year}","hakgi":"1","hakgiOrder":"1","applyHakjum":6,"chidukHakjum":6,"jaechulScoresum":4.25,"sungjukList":[{"gwamokKname":"컴퓨터프로그래밍","codeName1":"전필","hakjumNum":"3","getGrade":"A+"},{"gwamokKname":"대학영어","codeName1":"교필","hakjumNum":"3","getGrade":"A0"}]}]"""
            "AtnlcScreSungjukTot.do" -> """{"applyHakjum":6,"majorApplyHakjum":3,"delHakjum":0,"majorDelHakjum":0,"chidukHakjum":6,"majorChidukHakjum":3,"jaechulScoresum":4.25,"hwakinScoresum":4.25}"""
            "CmmnGamokList.do", "CmmnHakgwaList.do", "CmmnMagerCodeList.do" -> """[{"code":"DEMO","codeName1":"컴퓨터정보공학부","codeName":"컴퓨터정보공학부"}]"""
            "LectrePlanData.do" -> """[{"gwamokKname":"컴퓨터프로그래밍","openMajorCode":"DEMO","openGrade":"2","openGwamokNo":"001","bunbanNo":"01","memberName":"김교수","jikgeubName":"교수","hakjumNum":"3","sisuNum":"3","codeName1":"전필","summary":"변수, 조건문, 반복문과 함수를 예제로 배워요.","purpose":"프로그래밍의 기본 원리를 익히고 프로그램을 작성해요.","week1Lecture":"변수와 자료형","week2Lecture":"조건문과 반복문","week3Lecture":"함수와 자료구조","gwamokAble":"문제 해결","face100Opt":"Y","bookName":"프로그래밍 기초 · 데모 교재","writeName":"김교수","companyName":"데모 출판사","printYear":"2026","middleBiyul":30,"lastBiyul":30,"reportBiyul":30,"attendBiyul":10}]"""
            "LectreTimeInfo.do" -> """[{"dayname1":"월","timeNo1":"1","locHname":"새빛관 101"}]"""
            "LectreAstnt.do", "LectreTeam.do", "LectreEnginerGwamok.do", "LectrePlanInputTabFourgrid.do", "LectreBeforeGwamok.do", "LectrePlanInputTabSixInfo.do" -> "[]"
            "LectrePlanStdCrtNum.do" -> """{"currentNum":30}"""
            "KwAttendStdGwakmokList.do" -> """[{"thisYear":"${GMTDate(now).year}","hakgi":"${term.substringAfter(',')}","openMajorCode":"DEMO","openGrade":"2","openGwamokNo":"001","bunbanNo":"01","gwamokKname":"컴퓨터프로그래밍","codeName1":"전필","hakjumNum":"3","sisuNum":"3","memberName":"김교수","currentNum":"30","yoil":"월"},{"thisYear":"${GMTDate(now).year}","hakgi":"${term.substringAfter(',')}","openMajorCode":"DEMO","openGrade":"2","openGwamokNo":"002","bunbanNo":"01","gwamokKname":"자료구조","codeName1":"전필","hakjumNum":"3","sisuNum":"3","memberName":"이교수","currentNum":"30","yoil":"화"},{"thisYear":"${GMTDate(now).year}","hakgi":"${term.substringAfter(',')}","openMajorCode":"DEMO","openGrade":"2","openGwamokNo":"003","bunbanNo":"01","gwamokKname":"대학영어","codeName1":"교필","hakjumNum":"2","sisuNum":"2","memberName":"박교수","currentNum":"30","yoil":"수"}]"""
            "KwAttendStdAttendList.do" -> "[]"
            "CertiPushSucStd.do" -> """{"randomKey":"demo-attendance-${DemoSession.generation}"}"""
            "KwAttendQRCodeInsert.do" -> {
                if (body.text("encrypt") == "KLASPLUS-DEMO-ATTENDANCE") {
                    activities["attendance"] = buildJsonObject { put("submitted", true) }
                    """{"fieldErrors":[],"success":true}"""
                } else """{"fieldErrors":[{"message":"심사용 샘플 QR 코드를 스캔해 주세요."}]}"""
            }
            "LectrePlanStdList.do" -> """[{"thisYear":"${GMTDate(now).year}","hakgi":"2","openGwamokNo":"001","openMajorCode":"DEMO","bunbanNo":"01","openGrade":"2","gwamokKname":"컴퓨터프로그래밍","memberName":"김교수","codeName1":"전필","hakjumNum":"3","sisuNum":"3","summary":"변수와 함수를 학습해요.","closeOpt":null}]"""
            "MySchdulSave.do" -> {
                val id = body.text("schdulId").takeUnless { it.isNullOrBlank() || it == "null" } ?: "demo-${snapshot.nextId++}"
                fun stamp(prefix: String) = body.text("${prefix}date").orEmpty().replace("-", "") +
                    body.text("${prefix}timeHour").orEmpty().padStart(2, '0') + body.text("${prefix}timeMin").orEmpty().padStart(2, '0')
                events[id] = buildJsonObject {
                    put("schdulId", id); put("title", body.text("title").orEmpty()); put("place", body.text("place").orEmpty())
                    put("started", stamp("s")); put("ended", stamp("e")); put("typeNm", "개인일정"); put("gubun", "10")
                    put("schdulColor", body.text("schdulColor") ?: "#336699")
                }
                "{\"success\":true}"
            }
            "MySchdulDelete.do" -> { events.remove(body.text("schdulId")); "{\"success\":true}" }
            "MySchdulMonthTableList.do" -> {
                val sampleDay = body.text("start")?.take(7)?.plus("-15") ?: today
                val sample = buildJsonObject {
                    put("schdulId", "sample"); put("title", "학습 계획 세우기"); put("typeNm", "학사일정")
                    put("started", sampleDay.replace("-", "") + "0900"); put("ended", sampleDay.replace("-", "") + "1000")
                    put("place", "새빛관"); put("gubun", "20")
                }
                JsonArray(listOf(sample) + events.values).toString()
            }
            else -> return@withLock DemoResponse("{\"error\":\"DEMO_UNSUPPORTED_ENDPOINT\"}", 501)
        }
        DemoResponse(result)
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
}
