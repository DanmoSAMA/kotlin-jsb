# JSBridge

## JSONP

在讲JSBridge前，先回顾一下JSONP的知识，因为它们的思想有共通之处：

### 简介

JSONP是`JSON with Padding`的略称，前端定义好回调函数，函数名放在url中传给后端，后端拿到函数名，构造出执行函数的字符串返回给前端，前端收到后便会执行该回调，回调函数中可以拿到后端传过来的数据。

原理：src属性不受同源策略的限制，`img`、`script`等标签都不受同源策略的影响。

缺点：由于JSONP只支持get请求，且具有一定安全漏洞，一般不在实际场景中使用。

### 实现

以最简单的场景为例：

```js
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta http-equiv="X-UA-Compatible" content="IE=edge" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Document</title>
  </head>
  <body>
    <span class="amount">100</span>
    <button class="btn">Btn</button>
    <script>
      const button = document.querySelector('.btn')
      const amount = document.querySelector('.amount')

      button.addEventListener('click', (e) => {
        // 创建script标签
        let script = document.createElement('script')
        // 随机生成回调函数名
        let functionName = 'func' + parseInt(Math.random() * 10000, 10)
        // 将回调函数绑在window对象上
        window[functionName] = function (result) {
          console.log(result)
          if (result.name === 'success') {
            amount.innerText = amount.innerText - 1
          }
        }
        // 设置src，请求的url带上回调函数名
        script.src = `http://127.0.0.1:3000?callback=${functionName}`
        // 发送请求
        document.body.appendChild(script)
        // 请求结束，从window上移除绑定的回调函数
        script.onload = function (e) {
          e.currentTarget.remove()
          delete window[functionName]
        }
        script.onerror = function () {
          alert('Fail')
          e.currentTarget.remove()
          delete window[functionName]
        }
      })
    </script>
  </body>
</html>

```

```js
import * as http from 'http'
import * as url from 'url'
import * as qs from 'qs'

const server = http.createServer((req, res) => {
  // 解析得到params
  const { query } = url.parse(req.url)
  const params = qs.parse(query)
	// 后端要传的数据
  const data = { name: 'success' }
  let str = ''

  if (params.callback) {
    // 构造出执行函数的字符串
    str = `${params.callback}(${JSON.stringify(data)})`
    // 返回给前端
    res.end(str)
  } else {
    res.end()
  }
})

server.listen(3000, () => {
  console.log('Server is running on port 3000...')
})
```

前端发送请求的url可以是：`http://127.0.0.1:3000?callback=func1234`

后端获取回调函数名`func1234`，构造好字符串`func1234({ name: 'success' })`

前端拿到该字符串后，就会调用回调函数，拿到后端传的数据。

可见，JSONP的模式大概是：**前端随机生成了回调函数名，把函数名告诉后端，后端从url中解析出函数名，完成自身的逻辑后，将构造好的字符串返回给前端，前端执行回调。**

## JSB

