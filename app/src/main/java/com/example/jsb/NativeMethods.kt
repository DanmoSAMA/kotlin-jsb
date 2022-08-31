package com.example.jsb

import android.webkit.WebView
import android.widget.Toast
import org.json.JSONObject


class NativeMethods {
    fun showToast(view: WebView, arg: JSONObject, callBack: CallBack) {
        // 拿到h5传过来的msg，展示在Toast中
        val message = arg.optString("msg")
        Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
        // 执行h5的回调
        try {
            // 创建hashMap，作为res data返回给h5
            val result = JSONObject()
            result.put("msg", "js 调用 native 成功！")
            // 执行回调，h5弹出alert
            callBack.apply(result)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
