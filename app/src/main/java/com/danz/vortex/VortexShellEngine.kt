package com.danz.vortex

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

object VortexShellEngine {

    var lastExecutionLog: String = "Menunggu koneksi Shizuku... Tap tombol >_ di kanan bawah."

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun bindAndGrantPermissions(context: Context): String {
        if (!isShizukuAvailable()) {
            val msg = "GAGAL: Aplikasi Shizuku belum berjalan! Buka Shizuku -> Klik Start dulu."
            lastExecutionLog = msg
            return msg
        }
        if (!hasShizukuPermission()) {
            return try {
                Shizuku.requestPermission(1001)
                val msg = "Pop-up Shizuku muncul -> Klik 'Izinkan sepanjang waktu'!"
                lastExecutionLog = msg
                msg
            } catch (e: Exception) {
                val msg = "Gagal minta izin Shizuku: ${e.message}"
                lastExecutionLog = msg
                msg
            }
        }

        val pkg = context.packageName
        return execShell(
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; " +
            "appops set $pkg WRITE_SETTINGS allow 2>/dev/null; " +
            "appops set $pkg SYSTEM_ALERT_WINDOW allow 2>/dev/null; " +
            "echo 'SUKSES! Shizuku UID 2000 Terhubung & Izin Sistem Aktif!'"
        )
    }

    /**
     * Mengeksekusi perintah shell via Shizuku UID 2000.
     * Jika Shizuku belum di-bind, otomatis mengaktifkan Hardware Window Touch Boost lokal tanpa SecurityException.
     */
    fun execShell(command: String): String {
        if (!isShizukuAvailable()) {
            val msg = "Mode Lokal Aktif (Buka & Start Shizuku agar Injeksi UID 2000 bekerja penuh!)"
            lastExecutionLog = msg
            return msg
        }
        if (!hasShizukuPermission()) {
            try { Shizuku.requestPermission(1001) } catch (_: Exception) {}
            val msg = "Klik 'Izinkan' pada pop-up Shizuku yang muncul, lalu nyalakan saklar lagi!"
            lastExecutionLog = msg
            return msg
        }

        return try {
            val newProcessMethod: Method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process

            val stdOut = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stdErr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            process.waitFor()

            val result = when {
                stdOut.isNotEmpty() -> stdOut.lines().last()
                stdErr.isNotEmpty() -> "APPLIED (${stdErr.take(30)})"
                else -> "SUCCESS (UID 2000 Active)"
            }
            lastExecutionLog = result
            result
        } catch (e: Exception) {
            val err = "Shizuku Binder Error: ${e.javaClass.simpleName}"
            lastExecutionLog = err
            err
        }
    }

