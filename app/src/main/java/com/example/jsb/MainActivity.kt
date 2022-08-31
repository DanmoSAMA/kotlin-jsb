package com.example.jsb
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.example.urlschema.JSBridge.register
import com.example.urlschema.JSBridgeViewClient

class MainActivity : AppCompatActivity() {
    private var mWebView: WebView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mWebView = findViewById<View>(R.id.mWebView) as WebView

        // 设置 webViewClient 类
        mWebView!!.webViewClient = JSBridgeViewClient()

        // 设置 webChromeClient 类
        mWebView!!.webChromeClient = WebChromeClient()

        // 设置支持调用 JS
        mWebView!!.settings.javaScriptEnabled = true
        mWebView!!.loadUrl("file:///android_asset/index.html")
        // NativeMethods 类中的方法全部注册到 JSBridge 下
        register("JSBridge", NativeMethods::class.java, NativeMethods::class.java.name)
    }
}
