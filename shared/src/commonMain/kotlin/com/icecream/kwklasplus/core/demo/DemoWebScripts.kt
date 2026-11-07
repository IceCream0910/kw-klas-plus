package com.icecream.kwklasplus.core.demo

import com.icecream.kwklasplus.core.web.WebScript

object DemoWebScripts {
    fun install(): WebScript {
        if (!DemoSession.active) return WebScript("")
        val generation = DemoSession.generation
        return WebScript("""
            (function(){
              if(window!==window.top||location.origin!=='https://klasplus.yuntae.in')return;
              Object.defineProperty(window,'__klasDemoSession',{value:Object.freeze({version:1,generation:$generation})});
              var backing=window.sessionStorage,prefix='klas-demo-$generation:';
              var storage={getItem:function(k){return backing.getItem(prefix+k)},setItem:function(k,v){backing.setItem(prefix+k,String(v))},removeItem:function(k){backing.removeItem(prefix+k)},clear:function(){Object.keys(backing).filter(function(k){return k.indexOf(prefix)===0}).forEach(function(k){backing.removeItem(k)})},key:function(i){return Object.keys(backing).filter(function(k){return k.indexOf(prefix)===0})[i]?.slice(prefix.length)??null}};
              Object.defineProperty(storage,'length',{get:function(){return Object.keys(backing).filter(function(k){return k.indexOf(prefix)===0}).length}});
              Object.defineProperty(window,'localStorage',{value:storage});
              Object.defineProperty(window,'sessionStorage',{value:storage});
              var fetchOriginal=window.fetch;
              window.fetch=function(input,options){var url=new URL(typeof input==='string'?input:input.url,location.href);if(url.hostname==='kw.ac.kr'||url.hostname.endsWith('.kw.ac.kr')||url.pathname.startsWith('/api/'))return Promise.reject(new Error('DEMO_NETWORK_BLOCKED'));return fetchOriginal.call(this,input,options)};
            })();
        """.trimIndent())
    }

    fun isSchoolUrl(url: String): Boolean = runCatching {
        val host = io.ktor.http.Url(url).host.lowercase()
        host == "kw.ac.kr" || host.endsWith(".kw.ac.kr")
    }.getOrDefault(false)
}
