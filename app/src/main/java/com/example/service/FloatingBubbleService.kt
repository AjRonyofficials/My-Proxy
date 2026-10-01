package com.example.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.MainActivity
import com.example.R
import com.example.network.GeoIpFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var tvTimer: TextView? = null
    private var tvCountryInfo: TextView? = null
    private var tvProxyIp: TextView? = null

    private var secondsElapsed = 0
    private val handler = Handler(Looper.getMainLooper())
    private var geoJob: Job? = null
    private var currentHost: String? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            secondsElapsed++
            val minutes = secondsElapsed / 60
            val seconds = secondsElapsed % 60
            tvTimer?.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentHost = intent?.getStringExtra("HOST")
        fetchGeoDetails(currentHost)
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        try {
            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.layout_floating_bubble, null)
            floatingView = view

            tvTimer = view.findViewById(R.id.tv_bubble_timer)
            tvCountryInfo = view.findViewById(R.id.tv_country_info)
            tvProxyIp = view.findViewById(R.id.tv_proxy_ip)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 60
                y = 180
            }

            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager = wm
            wm.addView(view, params)

            view.findViewById<View>(R.id.tv_disconnect_btn)?.setOnClickListener {
                val stopVpnIntent = Intent(this, MyProxyVpnService::class.java).apply {
                    action = MyProxyVpnService.ACTION_STOP
                }
                startService(stopVpnIntent)
            }

            // Click anywhere else on bubble to open MainActivity
            view.setOnClickListener {
                val appIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(appIntent)
            }

            setupDrag(view, params, wm)
            handler.post(timerRunnable)
            fetchGeoDetails(currentHost)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun fetchGeoDetails(host: String?) {
        geoJob?.cancel()
        geoJob = CoroutineScope(Dispatchers.Main).launch {
            tvCountryInfo?.text = "🔄 Resolving..."
            val geo = GeoIpFetcher.getDetails(host)
            tvCountryInfo?.text = "${geo.flag} ${geo.countryCode} ${geo.city}"
            tvProxyIp?.text = geo.ip
        }
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams, wm: WindowManager) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0
            private var initY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = params.x
                        initY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        isDragging = false
                        return false // Allow click handling if no move
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true
                            params.x = initX + dx
                            params.y = initY + dy
                            runCatching { wm.updateViewLayout(view, params) }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        return isDragging
                    }
                }
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        geoJob?.cancel()
        floatingView?.let { view ->
            runCatching {
                windowManager?.removeView(view)
            }
        }
        floatingView = null
    }
}
