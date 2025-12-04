package com.kkek.assistant.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.kkek.assistant.R
import com.kkek.assistant.data.AssistantRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class BubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var params: WindowManager.LayoutParams

    @Inject
    lateinit var repository: AssistantRepository

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubbleView = LayoutInflater.from(this).inflate(R.layout.bubble_layout, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(bubbleView, params)

        val bubbleIcon = bubbleView.findViewById<ImageView>(R.id.bubble_icon)
        bubbleIcon.setImageResource(R.mipmap.ic_launcher)

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var downTime: Long = 0
            private val longPressDuration = 500L
            private val clickMoveThreshold = 10

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downTime = System.currentTimeMillis()
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val timeSinceDown = System.currentTimeMillis() - downTime
                        val xMove = abs(event.rawX - initialTouchX)
                        val yMove = abs(event.rawY - initialTouchY)

                        if (timeSinceDown < longPressDuration && xMove < clickMoveThreshold && yMove < clickMoveThreshold) {
                            // Click: Request screen capture
                            Log.d(TAG, "Bubble clicked. Requesting screen capture.")
                            repository.requestScreenCapture()
                        } else if (timeSinceDown >= longPressDuration && xMove < clickMoveThreshold && yMove < clickMoveThreshold) {
                            // Long Press: Stop the service
                            Log.d(TAG, "Bubble long-pressed. Stopping service.")
                            stopSelf()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).roundToInt()
                        params.y = initialY + (event.rawY - initialTouchY).roundToInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(bubbleView)
    }

    companion object {
        private const val TAG = "BubbleService"
    }
}
