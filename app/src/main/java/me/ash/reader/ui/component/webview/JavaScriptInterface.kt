package me.ash.reader.ui.component.webview

import android.webkit.JavascriptInterface

interface JavaScriptInterface {

    @JavascriptInterface
    fun onImgTagClick(imgUrl: String?, alt: String?)

    @JavascriptInterface
    fun onContentHeightChanged(height: Int)

    companion object {

        const val NAME = "JavaScriptInterface"
    }
}
