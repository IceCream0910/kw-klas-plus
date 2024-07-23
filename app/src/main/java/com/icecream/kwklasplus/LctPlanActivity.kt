package com.icecream.kwklasplus

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat

class LctPlanActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lct_plan)
        window.statusBarColor = Color.parseColor("#3A051F")

        val subjID = intent.getStringExtra("subjID")

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("https://klas.kw.ac.kr/std/cps/atnlc/popup/LectrePlanStdView.do?selectSubj=$subjID")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // 프린트 버튼 제거
                webView.evaluateJavascript("""
                    document.querySelectorAll('.btn2.btn-white').forEach(button => button.innerText.trim() === '프린트' && button.remove());
                """.trimIndent(), null)

                // 수강 인원 조회를 페이지 이동 없이 할 수 있도록 로직 변경
                webView.evaluateJavascript(
                    """
                     const getSearch = () => {
    axios.post('/std/cps/atnlc/popup/LectrePlanStdCrtNum.do', {
      currentNum: appModule.${'$'}data.currentNum,
      gwamokName: appModule.${'$'}data.gwamokName,
      numText: appModule.${'$'}data.numText,
      randomNum: appModule.${'$'}data.randomNum,
      selectGrcode: appModule.${'$'}data.selectGrcode,
      selectSubj: appModule.${'$'}data.selectSubj,
      selectYear: appModule.${'$'}data.selectYear,
      selectYearHakgi: appModule.${'$'}data.selectYearHakgi,
      selecthakgi: appModule.${'$'}data.selecthakgi,
      stopFlag: appModule.${'$'}data.stopFlag,
    })
      .then(function (response) {
        const currentNum = response.data.currentNum;
        ${'$'}('#student-number').text(currentNum+'명');
      });
  };

  // 수강인원조회라는 항목을 가진 버튼 찾기
  const ${'$'}button = ${'$'}('button').filter((index, element) => {
    return ${'$'}(element).text() === '수강인원조회';
  });

  // 부모 요소 찾기
  const ${'$'}parent = ${'$'}button.parent();

  // 새로운 버튼 및 Span 생성
  const ${'$'}newButton = ${'$'}('<button id="student-get">조회</button>');
  const ${'$'}span = ${'$'}('<span id="student-number"></span>');
  ${'$'}span.css('margin-left', '10px');

  ${'$'}parent.append(${'$'}newButton);
  ${'$'}parent.append(${'$'}span);

  // 기존 버튼 삭제
  ${'$'}button.remove();

  // 수강인원조회 버튼 클릭 시
  ${'$'}newButton.click(() => {
    getSearch();
  });""".trimIndent(), null
                )
            }
        }

        webView.setWebChromeClient(object : WebChromeClient() {
            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                finish()
            }
        })
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}