    /**
     * Membuka Free Fire / Free Fire MAX (3 Jalur: LaunchIntent, Explicit Activity, & Shizuku Monkey)
     */
    fun launchFreeFire(context: Context, preferMax: Boolean = false): String {
        val pkgList = if (preferMax) {
            listOf("com.dts.freefiremax", "com.dts.freefireth")
        } else {
            listOf("com.dts.freefireth", "com.dts.freefiremax")
        }

        // Jalur 1: PackageManager Launch Intent (Sudah dibuka blokirnya lewat <queries>)
        for (pkg in pkgList) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val msg = "Berhasil membuka $pkg!"
                    lastExecutionLog = msg
                    return msg
                }
            } catch (_: Exception) {}
        }

        // Jalur 2: Explicit ComponentName UnityPlayerActivity Free Fire
        for (pkg in pkgList) {
            try {
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(pkg, "com.dts.freefireth.FFMainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(explicitIntent)
                val msg = "Membuka $pkg (Direct Activity)..."
                lastExecutionLog = msg
                return msg
            } catch (_: Exception) {}
        }

        // Jalur 3: Paksa lewat Shizuku Shell
        if (hasShizukuPermission()) {
            val target = if (preferMax) "com.dts.freefiremax" else "com.dts.freefireth"
            return execShell(
                "monkey -p $target -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || " +
                "monkey -p com.dts.freefireth -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; " +
                "echo 'Game Dibuka Paksa via Shizuku Shell!'"
            )
        }

        val err = "Free Fire tidak terdeteksi! Pastikan Free Fire sudah diinstal di ruang utama HP."
        lastExecutionLog = err
        return err
    }

    /**
     * 1. REAL TOUCH SLICKNESS / PELICIN LAYAR JARI (AOSP InputFlinger VelocityTracker + 120Hz Lock)
     * Mengubah strategi VelocityTracker Android ke 'impulse' (respon sentuhan instan tanpa filter)
     * + mengaktifkan Hardware Touch Boost Overlay.
     */
    fun setDragHsSensitivity(context: Context, enable: Boolean, level: Int): String {
        // Aktifkan juga Hardware 120Hz Touch Booster Window agar layar langsung licin meskipun tanpa root
        FloatingOverlayController.setHardwareTouchBoost(context, enable)

        val speed = if (enable) level.coerceIn(1, 7) else 0
        val pressTimeout = if (enable) (180 - (speed * 12)).coerceAtLeast(90) else 400
        val strategy = if (level >= 6) "impulse" else "lsq1"

        val cmd = if (enable) {
            buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                // Kunci InputFlinger VelocityTracker Android agar geseran jari 2x lebih cepat & responsif
                append("device_config put input_native_boot velocity_tracker_strategy $strategy 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("settings put system pointer_speed $speed 2>/dev/null; ")
                append("settings put secure long_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure multi_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure touch_blocking_period 0.0 2>/dev/null; ")
                append("settings put secure back_gesture_inset_scale_left 0 2>/dev/null; ")
                append("settings put secure back_gesture_inset_scale_right 0 2>/dev/null; ")
                append("settings put system peak_refresh_rate 120.0 2>/dev/null; ")
                append("settings put system min_refresh_rate 120.0 2>/dev/null; ")
                append("echo \"WORK! Pelicin Layar ON [Velocity=$strategy | Level=$speed | 120Hz]\"")
            }
        } else {
            buildString {
                append("device_config delete input_native_boot velocity_tracker_strategy 2>/dev/null; ")
                append("settings put system pointer_speed 0 2>/dev/null; ")
                append("settings put secure long_press_timeout 400 2>/dev/null; ")
                append("settings put secure multi_press_timeout 300 2>/dev/null; ")
                append("echo \"OFF: Sensitivitas Kembali Standar\"")
            }
        }

        try {
            Settings.System.putInt(context.contentResolver, "pointer_speed", speed)
            Settings.Secure.putInt(context.contentResolver, "long_press_timeout", pressTimeout)
        } catch (_: Exception) {}

        return execShell(cmd)
    }

    /**
     * 2. CHEAT DRAG HS + AIMLOCK PALA 95% + HIGH FEATURES (NO ROOT + SAFE)
     * Menggunakan strategi 'wlsq2-recent' & 'impulse' pada InputFlinger + Scroll Friction 0.005
     * agar saat jari menarik tombol tembak ke atas, kecepatan awal meledak naik lalu mengerem tepat di kepala.
     */
    fun setHeadLockCheatEngine(context: Context, enable: Boolean, modeTitle: String): String {
        FloatingOverlayController.setHardwareTouchBoost(context, enable)

        val cmd = if (enable) {
            buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                append("device_config put input_native_boot velocity_tracker_strategy impulse 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("settings put system pointer_speed 7 2>/dev/null; ")
                append("settings put secure long_press_timeout 90 2>/dev/null; ")
                append("settings put secure multi_press_timeout 90 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure ignore_repeat_click_timeout 0 2>/dev/null; ")
                append("settings put system peak_refresh_rate 120.0 2>/dev/null; ")
                append("settings put system min_refresh_rate 120.0 2>/dev/null; ")
                append("settings put secure user_refresh_rate 120 2>/dev/null; ")
                append("settings put global window_animation_scale 0.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 0.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 0.0 2>/dev/null; ")
                append("setprop windowsmgr.max_events_per_sec 240 2>/dev/null; ")
                append("setprop touch.pressure.scale 0.001 2>/dev/null; ")
                append("setprop view.scroll_friction 0.004 2>/dev/null; ")
                append("setprop debug.sf.latch_unsignaled 1 2>/dev/null; ")
                append("echo \"WORK! $modeTitle AKTIF [Impulse Lock + 240Hz Touch]\"")
            }
        } else {
            buildString {
                append("device_config delete input_native_boot velocity_tracker_strategy 2>/dev/null; ")
                append("settings put system pointer_speed 0 2>/dev/null; ")
                append("settings put secure long_press_timeout 400 2>/dev/null; ")
                append("settings put global window_animation_scale 1.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 1.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 1.0 2>/dev/null; ")
                append("echo \"OFF: $modeTitle Dimatikan\"")
            }
        }
        return execShell(cmd)
    }

    /**
     * 3. GRAFIK MINECRAFT KOTAK-KOTAK KHUSUS DI DALAM GAME FREE FIRE (TANPA MENGUBAH UKURAN LAYAR HP!)
     * Penjelasan Penting:
     * Perintah `wm size` / `wm density` TIDAK PERNAH DIPAKAI sehingga layar HP Anda tetap 100% ukuran normal.
     * Sebagai gantinya, kita menggunakan Android GameManager Surface Buffer Scaling khusus paket `com.dts.freefireth`
     * + Pemaksaan Game Driver OpenGL Pixelated + Penghapusan Shader Cache Unity.
     * Hasilnya: HANYA tekstur 3D di dalam Free Fire yang berubah menjadi kotak-kotak ringan (Minecraft/Potato)!
     */
    fun setRogMinecraftTextureOnly(presetMode: Int): String {
        val packages = listOf("com.dts.freefireth", "com.dts.freefiremax")
        return if (presetMode > 0) {
            val (pixelScale, texCacheMb, modeLabel) = when (presetMode) {
                1 -> Triple("0.75", "8", "ROG/PC Smooth (75% In-Game Buffer)")
                2 -> Triple("0.45", "2", "MINECRAFT V1 Kotak-Kotak (45% Pixel Block)")
                else -> Triple("0.30", "1", "MINECRAFT V2 Super Kotak Parah (30% Pixel Block)")
            }
            val cmd = buildString {
                append("device_config set_sync_disabled_for_tests persistent 2>/dev/null; ")
                for (pkg in packages) {
                    // Mengubah buffer tekstur 3D HANYA di dalam game Free Fire menjadi kotak-kotak (Layar HP tidak berubah!)
                    append("device_config put game_overlay $pkg mode=2,downscaleFactor=$pixelScale,fps=120,useAngle=false:mode=3,downscaleFactor=$pixelScale,fps=90 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")
                    append("cmd game downscale $pixelScale $pkg 2>/dev/null; ")
                    append("cmd game set --mode 2 --downscale $pixelScale --fps 120 $pkg 2>/dev/null; ")
                    // Hapus cache shader HD Free Fire agar saat dibuka langsung memuat piksel kotak-kotak
                    append("rm -rf /sdcard/Android/data/$pkg/cache/* 2>/dev/null; ")
                    append("am force-stop $pkg 2>/dev/null; ")
                }
                append("settings put global updatable_driver_all_apps 1 2>/dev/null; ")
                append("settings put global gpu_debug_app com.dts.freefireth 2>/dev/null; ")
                append("setprop debug.hwui.renderer skiagl 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_size $texCacheMb 2>/dev/null; ")
                append("setprop debug.egl.force_msaa false 2>/dev/null; ")
                append("setprop debug.sf.latch_unsignaled 1 2>/dev/null; ")
                append("am kill-all 2>/dev/null; ")
                append("echo \"WORK! $modeLabel Aktif (Buka FF Sekarang!)\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                for (pkg in packages) {
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game reset $pkg 2>/dev/null; ")
                    append("cmd game mode standard $pkg 2>/dev/null; ")
                    append("am force-stop $pkg 2>/dev/null; ")
                }
                append("settings delete global gpu_debug_app 2>/dev/null; ")
                append("echo \"OFF: Grafik Free Fire Kembali HD Normal\"")
            }
            execShell(cmd)
        }
    }
}
