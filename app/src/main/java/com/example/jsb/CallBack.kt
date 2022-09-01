package com.example.jsb
import android.webkit.ValueCallback
import android.webkit.WebView
import org.json.JSONObject

class CallBack(private val mWebView: WebView?, private val callbackName: String) {
    fun apply(jsonObject: JSONObject) {
        // native调用js
        mWebView?.evaluateJavascript(
            "javascript:$callbackName($jsonObject)",
            // @函数名 可以确定从哪个函数体中返回
            // In Kotlin, you can call return from nested closure to finish outer closure.
            // 如果函数写成嵌套形式，内层的函数想要返回上一层，必须加@函数名
            ValueCallback { return@ValueCallback })
    }
}
