package com.example.urlschema
import android.net.Uri
import android.webkit.WebView
import com.example.jsb.CallBack
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object JSBridge {
    private val exposeMethods: MutableMap<String, HashMap<String?, Method>> = HashMap()
    private val classAndMethods: MutableMap<String, HashMap<String?, Method>> = HashMap()
      // exposeMethods 数据结构如下，JSBridge是类名，下边有 showToast、openScan等方法
      // {
      //    JSBridge: {
      //        showToast: ...
      //    }
      // }
    fun register(exposeName: String, classz: Class<*>, className: String) {
        val allMethods = getAllMethod(classz)
        if (!exposeMethods.containsKey(exposeName)) {
            exposeMethods[exposeName] = allMethods
        }
        if (!classAndMethods.containsKey((className))) {
            classAndMethods[className] = allMethods
        }
    }
    // 收集一个类下符合条件的方法，打包成一个hashmap返回
    private fun getAllMethod(injectedCls: Class<*>): HashMap<String?, Method> {
        val methodHashMap = HashMap<String?, Method>()
        // 该类下声明的全部方法
        val methods = injectedCls.declaredMethods
        for (method in methods) {
            // 判断是公有方法，如果是private，肯定不希望暴露出去被h5调用
            val modifiers = method.modifiers
            if (!Modifier.isPublic(modifiers)) {
                continue
            }
            // 第一个参数为 Webview 类的实例，第二个参数为 JSONObject 类的实例，第三个参数为 CallBack 类的实例
            val parameters = method.parameterTypes
            if (parameters.size == 3) {
                if (parameters[0] == WebView::class.java && parameters[1] == JSONObject::class.java && parameters[2] == CallBack::class.java) {
                    // 以上条件都满足，则加入 methodHashMap
                    methodHashMap[method.name] = method
                }
            }
        }
        return methodHashMap
    }

    fun call(webView: WebView?, urlString: String?): String? {
        // 判断字符串是否以 jsbridge 开头
        if (urlString != "" && urlString != null && urlString.startsWith("jsbridge")) {
            // 将该字符串转成 Uri 格式
            val uri = Uri.parse(urlString)
            // 获取其中的 host名，即方法名
            val methodName = uri.host
            try {
                // 获取 query，即方法参数和 js 回调函数名组合的对象
                val args = JSONObject(uri.query)
                // 方法参数，加工后的arg是对象的形式，arg.msg就能拿到属性
                val arg = JSONObject(args.getString("data"))
                // 回调函数名
                val callbackName = args.getString("callbackName")
                // 查找 exposeMethods 的映射，找到对应的方法并执行该方法
                if (exposeMethods.containsKey("JSBridge")) {
                    // 此处我们假设所有jsb相关方法都定义在JSBridge这个类中
                    // 当协议不同时，则去别的类中找方法
                    val methodHashMap = exposeMethods["JSBridge"]
                    if (methodHashMap != null && methodHashMap.size != 0 && methodHashMap.containsKey(
                            methodName
                        )
                    ) {
                        val method = methodHashMap[methodName]
                        var className = ""
                        for ((_className, methods) in classAndMethods){
                            for ((_, methodReflection) in methods) {
                                if (methodReflection == method) {
                                    className = _className
                                }
                            }
                        }
                        val instance = Class.forName(className).newInstance()
                        // NativeMethods 下的方法都注册到了JSBridge中，这里调用的是showToast
                        // 第一个参数传null会遇到 null receiver 异常，因此又弄了一个map
                        // 该map的最外层key是类的名字，这样可以把类名查出来，然后构造一个实例对象，传入第一个参数
                        // Java中应该没有这个问题，直接传null就行了
                        method?.invoke(instance, webView, arg, CallBack(webView, callbackName))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }
}
