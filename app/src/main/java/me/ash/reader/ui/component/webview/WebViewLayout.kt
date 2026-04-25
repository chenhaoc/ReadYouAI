package me.ash.reader.ui.component.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlin.math.abs
import me.ash.reader.infrastructure.preference.ReadingFontsPreference

object WebViewLayout {

    @SuppressLint("SetJavaScriptEnabled")
    fun get(
        context: Context,
        readingFontsPreference: ReadingFontsPreference,
        webViewClient: WebViewClient,
        onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
        onContentHeightChanged: ((height: Int) -> Unit)? = null,
        onScrollDelta: ((Float) -> Unit)? = null,
    ): WebView =
        OuterScrollWebView(context = context, onScrollDelta = onScrollDelta).apply {
            this.webViewClient = webViewClient
            scrollBarSize = 0
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
            with(this.settings) {
                standardFontFamily =
                    when (readingFontsPreference) {
                        ReadingFontsPreference.Cursive -> "cursive"
                        ReadingFontsPreference.Monospace -> "monospace"
                        ReadingFontsPreference.SansSerif -> "sans-serif"
                        ReadingFontsPreference.Serif -> "serif"
                        ReadingFontsPreference.GoogleSans -> {
                            allowFileAccess = true
                            allowFileAccessFromFileURLs = true
                            "sans-serif"
                        }
                        ReadingFontsPreference.External -> {
                            allowFileAccess = true
                            allowFileAccessFromFileURLs = true
                            "sans-serif"
                        }

                        else -> "sans-serif"
                    }
                domStorageEnabled = true
                javaScriptEnabled = true
                addJavascriptInterface(
                    object : JavaScriptInterface {
                        @JavascriptInterface
                        override fun onImgTagClick(imgUrl: String?, alt: String?) {
                            if (onImageClick != null && imgUrl != null) {
                                onImageClick.invoke(imgUrl, alt ?: "")
                            }
                        }

                        @JavascriptInterface
                        override fun onContentHeightChanged(height: Int) {
                            onContentHeightChanged?.invoke(height)
                        }
                    },
                    JavaScriptInterface.NAME,
                )
                setSupportZoom(false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    isAlgorithmicDarkeningAllowed = true
                }
            }
        }

    private class OuterScrollWebView(
        context: Context,
        private val onScrollDelta: ((Float) -> Unit)?,
    ) : WebView(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downY = 0f
        private var lastY = 0f
        private var dragging = false

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (onScrollDelta == null) return super.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.y
                    lastY = event.y
                    dragging = false
                    super.onTouchEvent(event)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val totalDelta = event.y - downY
                    if (!dragging && abs(totalDelta) > touchSlop) {
                        dragging = true
                        super.onTouchEvent(
                            MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        )
                    }

                    if (dragging) {
                        onScrollDelta.invoke(lastY - event.y)
                        lastY = event.y
                        return true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val handled = if (dragging) true else super.onTouchEvent(event)
                    dragging = false
                    return handled
                }
            }

            return super.onTouchEvent(event)
        }
    }
}
