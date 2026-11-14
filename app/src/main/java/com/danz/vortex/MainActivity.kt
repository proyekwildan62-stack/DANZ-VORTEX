package com.danz.vortex

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.math.abs

val BgDark = Color(0xFF0A0505)
val CardDark = Color(0xFF160B0B)
val CardBorder = Color(0xFF381414)
val CrimsonRed = Color(0xFFE50914)
val TextGray = Color(0xFF9E9E9E)

class CustomCrosshairView(context: Context) : View(context) {
    var styleIndex: Int = 0
    var crosshairColor: Int = android.graphics.Color.RED
    var sizePx: Float = 36f
    var strokeWidthPx: Float = 4f
    var alphaVal: Int = 255

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val half = sizePx / 2f

        paint.color = crosshairColor
        paint.alpha = alphaVal
        paint.strokeWidth = strokeWidthPx

        when (styleIndex) {
            0 -> {
                paint.style = Paint.Style.STROKE
                val gap = sizePx * 0.2f
                canvas.drawLine(cx - half, cy, cx - gap, cy, paint)
                canvas.drawLine(cx + gap, cy, cx + half, cy, paint)
                canvas.drawLine(cx, cy - half, cx, cy - gap, paint)
                canvas.drawLine(cx, cy + gap, cx, cy + half, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, strokeWidthPx * 0.9f, paint)
            }
            1 -> {
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, half, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, strokeWidthPx * 1.1f, paint)
            }
            2 -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, half * 0.45f, paint)
                paint.style = Paint.Style.STROKE
                paint.color = android.graphics.Color.BLACK
                paint.strokeWidth = 2f
                canvas.drawCircle(cx, cy, half * 0.45f, paint)
            }
            3 -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - half, cy - half, cx + half, cy + half, paint)
                canvas.drawLine(cx + half, cy - half, cx - half, cy + half, paint)
            }
            4 -> {
                paint.style = Paint.Style.STROKE
                canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, strokeWidthPx, paint)
            }
            5 -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - half, cy, cx + half, cy, paint)
                canvas.drawLine(cx, cy, cx, cy + half, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, strokeWidthPx, paint)
            }
        }
    }
}

object FloatingOverlayController {
    private var crosshairView: CustomCrosshairView? = null
    private var crosshairParams: WindowManager.LayoutParams? = null

    private var monitorLayout: LinearLayout? = null
    private var monitorTextView: TextView? = null
    private var monitorParams: WindowManager.LayoutParams? = null
    private var fpsRunning = false

    private var touchBoostView: View? = null

    var currentStyle = 0
    var currentColor = android.graphics.Color.RED
    var currentSize = 42f
    var currentStroke = 4.5f
    var currentOffsetX = 0
    var currentOffsetY = 0

    var isMonitorMiniMode = true
    var monitorTextSizeSp = 9.5f
    private var latestFps = 60
    private var latestRam = 1500L

    fun isCrosshairActive(): Boolean = crosshairView != null
    fun isMonitorActive(): Boolean = monitorLayout != null