> H5 -> 前端，Native/原生 -> 客户端
>
> 安卓 -> Java/Kotlin，IOS -> OC/Swift
>
> 
>
> webview 是一个基于 webkit 的引擎，可以解析 DOM 元素，展示 html 页面的控件。
>
> - 显示和渲染Web页面
> - 直接使用html文件（网络上或本地assets中）作布局
> - 可和JavaScript交互调用
>
> —— [Carson带你学Android：最全面、易懂的Webview使用教程](https://www.jianshu.com/p/3c94ae673e2a)

现在市面上的 App，基本上不是纯 Native 实现，客户端内置 webview，不少页面都嵌入了 H5，这种称作`Hybrid App`，那么 H5 和 Native 必然要进行通信。

JSBridge，是沟通 JS 和 Native 的桥梁，为**双向信道**，使 JS 可以调 Native 的 Api，从而拥有部分原生的能力。这样，页面中的 H5 部分就可以使用地址位置、摄像头等原生才有的功能。同时，Native 也可以调用JS。

我们要实现的 JSB，功能大致为：前端向客户端发送请求，意在调用客户端的某方法，达到前端使用客户端能力的效果，前端也定义了回调函数，并且把回调函数名告知客户端。客户端拦截请求，执行对应的方法，然后通过**特定 API**（如下），调用前端的回调函数。

### Native 调用 JS

安卓和IOS都提供了调用JS的方法，**被调用的方法需要在 JS 全局上下文上**。

其中安卓有两种方法可供选择：

```kotlin
// loadUrl
webview.loadUrl("javascript: func()");

// evaluateJavascript
webView.evaluateJavascript(
  "javascript:func()",
  // @函数名 可以确定从哪个函数体中返回
  // 如果函数写成嵌套形式，内层的函数想要返回上一层，必须加@函数名
  ValueCallback { return@ValueCallback })
```

可见，客户端想调用前端只需要直接调用这些方法，把JS代码字符串传入即可。


| 方式               | 优点                                  | 缺点                                      |
| ------------------ | ------------------------------------- | ----------------------------------------- |
| loadUrl            | 兼容性好                              | 1. 会刷新页面 2. 无法获取 js 方法执行结果 |
| evaluateJavascript | 1. 性能好 2. 可获取 js 执行后的返回值 | 仅在安卓 4.4 以上可用                     |

由于现在98%以上的手机安卓版本>=5，所以采用 evaluateJavascript。

### JS 调用 Native

JS 调用 Native 的实现方式一般至少有两种，分别是：拦截 URL Schema、注入 JS 上下文，本文介绍拦截 URL Schema的安卓实现。

**拦截 URL SCHEME 的主要流程是：前端通过某种方式（例如 iframe.src）发送 URL Scheme 请求，之后 Native 拦截到请求并根据 URL SCHEME（包括所带的参数）进行相关操作。**

#### URL SCHEME

URL SCHEME 是一种类似于url的链接，是为了方便app直接互相调用设计的，形式和普通的 url 近似，主要区别是 protocol 和 host 一般是自定义的，例如: `jsbridge://showToast?msg=hello`，protocol 是 jsbridge，host 是 showToast。

jsbridge:// 只是一种规则，可以**根据业务进行制定，使其具有含义**。

在下面实现的时候，参数使用了JSON的形式，如`jsbridge://showToast?{"data": {"msg": "hello"}, "callbackName": "callback1234"}`

#### 前端实现

接下来分别看看前端和客户端应该如何配合，实现相互通信：

规定 protocol 为 jsbridge，即 URL SCHEME 以 jsbridge:// 开头，客户端拿到 url 后进行解析，如果以 jsbridge 开头，则执行对应逻辑。

前端和 JSONP 一样，随机生成一个回调函数名，把回调函数绑定在 window 上，URL SCHEME 作为请求的 url，URL SCHEME 中包含了数据、以及回调函数名，然后使用 iframe 发送请求。

```js
class JSBridge {
  // 要请求Native的方法名，参数，回调
  static call(methodName, arg, callback) {
    // { data: {msg: "hello"}}
    const args = {
      data: arg === undefined ? null : JSON.stringify(arg),
    }
    if (typeof callback === 'function') {
      // 生成回调函数名
      const callbackName = 'CALLBACK' + parseInt(Math.random() * 10000)
      // 被调用的方法需要在 JS 全局上下文上
      window[callbackName] = callback
      // { data: {msg: "hello"}, callbackName: "CALLBACK1234"}
      args['callbackName'] = callbackName
    }
    // URL SCHEME，协议为jsbridge，方法名作为host
    const url = 'jsbridge://' + methodName + '?' + JSON.stringify(args)
    // 使用iframe发送请求，不要使用window.location.href，据说是因为多次请求会被合并为一次
    const iframe = document.createElement('iframe')
    iframe.src = url
    iframe.style.display = 'none'
    document.body.appendChild(iframe)
    window.setTimeout(() => {
        document.body.removeChild(iframe)
    }, 1000);
  }
}

const arg = {
  msg: "The message is from JS!",
};
// 请求Native的showToast方法
JSBridge.call('showToast', arg, (res) => {
  alert(res.msg);
});
```

#### 安卓实现

##### NativeMethods

既然前端请求了客户端的 showToast 方法，先来实现该方法：

```kotlin
class NativeMethods {
    fun showToast(view: WebView, arg: JSONObject, callBack: CallBack) {
        // 拿到h5传过来的msg，展示在Toast中
        val message = arg.optString("msg")
        Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
        // 执行h5的回调
        try {
            // 创建hashMap，作为res data返回给h5
            // { msg: "js 调用 native 成功！"}
            val result = JSONObject()
            result.put("msg", "js 调用 native 成功！")
            // 执行回调，h5弹出alert
            callBack.apply(result)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
```

原理很简单，拿到前端传过来的 msg，展示在 Toast 里，然后调用 JS 的回调函数，此处使用了 callBack.apply 方法，来看看该方法：

```kotlin
class CallBack(private val mWebView: WebView?, private val callbackName: String) {
    fun apply(jsonObject: JSONObject) {
        // native调用js
        mWebView?.evaluateJavascript(
          // 此处 $变量名 类似于js的${}
            "javascript:$callbackName($jsonObject)",
            ValueCallback { return@ValueCallback })
    }
}
```

CallBack 类仅仅是作了一层封装，唯一做的事就是调了 evaluateJavascript 方法，即 Native 调 JS。

callbackName 是在别的地方传给 CallBack 类的，见下文。

##### JSBridge.register

然后看看客户端的 JSBridge 类，其主要有 register 和 call 两个方法。

register 用于将客户端暴露出来的方法塞到 hashmap 中，为一个双层的 map 结构，里层 map 的 key 为方法名，val 为 method 的反射。

```json
// exposeMethods 数据结构如下，JSBridge是类名，下边有 showToast 等方法
{
	// 为什么这里不叫NativeMthods，而要注册成JSBridge呢，个人理解是，可能和业务有关
	// 可能多个类下的方法，在业务上属于JSBridge，所以把这些类的方法都打包进来
	JSBridge: {
    showToast: (showToast方法的反射)
  }
}
```

```kotlin
// object 声明单例，同时该类下的所有方法成为静态方法
object JSBridge {
    private val exposeMethods: MutableMap<String, HashMap<String?, Method>> = HashMap()
  	// 这个map是为了查出方法所属类的类名，原因之后讲
    private val classAndMethods: MutableMap<String, HashMap<String?, Method>> = HashMap()
  
    fun register(exposeName: String, classz: Class<*>, className: String) {
        val allMethods = getAllMethod(classz)
        if (!exposeMethods.containsKey(exposeName)) {
            exposeMethods[exposeName] = allMethods
        }
        if (!classAndMethods.containsKey((className))) {
            classAndMethods[className] = allMethods
        }
    }
    // register的辅助方法，收集一个类下符合条件的方法，打包成一个hashmap返回
    private fun getAllMethod(injectedCls: Class<*>): HashMap<String?, Method> {
        val methodHashMap = HashMap<String?, Method>()
        // 该类下声明的全部方法的反射
        val methods = injectedCls.declaredMethods
        for (method in methods) {
            // 判断是公有方法，如果是private，肯定不希望暴露出去被h5调用
            val modifiers = method.modifiers
            if (!Modifier.isPublic(modifiers)) {
                continue
            }
            // 第一个参数为 Webview 类的实例
          	// 第二个参数为 JSONObject 类的实例
          	// 第三个参数为 CallBack 类的实例
            val parameters = method.parameterTypes
            if (parameters.size == 3) {
                if (parameters[0] == WebView::class.java && parameters[1] == JSONObject::class.java && parameters[2] == CallBack::class.java) {
                    // 以上条件都满足，则加入 methodHashMap
                  	// 该method为反射，需要用invoke调用
                    methodHashMap[method.name] = method
                }
            }
        }
        return methodHashMap
    }
  
  fun call () { ... }
}
```

##### JSBridge.call

call 用来响应前端的请求，由上文可知，从 URL SCHEME 中可以拿到方法名，然后可以在 hashmap 中查找，拿到方法的反射，最后使用 `method.invoke()`来调用该方法。

> 注意前面声明了变量 classAndMethods，这个数据结构和 exposeMethods 类似，只不过 key 就是类名，设立这个数据结构的原因是，method.invoke() 的第一个参数要求传`所调用方法所属类的实例对象`，此处 showToast 属于 NativeMethods 类，所以要传入它的实例对象，而我实在没搞懂如何根据方法的反射，获取该类的实例对象，因此使用了不优雅的实现，即先创建了 hashmap，然后用两层 for 循环去查出类名，最后获取实例对象，当作 invoke 的第一个参数传入。

```kotlin
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
```

到这一步，还有一个问题，就是如何在哪里执行 call 方法。

webview 当收到跳转请求时，会经过 WebViewClient 类的 shouldOverrideUrlLoading 方法，因此我们需要覆写该方法。假设我们请求的 url 为`https://www.baidu.com` 如果 return false，webview 会继续加载百度的页面，如果 return true，则会拦截该请求，停止加载，这里应该将请求拦截，调用 JSBridge.call 方法。

```kotlin
import com.example.urlschema.JSBridge.call

class JSBridgeViewClient : WebViewClient() {
    // 通过覆盖 WebViewClient 类的 shouldOverrideUrlLoading 方法进行拦截
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        call(view, url)
        return true
    }
}
```

这一切都完成后，JSBridge 就可以正常工作了，如图：

<img src="https://s1.ax1x.com/2022/09/01/v5G0EV.jpg" style="zoom: 50%;" />

# 总结

JSONP 和 JSB 还是有一定相似之处的，都是由前端发起请求，构造url，告诉了对方回调函数的名称，经过 客户端/后端 的处理，再执行前端的回调函数。

由于我个人对安卓只是初步入门的水平，Java/Kotlin 也只是了解的程度，不准确的地方希望大家谅解。

## 参考资料

[mcuking/JSBridge](https://github.com/mcuking/JSBridge)

[SDBridge/SDBridgeKotlin](https://github.com/SDBridge/SDBridgeKotlin)

[Hybrid App技术解析 -- 原理篇](https://juejin.cn/post/6844903640520474637)

## Demo

[Demo，需要使用Android Studio运行](https://github.com/DanmoSAMA/kotlin-jsb)









