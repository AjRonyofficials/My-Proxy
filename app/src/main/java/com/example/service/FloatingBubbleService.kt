package com.example.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.MyProxyApp
import com.example.R
import com.example.network.GeoIpFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private var flCircle: FrameLayout? = null
    private var ivPowerIcon: ImageView? = null
    private var vStatusBeacon: View? = null
    private var tvTimer: TextView? = null
    private var tvCountryInfo: TextView? = null
    private var tvProxyIp: TextView? = null
    private var tvStatusBadge: TextView? = null

    private var secondsElapsed = 0
    private val handler = Handler(Looper.getMainLooper())
    private var geoJob: Job? = null
    private var vpnStateJob: Job? = null
    private var currentHost: String? = null
    private var currentUsername: String? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (MyProxyVpnService.isRunning) {
                secondsElapsed++
                val minutes = secondsElapsed / 60
                val seconds = secondsElapsed % 60
                tvTimer?.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val host = intent?.getStringExtra("HOST")
        val user = intent?.getStringExtra("USER")
        if (!host.isNullOrBlank()) {
            currentHost = host
            currentUsername = user
            fetchGeoDetails(host, user)
        }
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

            flCircle = view.findViewById(R.id.fl_bubble_circle)
            ivPowerIcon = view.findViewById(R.id.iv_bubble_power_icon)
            vStatusBeacon = view.findViewById(R.id.v_status_beacon)
            tvTimer = view.findViewById(R.id.tv_bubble_timer)
            tvCountryInfo = view.findViewById(R.id.tv_country_info)
            tvProxyIp = view.findViewById(R.id.tv_proxy_ip)
            tvStatusBadge = view.findViewById(R.id.tv_status_badge)

            vStatusBeacon?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
            }

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
                x = 30
                y = 220
            }

            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager = wm
            wm.addView(view, params)

            setupMessengerFloatingPhysics(view, params, wm)
            observeVpnRunningState()
            loadActiveProxyInfo()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun loadActiveProxyInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            val defaultProxy = MyProxyApp.repository.getDefaultProxy()
            val host = defaultProxy?.host
            val user = defaultProxy?.user
            currentHost = host
            currentUsername = user
            launch(Dispatchers.Main) {
                fetchGeoDetails(host, user)
            }
        }
    }

    private fun observeVpnRunningState() {
        vpnStateJob?.cancel()
        vpnStateJob = CoroutineScope(Dispatchers.Main).launch {
            MyProxyVpnService.vpnRunningFlow.collect { isRunning ->
                updateBubbleUi(isRunning)
            }
        }
    }

    private fun updateBubbleUi(isRunning: Boolean) {
        if (isRunning) {
            flCircle?.background = ContextCompat.getDrawable(this, R.drawable.bg_floating_circle_connected)
            ivPowerIcon?.setColorFilter(Color.parseColor("#00E676"))
            (vStatusBeacon?.background as? GradientDrawable)?.setColor(Color.parseColor("#00E676"))
            tvStatusBadge?.text = "ON"
            tvStatusBadge?.setTextColor(Color.parseColor("#00E676"))
            handler.removeCallbacks(timerRunnable)
            handler.post(timerRunnable)
        } else {
            flCircle?.background = ContextCompat.getDrawable(this, R.drawable.bg_floating_circle_disconnected)
            ivPowerIcon?.setColorFilter(Color.parseColor("#78909C"))
            (vStatusBeacon?.background as? GradientDrawable)?.setColor(Color.parseColor("#FF5252"))
            tvStatusBadge?.text = "OFF"
            tvStatusBadge?.setTextColor(Color.parseColor("#FF5252"))
            handler.removeCallbacks(timerRunnable)
            secondsElapsed = 0
            tvTimer?.text = "00:00"
        }
    }

    /**
     * 1-Click on floating circular head toggles connect / disconnect
     */
    private fun toggleProxyConnection() {
        if (MyProxyVpnService.isRunning) {
            val stopIntent = Intent(this, MyProxyVpnService::class.java).apply {
                action = MyProxyVpnService.ACTION_STOP
            }
            startService(stopIntent)
            Toast.makeText(this, "Proxy Disconnected", Toast.LENGTH_SHORT).show()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                val proxy = MyProxyApp.repository.getDefaultProxy()
                    ?: MyProxyApp.database.proxyDao().getAllProxies().firstOrNull()?.firstOrNull()

                if (proxy != null) {
                    val startIntent = Intent(this@FloatingBubbleService, MyProxyVpnService::class.java).apply {
                        action = MyProxyVpnService.ACTION_START
                        putExtra("ID", proxy.id)
                        putExtra("NAME", proxy.name)
                        putExtra("HOST", proxy.host)
                        putExtra("PORT", proxy.port)
                        putExtra("TYPE", proxy.type)
                        putExtra("USER", proxy.user)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(startIntent)
                    } else {
                        startService(startIntent)
                    }
                    launch(Dispatchers.Main) {
                        currentHost = proxy.host
                        currentUsername = proxy.user
                        fetchGeoDetails(proxy.host, proxy.user)
                        Toast.makeText(this@FloatingBubbleService, "Connecting: ${proxy.name}...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    launch(Dispatchers.Main) {
                        openMainActivity()
                        Toast.makeText(this@FloatingBubbleService, "Add your real proxy first", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun openMainActivity() {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(appIntent)
    }

    private fun fetchGeoDetails(host: String?, user: String?) {
        geoJob?.cancel()
        geoJob = CoroutineScope(Dispatchers.Main).launch {
            // First check if username has country alpha code
            val usernameCountry = GeoIpFetcher.extractCountryCodeFromUsername(user)
            if (usernameCountry != null) {
                val flag = GeoIpFetcher.countryCodeToEmoji(usernameCountry)
                val countryName = GeoIpFetcher.getCountryName(usernameCountry)
                tvCountryInfo?.text = "$flag $usernameCountry $countryName"
                tvProxyIp?.text = host ?: "Target Host"
            } else {
                tvCountryInfo?.text = "🔄 Resolving..."
                val geo = GeoIpFetcher.getDetails(host, user)
                tvCountryInfo?.text = "${geo.flag} ${geo.countryCode} ${geo.city}"
                tvProxyIp?.text = geo.ip
            }
        }
    }

    /**
     * Messenger-like floating physics:
     * - Freely draggable anywhere on screen
     * - On release, smoothly snaps magnetically to the nearest screen edge (left or right)
     * - Tap on circular button triggers 1-click connect/disconnect
     */
    private fun setupMessengerFloatingPhysics(view: View, params: WindowManager.LayoutParams, wm: WindowManager) {
        val circleBtn = view.findViewById<View>(R.id.fl_bubble_circle)
        val detailsPill = view.findViewById<View>(R.id.ll_bubble_details)

        detailsPill.setOnClickListener {
            openMainActivity()
        }

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
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true
                            params.x = initX + dx
                            params.y = initY + dy
                            runCatching { wm.updateViewLayout(view, params) }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            // Messenger magnetic edge-snap animation to nearest left/right screen edge
                            snapToNearestEdge(params, wm, view)
                        } else {
                            // Tap on circle button
                            val circleLoc = IntArray(2)
                            circleBtn.getLocationOnScreen(circleLoc)
                            val bounds = android.graphics.Rect(
                                circleLoc[0] - 25,
                                circleLoc[1] - 25,
                                circleLoc[0] + circleBtn.width + 25,
                                circleLoc[1] + circleBtn.height + 25
                            )
                            if (bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
                                toggleProxyConnection()
                            } else {
                                openMainActivity()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams, wm: WindowManager, view: View) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val viewWidth = if (view.width > 0) view.width else 180

        val targetX = if (params.x + viewWidth / 2 < screenWidth / 2) {
            24 // Snap to left edge
        } else {
            screenWidth - viewWidth - 24 // Snap to right edge
        }

        val startX = params.x
        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                runCatching { wm.updateViewLayout(view, params) }
            }
        }
        animator.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timerRunnable)
        geoJob?.cancel()
        vpnStateJob?.cancel()
        floatingView?.let { view ->
            runCatching {
                windowManager?.removeView(view)
            }
        }
        floatingView = null
    }
}