    fun setHardwareTouchBoost(context: Context, enable: Boolean) {
        if (!Settings.canDrawOverlays(context)) return
        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (enable) {
            if (touchBoostView != null) return
            val v = View(appCtx)
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                preferredRefreshRate = 120f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    preferMinimalPostProcessing = true
                }
            }
            try {
                wm.addView(v, params)
                touchBoostView = v
            } catch (_: Exception) {}
        } else {
            touchBoostView?.let {
                try { wm.removeView(it) } catch (_: Exception) {}
                touchBoostView = null
            }
        }
    }

    fun toggleCrosshair(context: Context, enable: Boolean) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Izinkan 'Tampilkan di atas aplikasi lain' terlebih dahulu!", Toast.LENGTH_LONG).show()
            return
        }
        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (enable) {
            if (crosshairView != null) {
                updateCrosshairConfig(context)
                return
            }
            val view = CustomCrosshairView(appCtx).apply {
                styleIndex = currentStyle
                crosshairColor = currentColor
                sizePx = currentSize
                strokeWidthPx = currentStroke
            }
            val params = WindowManager.LayoutParams(
                180,
                180,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                x = currentOffsetX
                y = currentOffsetY
                preferredRefreshRate = 120f
            }
            wm.addView(view, params)
            crosshairView = view
            crosshairParams = params
        } else {
            crosshairView?.let {
                wm.removeView(it)
                crosshairView = null
                crosshairParams = null
            }
        }
    }

    fun updateCrosshairConfig(context: Context) {
        val view = crosshairView ?: return
        val params = crosshairParams ?: return
        val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        view.styleIndex = currentStyle
        view.crosshairColor = currentColor
        view.sizePx = currentSize
        view.strokeWidthPx = currentStroke
        view.invalidate()

        params.x = currentOffsetX
        params.y = currentOffsetY
        wm.updateViewLayout(view, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun togglePerfMonitor(context: Context, enable: Boolean) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Izinkan 'Tampilkan di atas aplikasi lain' terlebih dahulu!", Toast.LENGTH_LONG).show()
            return
        }
        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (enable) {
            if (monitorLayout != null) {
                updateMonitorStyle()
                return
            }
            val bgDrawable = GradientDrawable().apply {
                setColor(android.graphics.Color.argb(155, 18, 5, 5))
                cornerRadius = 24f
                setStroke(2, android.graphics.Color.argb(190, 229, 9, 20))
            }
            val layout = LinearLayout(appCtx).apply {
                orientation = LinearLayout.HORIZONTAL
                background = bgDrawable
                setPadding(16, 6, 16, 6)
            }
            val tvStats = TextView(appCtx).apply {
                textSize = monitorTextSizeSp
                setTextColor(android.graphics.Color.rgb(255, 80, 80))
                text = "⚡ -- FPS"
            }
            layout.addView(tvStats)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 24
                y = 60
                preferredRefreshRate = 120f
            }

            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f
            layout.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        wm.updateViewLayout(layout, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(event.rawX - touchX) < 12 && abs(event.rawY - touchY) < 12) {
                            isMonitorMiniMode = !isMonitorMiniMode
                            updateMonitorStyle()
                        }
                        true
                    }
                    else -> false
                }
            }

            wm.addView(layout, params)
            monitorLayout = layout
            monitorTextView = tvStats
            monitorParams = params
            fpsRunning = true
            updateMonitorStyle()

            var frameCount = 0
            var lastTime = System.nanoTime()
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!fpsRunning) return
                    frameCount++
                    val diff = (frameTimeNanos - lastTime) / 1_000_000_000.0
                    if (diff >= 1.0) {
                        latestFps = (frameCount / diff).toInt()
                        val am = appCtx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        val memInfo = ActivityManager.MemoryInfo()
                        am.getMemoryInfo(memInfo)
                        latestRam = memInfo.availMem / (1024 * 1024)
                        updateMonitorStyle()
                        frameCount = 0
                        lastTime = frameTimeNanos
                    }
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
            Choreographer.getInstance().postFrameCallback(callback)
        } else {
            fpsRunning = false
            monitorLayout?.let {
                wm.removeView(it)
                monitorLayout = null
                monitorTextView = null
                monitorParams = null
            }
        }
    }

    fun updateMonitorStyle() {
        val tv = monitorTextView ?: return
        val layout = monitorLayout ?: return
        tv.textSize = monitorTextSizeSp
        if (isMonitorMiniMode) {
            layout.setPadding(14, 5, 14, 5)
            tv.text = "⚡ ${latestFps} FPS"
        } else {
            layout.setPadding(20, 8, 20, 8)
            tv.text = "DANZVORTEX | ${latestFps} FPS | RAM: ${latestRam}MB"
        }
    }
}

class MainActivity : ComponentActivity() {

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Thread {
                VortexShellEngine.bindAndGrantPermissions(applicationContext)
            }.start()
            Toast.makeText(this, "Shizuku UID 2000 Terhubung Penuh!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Izin Shizuku ditolak! Klik Izinkan agar fitur aktif.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgDark,
                    surface = CardDark,
                    primary = CrimsonRed
                )
            ) {
                DanzVortexApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}
    }
}

@Composable
fun DanzVortexApp() {
    val context = LocalContext.current
    var currentRole by remember { mutableStateOf(LicenseEngine.getSavedRole(context)) }
    val myHwid = remember { LicenseEngine.getDeviceHwid(context) }

    if (currentRole == null) {
        LicenseLoginScreen(
            hwid = myHwid,
            onSuccess = { currentRole = LicenseEngine.getSavedRole(context) }
        )
    } else {
        MainDashboard(
            role = currentRole!!,
            hwid = myHwid,
            onLogout = {
                LicenseEngine.logout(context)
                currentRole = null
            }
        )
    }
}

