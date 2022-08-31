class JSBridge {
  static call(methodName, arg, callback) {
    const args = {
      data: arg === undefined ? null : JSON.stringify(arg),
    }
    if (typeof callback === 'function') {
      const callbackName = 'CALLBACK' + parseInt(Math.random() * 10000)
      window[callbackName] = callback
      args['callbackName'] = callbackName
    }
    const url = 'jsbridge://' + methodName + '?' + JSON.stringify(args)
    const iframe = document.createElement('iframe')
    iframe.src = url
    iframe.style.display = 'none'
    document.body.appendChild(iframe)
    window.setTimeout(() => {
        document.body.removeChild(iframe)
    }, 1000);
  }
}
