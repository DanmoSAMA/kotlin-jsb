package com.example.urlschema
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.urlschema.JSBridge.call

class JSBridgeViewClient : WebViewClient() {
    // 通过覆盖 WebViewClient 类的 shouldOverrideUrlLoading 方法进行拦截
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        call(view, url)
        // If a WebViewClient is provided, returning true causes the current WebView to abort loading the URL,
        // while returning false causes the WebView to continue loading the URL as usual.
        return true
    }
}
