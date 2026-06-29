package com.dispatcher.companion.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.dispatcher.companion.model.FieldKey
import com.dispatcher.companion.session.DispatchSession
import com.dispatcher.companion.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Floating copilot panel (Concept B): plain views in a TYPE_APPLICATION_OVERLAY
 * window — draggable, never covering the bottom 25% where RingCentral's call
 * controls live (Phase 4 interaction rule).
 */
class CopilotOverlay(private val context: Context, private val session: DispatchSession) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var root: LinearLayout
    private val pdwcrViews = mutableMapOf<FieldKey, TextView>()
    private lateinit var counterView: TextView
    private lateinit var liveView: TextView

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 24; y = 200
    }

    fun show() {
        root = buildPanel()
        wm.addView(root, params)
        scope.launch { session.fields.collect { render() } }
        scope.launch { session.advice.collect { render() } }
        scope.launch { session.liveLine.collect { render() } }
    }

    fun hide() {
        scope.cancel()
        runCatching { wm.removeView(root) }
    }

    private fun render() {
        val fields = session.fields.value
        for ((key, view) in pdwcrViews) {
            view.text = "${key.name.first()}  ${fields[key]?.text ?: "—"}"
            view.setTextColor(if (fields[key] != null) Color.WHITE else Color.parseColor("#5A6B82"))
        }
        val advice = session.advice.value
        counterView.text = if (advice != null) {
            "COUNTER $%,.0f   floor $%,.0f   %d%%".format(
                advice.counterUsd, advice.likelyFloorUsd, (advice.acceptanceProbability * 100).toInt(),
            )
        } else "COUNTER —"
        liveView.text = session.liveLine.value
            ?: session.transcript.value.lastOrNull()?.let { "${it.speaker}: ${it.text}" } ?: ""
    }

    private fun buildPanel(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(28, 20, 28, 20)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#EE15233B"))
            cornerRadius = 24f
            setStroke(4, Color.parseColor("#F97316"))
        }
        addView(TextView(context).apply {
            text = "COPILOT"
            setTextColor(Color.parseColor("#F97316"))
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12f
        })
        for (key in FieldKey.entries.filter { it.isPdwcr }) {
            val tv = TextView(context).apply { textSize = 13f }
            pdwcrViews[key] = tv
            addView(tv)
        }
        counterView = TextView(context).apply {
            setTextColor(Color.parseColor("#F97316"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
        }
        addView(counterView)
        liveView = TextView(context).apply {
            setTextColor(Color.parseColor("#9FB0D0"))
            textSize = 11f
            maxLines = 2
        }
        addView(liveView)
        addView(Button(context).apply {
            text = "EXPAND"
            textSize = 11f
            setOnClickListener {
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        })
        setOnTouchListener(DragListener())
    }

    /** Drag anywhere; snap inside the top 75% of the screen. */
    private inner class DragListener : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var touchX = 0f; private var touchY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean = when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = params.x; startY = params.y
                touchX = e.rawX; touchY = e.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = startX + (e.rawX - touchX).toInt()
                val maxY = (context.resources.displayMetrics.heightPixels * 0.75 - v.height).toInt()
                params.y = (startY + (e.rawY - touchY).toInt()).coerceIn(0, maxOf(0, maxY))
                wm.updateViewLayout(root, params)
                true
            }
            else -> false
        }
    }
}