@Composable
fun LicenseLoginScreen(hwid: String, onSuccess: () -> Unit) {
    val context = LocalContext.current
    var inputKey by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("Masukkan Lisensi 1 Key 1 Device atau Key Developer") }

    Box(
        modifier = Modifier.fillMaxSize().background(BgDark).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .border(1.dp, CrimsonRed, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("DANZVORTEX", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("1 KEY 1 DEVICE SECURITY", color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF221010))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("YOUR DEVICE HWID", color = TextGray, fontSize = 10.sp)
                    Text(hwid, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                TextButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("HWID", hwid))
                    Toast.makeText(context, "HWID Disalin!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("COPY", color = CrimsonRed, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = inputKey,
                onValueChange = { inputKey = it },
                label = { Text("License Key / Dev Key", color = TextGray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(statusMsg, color = CrimsonRed, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val res = LicenseEngine.verifyAndActivateKey(context, inputKey)
                    statusMsg = res.second
                    if (res.first) onSuccess()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("UNLOCK DANZVORTEX", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun MainDashboard(role: String, hwid: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var liveShellOutput by remember { mutableStateOf(VortexShellEngine.lastExecutionLog) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(300)
        if (VortexShellEngine.isShizukuAvailable()) {
            val res = withContext(Dispatchers.IO) {
                VortexShellEngine.bindAndGrantPermissions(context)
            }
            liveShellOutput = res
        }
    }

    fun executeRealCommand(block: () -> String) {
        scope.launch {
            isProcessing = true
            val out = withContext(Dispatchers.IO) { block() }
            liveShellOutput = out
            delay(350)
            isProcessing = false
        }
    }

    Scaffold(
        containerColor = BgDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    executeRealCommand {
                        VortexShellEngine.bindAndGrantPermissions(context)
                    }
                },
                containerColor = CrimsonRed,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(">_", color = Color.White, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF140909))
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavChip(icon = Icons.Default.Home, label = "Home", selected = currentTab == 0) { currentTab = 0 }
                    BottomNavChip(icon = Icons.Default.GridView, label = "Feature", selected = currentTab == 1) { currentTab = 1 }
                    BottomNavChip(icon = Icons.Default.Bolt, label = "Vortex", selected = currentTab == 2) { currentTab = 2 }
                    BottomNavChip(icon = Icons.Default.Person, label = "Key/Admin", selected = currentTab == 3) { currentTab = 3 }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (currentTab) {
                                0 -> "Home"
                                1 -> "Main Menu"
                                2 -> "Vortex Cheat"
                                else -> "Key Manager"
                            },
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = when (currentTab) {
                                0 -> "Hybrid ANGLE & Grafik MC Engine"
                                1 -> "Zero-Overshoot Slickness & Visual"
                                2 -> "High Features (Nempel Kepala)"
                                else -> "1 Key 1 Device & Reseller Panel"
                            },
                            color = CrimsonRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("By Danz", color = TextGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2B1010))
                                .border(1.dp, CrimsonRed, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("VIP", color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF140707))
                        .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "SHELL LOG: $liveShellOutput",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                when (currentTab) {
                    0 -> HomeTabScreen(context) { executeRealCommand(it) }
                    1 -> FeatureTabScreen(context) { executeRealCommand(it) }
                    2 -> VortexHighTabScreen(context) { executeRealCommand(it) }
                    3 -> AdminKeyTabScreen(role, hwid, onLogout)
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x77000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1F0808))
                            .border(3.dp, CrimsonRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(44.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF2B1212) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) CrimsonRed else TextGray, modifier = Modifier.size(20.dp))
        if (selected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = CrimsonRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun HomeTabScreen(context: Context, onRun: (() -> String) -> Unit) {
    val prefs = remember { context.getSharedPreferences("danz_toggles", Context.MODE_PRIVATE) }
    var tacticalRec by remember { mutableStateOf(prefs.getBoolean("tacticalRec", false)) }
    var rogSpace by remember { mutableStateOf(prefs.getBoolean("rogSpace", false)) }
    var selectedMinecraftPreset by remember { mutableStateOf(prefs.getInt("mcPreset", 3)) }

    val shizukuGranted = VortexShellEngine.hasShizukuPermission()
    val detectedFF = remember { VortexShellEngine.getInstalledFFPackage(context) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("Environment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1F0E0E)).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF3DDC84), modifier = Modifier.size(42.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Android ${Build.VERSION.RELEASE}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1F0E0E)).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(42.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(Build.HARDWARE.uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                EnvRow("Target Game Detected", detectedFF)
                Spacer(modifier = Modifier.height(8.dp))
                EnvRow("Head-Magnet Engine", "LSQ1 Linear + Big Core Lock")
                Spacer(modifier = Modifier.height(8.dp))
                EnvRow("Shizuku UID 2000", if (shizukuGranted) "GRANTED (100% WORK)" else "NOT BOUND (TAP >_)")
            }
        }

        item {
            ToggleFeatureCard(
                title = "Precision Head-Brake Slickness (Anti-Lewat Kepala)",
                subtitle = "Melicinkan tarikan awal namun langsung mengerem tepat di hitbox kepala (Zero Overshoot).",
                checked = tacticalRec,
                onCheckedChange = {
                    tacticalRec = it
                    prefs.edit().putBoolean("tacticalRec", it).apply()
                    onRun { VortexShellEngine.setHeadLockCheatEngine(context, it, "HeadBrakeSlick") }
                }
            )
        }

        // GAME SPACE GRAFIK MINECRAFT V6 (SINGLE-PACKAGE ANGLE + 3D WORLD PIXEL BLOCK)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
                    .border(1.dp, if (rogSpace) CrimsonRed else CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Danz Game Space (Grafik MC All-Device V6)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Mengubah tekstur 3D Free Fire menjadi kotak-kotak/polos ala Minecraft (Ukuran layar HP & tombol HUD tetap 100% normal).",
                            color = TextGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = rogSpace,
                        onCheckedChange = {
                            rogSpace = it
                            prefs.edit().putBoolean("rogSpace", it).apply()
                            onRun {
                                VortexShellEngine.setRogMinecraftTextureOnly(context, if (it) selectedMinecraftPreset else 0)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CrimsonRed,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = Color(0xFF2A1515)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Pilih Mesin Grafik MC (Support Semua HP):", color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                val presets = listOf(
                    Triple(1, "Mode 1: Preferensi ANGLE + Medium MC", "Single-Package ANGLE Driver + 0.50 3D World Block"),
                    Triple(2, "Mode 2: Tanpa Preferensi ANGLE (All Device)", "3D World Pixel Block 0.35 + Shader Strip (HUD Tetap Tajam)"),
                    Triple(3, "Mode 3: HYBRID EXTREME MC (Persis Foto)", "ANGLE Global ON + 0.25 3D Pixel Block + Zero Texture Cache")
                )

                presets.forEach { (modeId, title, desc) ->
                    val isSelected = selectedMinecraftPreset == modeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF2B1010) else Color(0xFF140808))
                            .border(1.dp, if (isSelected) CrimsonRed else CardBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                selectedMinecraftPreset = modeId
                                prefs.edit().putInt("mcPreset", modeId).apply()
                                if (rogSpace) {
                                    onRun { VortexShellEngine.setRogMinecraftTextureOnly(context, modeId) }
                                }
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(desc, color = TextGray, fontSize = 11.sp)
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedMinecraftPreset = modeId
                                prefs.edit().putInt("mcPreset", modeId).apply()
                                if (rogSpace) {
                                    onRun { VortexShellEngine.setRogMinecraftTextureOnly(context, modeId) }
                                }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = CrimsonRed, unselectedColor = TextGray)
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .border(1.dp, CrimsonRed, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(CrimsonRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SportsEsports, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Free Fire System Injector & Launcher", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Wajib buka FF dari tombol ini agar Grafik MC & Lock Kepala aktif", color = TextGray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onRun {
                                if (rogSpace) VortexShellEngine.setRogMinecraftTextureOnly(context, selectedMinecraftPreset)
                                VortexShellEngine.launchFreeFire(context, preferMax = false)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("BUKA FREE FIRE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            onRun {
                                if (rogSpace) VortexShellEngine.setRogMinecraftTextureOnly(context, selectedMinecraftPreset)
                                VortexShellEngine.launchFreeFire(context, preferMax = true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF331212)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("BUKA FF MAX", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EnvRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1F0E0E))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextGray, fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
fun FeatureTabScreen(context: Context, onRun: (() -> String) -> Unit) {
    val prefs = remember { context.getSharedPreferences("danz_toggles", Context.MODE_PRIVATE) }
    var subTab by remember { mutableStateOf(0) }

    var sensGameLab by remember { mutableStateOf(prefs.getBoolean("sensGameLab", false)) }
    var dynSens by remember { mutableStateOf(prefs.getBoolean("dynSens", false)) }
    var sensLevel by remember { mutableStateOf(prefs.getFloat("sensLevel", 7f)) }
    var motionResp by remember { mutableStateOf(prefs.getBoolean("motionResp", false)) }
    var sensAdjust by remember { mutableStateOf(prefs.getBoolean("sensAdjust", false)) }
    var fpsBooster by remember { mutableStateOf(prefs.getBoolean("fpsBooster", false)) }
    var hyperTouch by remember { mutableStateOf(prefs.getBoolean("hyperTouch", false)) }

    var crosshairAim by remember { mutableStateOf(FloatingOverlayController.isCrosshairActive()) }
    var chStyle by remember { mutableStateOf(FloatingOverlayController.currentStyle) }
    var chSize by remember { mutableStateOf(FloatingOverlayController.currentSize) }
    var chStroke by remember { mutableStateOf(FloatingOverlayController.currentStroke) }
    var chColor by remember { mutableStateOf(FloatingOverlayController.currentColor) }
    var chOffsetX by remember { mutableStateOf(FloatingOverlayController.currentOffsetX.toFloat()) }
    var chOffsetY by remember { mutableStateOf(FloatingOverlayController.currentOffsetY.toFloat()) }

    var perfMonitor by remember { mutableStateOf(FloatingOverlayController.isMonitorActive()) }
    var monitorMini by remember { mutableStateOf(FloatingOverlayController.isMonitorMiniMode) }
    var monitorFontSize by remember { mutableStateOf(FloatingOverlayController.monitorTextSizeSp) }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Configs & Head-Brake", "Visual (Crosshair & Monitor)").forEachIndexed { idx, title ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (subTab == idx) CrimsonRed else CardDark)
                        .border(1.dp, if (subTab == idx) CrimsonRed else CardBorder, RoundedCornerShape(8.dp))
                        .clickable { subTab = idx }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        if (subTab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ToggleFeatureCard(
                        title = "Linear LSQ1 Touch Matrix (Anti-Mental Kepala)",
                        subtitle = "Mematikan extrapolasi sentuhan Android agar saat jari berhenti, bidikan langsung berhenti di kepala.",
                        checked = sensGameLab,
                        onCheckedChange = {
                            sensGameLab = it
                            prefs.edit().putBoolean("sensGameLab", it).apply()
                            onRun { VortexShellEngine.setHeadLockCheatEngine(context, it, "LSQ1Matrix") }
                        }
                    )
                }
                item {
                    ToggleFeatureCard(
                        title = "Dynamic Drag Sensitivity",
                        subtitle = "Memangkas jeda sentuhan (long_press_timeout 70ms) & mematikan Palm Rejection.",
                        checked = dynSens,
                        onCheckedChange = {
                            dynSens = it
                            prefs.edit().putBoolean("dynSens", it).apply()
                            onRun { VortexShellEngine.setDragHsSensitivity(context, it, sensLevel.toInt()) }
                        }
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardDark)
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Text("Level Kelicinan Layar (1 - 7)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Mengatur kecepatan angkatan awal drag tanpa mengubah DPI/Ukuran Layar HP.",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                        Slider(
                            value = sensLevel,
                            onValueChange = { sensLevel = it },
                            onValueChangeFinished = {
                                prefs.edit().putFloat("sensLevel", sensLevel).apply()
                                onRun { VortexShellEngine.setDragHsSensitivity(context, true, sensLevel.toInt()) }
                            },
                            valueRange = 1f..7f,
                            steps = 5,
                            colors = SliderDefaults.colors(thumbColor = CrimsonRed, activeTrackColor = CrimsonRed)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            (1..7).forEach { lv ->
                                Text("LEV.$lv", color = if (sensLevel.toInt() == lv) CrimsonRed else TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item {
                    ToggleFeatureCard(
                        title = "Motion Response (120Hz Hardware Lock)",
                        subtitle = "Memaksa layar tetap berjalan di 120Hz/90Hz saat bermain Free Fire.",
                        checked = motionResp,
                        onCheckedChange = {
                            motionResp = it
                            prefs.edit().putBoolean("motionResp", it).apply()
                            onRun { VortexShellEngine.setHeadLockCheatEngine(context, it, "Motion120Hz") }
                        }
                    )
                }
                item {
                    ToggleFeatureCard(
                        title = "Sensitivity Adjust (Zero Resampling Lag)",
                        subtitle = "Mematikan ro.input.resampling agar posisi crosshair 1:1 dengan gerakan jempol.",
                        checked = sensAdjust,
                        onCheckedChange = {
                            sensAdjust = it
                            prefs.edit().putBoolean("sensAdjust", it).apply()
                            onRun { VortexShellEngine.setDragHsSensitivity(context, it, 7) }
                        }
                    )
                }
                item {
                    ToggleFeatureCard(
                        title = "FPS Booster (Hybrid Grafik MC V6)",
                        subtitle = "Mengaktifkan Hybrid ANGLE + 3D World Pixel Block 0.25 untuk FPS maksimal.",
                        checked = fpsBooster,
                        onCheckedChange = {
                            fpsBooster = it
                            prefs.edit().putBoolean("fpsBooster", it).apply()
                            onRun { VortexShellEngine.setRogMinecraftTextureOnly(context, if (it) 3 else 0) }
                        }
                    )
                }
                item {
                    ToggleFeatureCard(
                        title = "Hyper Touch Response 300Hz",
                        subtitle = "Meningkatkan pembacaan sampel sentuhan ke 300 events/detik yang stabil.",
                        checked = hyperTouch,
                        onCheckedChange = {
                            hyperTouch = it
                            prefs.edit().putBoolean("hyperTouch", it).apply()
                            onRun { VortexShellEngine.setHeadLockCheatEngine(context, it, "HyperTouch300") }
                        }
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardDark)
                            .border(1.dp, if (perfMonitor) CrimsonRed else CardBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mini Performance Monitor (Draggable)", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text("Bisa digeser dengan jari & dikecilkan agar tidak menghalangi pandangan!", color = TextGray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = perfMonitor,
                                onCheckedChange = {
                                    perfMonitor = it
                                    FloatingOverlayController.togglePerfMonitor(context, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonRed)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mode Kapsul Mini (Hanya ⚡ FPS):", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = monitorMini,
                                onCheckedChange = {
                                    monitorMini = it
                                    FloatingOverlayController.isMonitorMiniMode = it
                                    FloatingOverlayController.updateMonitorStyle()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonRed)
                            )
                        }

                        Text("Ukuran Teks Monitor (${monitorFontSize.toInt()} sp):", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = monitorFontSize,
                            onValueChange = {
                                monitorFontSize = it
                                FloatingOverlayController.monitorTextSizeSp = it
                                FloatingOverlayController.updateMonitorStyle()
                            },
                            valueRange = 7f..16f,
                            colors = SliderDefaults.colors(thumbColor = CrimsonRed, activeTrackColor = CrimsonRed)
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardDark)
                            .border(1.dp, if (crosshairAim) CrimsonRed else CardBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Custom Crosshair Aim-Assistant", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text("Bidikan melayang di atas Free Fire (Custom Bentuk, Warna, Ukuran, Posisi)", color = TextGray, fontSize = 12.sp)
                            }
                            Switch(
                                checked = crosshairAim,
                                onCheckedChange = {
                                    crosshairAim = it
                                    FloatingOverlayController.toggleCrosshair(context, it)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CrimsonRed)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("1. Pilih Bentuk Crosshair:", color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        val styles = listOf(
                            Pair(0, "╋ Plus Gap"),
                            Pair(1, "⊕ Circle Dot"),
                            Pair(2, "◉ Pure Dot"),
                            Pair(3, "✕ Hitmark X"),
                            Pair(4, "[+] Box Aim"),
                            Pair(5, "┳ Pro T-Aim")
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            styles.chunked(3).forEach { rowStyles ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowStyles.forEach { (sIdx, sLabel) ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (chStyle == sIdx) CrimsonRed else Color(0xFF241010))
                                                .border(1.dp, CrimsonRed, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    chStyle = sIdx
                                                    FloatingOverlayController.currentStyle = sIdx
                                                    FloatingOverlayController.updateCrosshairConfig(context)
                                                }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(sLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("2. Pilih Warna Crosshair:", color = CrimsonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        val colorOptions = listOf(
                            Pair(android.graphics.Color.RED, Color.Red),
                            Pair(android.graphics.Color.GREEN, Color(0xFF00E676)),
                            Pair(android.graphics.Color.CYAN, Color.Cyan),
                            Pair(android.graphics.Color.YELLOW, Color.Yellow),
                            Pair(android.graphics.Color.MAGENTA, Color.Magenta),
                            Pair(android.graphics.Color.WHITE, Color.White)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            colorOptions.forEach { (androidCol, composeCol) ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(composeCol)
                                        .border(
                                            width = if (chColor == androidCol) 3.dp else 1.dp,
                                            color = if (chColor == androidCol) Color.White else Color.Black,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            chColor = androidCol
                                            FloatingOverlayController.currentColor = androidCol
                                            FloatingOverlayController.updateCrosshairConfig(context)
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("3. Ukuran Crosshair (${chSize.toInt()} px):", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = chSize,
                            onValueChange = {
                                chSize = it
                                FloatingOverlayController.currentSize = it
                                FloatingOverlayController.updateCrosshairConfig(context)
                            },
                            valueRange = 14f..100f,
                            colors = SliderDefaults.colors(thumbColor = CrimsonRed, activeTrackColor = CrimsonRed)
                        )

                        Text("4. Ketebalan Garis (${chStroke.toInt()} px):", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = chStroke,
                            onValueChange = {
                                chStroke = it
                                FloatingOverlayController.currentStroke = it
                                FloatingOverlayController.updateCrosshairConfig(context)
                            },
                            valueRange = 1.5f..12f,
                            colors = SliderDefaults.colors(thumbColor = CrimsonRed, activeTrackColor = CrimsonRed)
                        )

                        Text("5. Geser Posisi X (${chOffsetX.toInt()}) & Y (${chOffsetY.toInt()}):", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = chOffsetX,
                            onValueChange = {
                                chOffsetX = it
                                FloatingOverlayController.currentOffsetX = it.toInt()
                                FloatingOverlayController.updateCrosshairConfig(context)
                            },
                            valueRange = -200f..200f,
                            colors = SliderDefaults.colors(thumbColor = CrimsonRed, activeTrackColor = CrimsonRed)
                        )
                        Slider(
                            value = chOffsetY,
                            onValueChange = {
                                chOffsetY = it
                                FloatingOverlayController.currentOffsetY = it.toInt()
                                FloatingOverlayController.updateCrosshairConfig(context)
                            },
                            valueRange = -200f..200f,
                            colors = SliderDefaults.colors(thumbColor = CrimsonRed, activeTrackColor = CrimsonRed)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VortexHighTabScreen(context: Context, onRun: (() -> String) -> Unit) {
    val prefs = remember { context.getSharedPreferences("danz_toggles", Context.MODE_PRIVATE) }

    val vortexFeatures = remember {
        mutableStateListOf(
            Triple("DRAG HS (CHEAT) [NEMPEL PALA]", "Injeksi LSQ1 Linear Velocity + Rem Kepala (MaxFling 3800) agar sekali tarik langsung berhenti tepat di kepala.", prefs.getBoolean("vf_0", false)),
            Triple("AIMLOCK (CHEAT) [MAGNET HITBOX]", "Mematikan Touch Resampling & mengunci UnityMain ke Big CPU Cores agar Red-Dot Aim Assist lengket di kepala.", prefs.getBoolean("vf_1", false)),
            Triple("Headtick 95% [LOCK HEAD]", "Meningkatkan akurasi recoil dan mengunci pengereman vertikal di area kepala (Safe No Root).", prefs.getBoolean("vf_2", false)),
            Triple("AimDrag [ZERO OVERSHOOT PACK]", "Paket lengkap LSQ1 Linear + 70ms tap delay + Scroll Friction 0.035 agar peluru tidak lewat di atas kepala.", prefs.getBoolean("vf_3", false)),
            Triple("Controller Recoil [NO SHAKE]", "Menstabilkan getaran input sentuhan beruntun agar peluru lurus di satu titik kepala.", prefs.getBoolean("vf_4", false)),
            Triple("Aim Tracking 95%", "Kalibrasi respons sentuhan 1:1 untuk mengikuti musuh lari tanpa jeda.", prefs.getBoolean("vf_5", false)),
            Triple("Stabilizer Head [SG2 / M1887]", "Khusus SG2 / M1887 di Training & Clash Squad: menahan lonjakan akhir drag agar nempel di kepala.", prefs.getBoolean("vf_6", false)),
            Triple("Headlock VIP [FIXED PERFORMANCE]", "Aktifkan Android Fixed Performance Mode + chrt 99 Real-Time pada proses Free Fire.", prefs.getBoolean("vf_7", false))
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(vortexFeatures.size) { idx ->
            val item = vortexFeatures[idx]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1A0909), Color(0xFF330A0A), Color(0xFF120606))
                        )
                    )
                    .border(1.5.dp, if (item.third) CrimsonRed else CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(item.first, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(item.second, color = TextGray, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Switch(
                        checked = item.third,
                        onCheckedChange = { checked ->
                            vortexFeatures[idx] = item.copy(third = checked)
                            prefs.edit().putBoolean("vf_$idx", checked).apply()
                            onRun {
                                VortexShellEngine.setDragHsSensitivity(context, checked, 7)
                                VortexShellEngine.setHeadLockCheatEngine(context, checked, item.first.take(18))
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CrimsonRed,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = Color(0xFF2A1515)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AdminKeyTabScreen(role: String, myHwid: String, onLogout: () -> Unit) {
    val context = LocalContext.current
    var targetHwid by remember { mutableStateOf(myHwid) }
    var selectedDays by remember { mutableStateOf(1) }
    var generatedKey by remember { mutableStateOf("") }

    val durations = listOf(
        Pair("1 DAY", 1),
        Pair("3 DAY", 3),
        Pair("7 DAY", 7),
        Pair("15 DAY", 15),
        Pair("30 DAY", 30),
        Pair("PERMANENT", 9999)
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Text("Status Lisensi & Shizuku ADB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Role Aktif: $role", color = CrimsonRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("HWID Perangkat Ini: $myHwid", color = TextGray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { VortexShellEngine.bindAndGrantPermissions(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                    ) {
                        Text("Bind ADB Shizuku", color = Color.White, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onLogout) {
                        Text("Logout Key", color = CrimsonRed, fontSize = 12.sp)
                    }
                }
            }
        }

        if (role == "DEVELOPER" || role == "RESELLER") {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .border(1.dp, CrimsonRed, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Text("PANEL DEVELOPER / RESELLER KEY GENERATOR", color = CrimsonRed, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("Sistem 1 Key 1 Device — Masukkan HWID pembeli lalu pilih durasi hari.", color = TextGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = targetHwid,
                        onValueChange = { targetHwid = it },
                        label = { Text("HWID Target Pembeli (10 Karakter)", color = TextGray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonRed,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Pilih Durasi Key:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        durations.chunked(3).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { (label, days) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selectedDays == days) CrimsonRed else Color(0xFF241010))
                                            .border(1.dp, CrimsonRed, RoundedCornerShape(8.dp))
                                            .clickable { selectedDays = days }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (targetHwid.isBlank()) {
                                Toast.makeText(context, "Masukkan HWID pembeli!", Toast.LENGTH_SHORT).show()
                            } else {
                                generatedKey = LicenseEngine.generateKey(targetHwid, selectedDays)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CREATE KEY (1 KEY 1 DEVICE)", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    if (generatedKey.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF241010))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(generatedKey, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("LicenseKey", generatedKey))
                                Toast.makeText(context, "Key berhasil disalin!", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("COPY", color = CrimsonRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToggleFeatureCard(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardDark)
            .border(1.dp, if (checked) CrimsonRed else CardBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = TextGray, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CrimsonRed,
                uncheckedThumbColor = TextGray,
                uncheckedTrackColor = Color(0xFF2A1515)
            )
        )
    }
}
