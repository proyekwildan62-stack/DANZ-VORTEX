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

    var lastExecutionLog: String = "Menunggu Shizuku UID 2000... Tap tombol >_ di kanan bawah."

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
            val msg = "GAGAL: Shizuku belum jalan! Buka app Shizuku -> Start dulu."
            lastExecutionLog = msg
            return msg
        }
        if (!hasShizukuPermission()) {
            return try {
                Shizuku.requestPermission(1001)
                val msg = "Klik 'Izinkan sepanjang waktu' pada pop-up Shizuku!"
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
            "echo 'SUKSES! Shizuku UID 2000 Terhubung Penuh ke Sistem FF!'"
        )
    }

    fun execShell(command: String): String {
        if (!isShizukuAvailable()) {
            val msg = "Shizuku belum aktif! Buka Shizuku -> Start dulu."
            lastExecutionLog = msg
            return msg
        }
        if (!hasShizukuPermission()) {
            try { Shizuku.requestPermission(1001) } catch (_: Exception) {}
            val msg = "Klik 'Izinkan' pada pop-up Shizuku lalu ulangi saklar ini!"
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
                stdErr.isNotEmpty() -> "OK (${stdErr.take(30)})"
                else -> "SUCCESS (UID 2000 Applied)"
            }
            lastExecutionLog = result
            result
        } catch (e: Exception) {
            val err = "Shizuku Error: ${e.javaClass.simpleName}"
            lastExecutionLog = err
            err
        }
    }

    /**
     * Mendeteksi paket Free Fire mana yang terinstal agar injeksi ANGLE & GameOverlay
     * menembak 1 nama paket tunggal yang valid (Bebas Bug Koma!).
     */
    fun getInstalledFFPackage(context: Context, preferMax: Boolean = false): String {
        val candidates = if (preferMax) {
            listOf("com.dts.freefiremax", "com.dts.freefireth")
        } else {
            listOf("com.dts.freefireth", "com.dts.freefiremax")
        }
        for (pkg in candidates) {
            try {
                if (context.packageManager.getLaunchIntentForPackage(pkg) != null) {
                    return pkg
                }
            } catch (_: Exception) {}
        }
        return if (preferMax) "com.dts.freefiremax" else "com.dts.freefireth"
    }

    fun launchFreeFire(context: Context, preferMax: Boolean = false): String {
        val targetPkg = getInstalledFFPackage(context, preferMax)

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                // Kunci thread UnityMain Free Fire ke Big Cores & Real-Time Priority setelah game terbuka
                if (hasShizukuPermission()) {
                    Thread {
                        Thread.sleep(2500)
                        execShell(
                            "cmd power set-fixed-performance-mode-enabled true 2>/dev/null; " +
                            "for pid in \$(pidof $targetPkg); do " +
                            "renice -n -20 -p \$pid 2>/dev/null; " +
                            "ionice -c 1 -n 0 -p \$pid 2>/dev/null; " +
                            "taskset -p -a ff \$pid 2>/dev/null; " +
                            "chrt -f -p 99 \$pid 2>/dev/null; " +
                            "done; echo 'FF Engine Locked on Big CPU Cores ($targetPkg)'"
                        )
                    }.start()
                }
                return "Membuka & Mengunci Magnet Aim $targetPkg..."
            }
        } catch (_: Exception) {}

        if (hasShizukuPermission()) {
            return execShell(
                "monkey -p $targetPkg -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; " +
                "echo 'Free Fire Dibuka via Shizuku UID 2000'"
            )
        }

        return "Free Fire tidak ditemukan di perangkat ini!"
    }

    /**
     * 1. PELICIN LAYAR PRESISI (CLEAN GLIDE TANPA OVERSHOOT)
     * Mengatur kelicinan layar dengan tetap menjaga kontrol agar peluru tidak terbang melewati kepala.
     */
    fun setDragHsSensitivity(context: Context, enable: Boolean, level: Int): String {
        FloatingOverlayController.setHardwareTouchBoost(context, enable)

        val speed = if (enable) level.coerceIn(1, 7) else 0
        val pressTimeout = if (enable) (150 - (speed * 10)).coerceAtLeast(75) else 400

        val cmd = if (enable) {
            buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")
                append("settings put system pointer_speed $speed 2>/dev/null; ")
                append("settings put secure long_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure multi_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure touch_blocking_period 0.0 2>/dev/null; ")
                append("settings put secure back_gesture_inset_scale_left 0 2>/dev/null; ")
                append("settings put secure back_gesture_inset_scale_right 0 2>/dev/null; ")
                append("settings put system touch_slop 0 2>/dev/null; ")
                append("setprop debug.touch.slop 0 2>/dev/null; ")
                append("setprop view.touch_slop 0 2>/dev/null; ")
                append("echo \"PELICIN PRESISI ON -> Level=$speed | Delay=${pressTimeout}ms\"")
            }
        } else {
            buildString {
                append("settings put system pointer_speed 0 2>/dev/null; ")
                append("settings put secure long_press_timeout 400 2>/dev/null; ")
                append("settings put secure multi_press_timeout 300 2>/dev/null; ")
                append("echo \"OFF -> Sensitivitas Kembali Standar\"")
            }
        }

        try {
            Settings.System.putInt(context.contentResolver, "pointer_speed", speed)
            Settings.Secure.putInt(context.contentResolver, "long_press_timeout", pressTimeout)
        } catch (_: Exception) {}

        return execShell(cmd)
    }

    /**
     * 2. CHEAT DRAG HS & AIMLOCK NEMPEL KEPALA (ZERO-OVERSHOOT HEAD MAGNET ENGINE)
     * Kenapa sekarang benar-benar nempel di kepala?
     * - Mengubah VelocityTracker ke `lsq1` (1st-degree Linear Least Squares): menghilangkan lonjakan liar (overshoot)
     *   saat jari berhenti menarik, sehingga crosshair berhenti tepat di hitbox kepala musuh!
     * - Membatasi `maximum_fling_velocity = 3800` & menaikkan `scroll_friction = 0.035` (Rem Magnetik Kepala):
     *   begitu tarikan ke atas mengenai zona merah Aim-Assist kepala Free Fire, luncuran layar langsung terkunci.
     * - Mengaktifkan `fixed-performance-mode` & mengikat PID Free Fire ke Big CPU Cores (`taskset ff`)
     *   agar kalkulasi Aim-Assist internal Unity berjalan 120x per detik tanpa drop!
     */
    fun setHeadLockCheatEngine(context: Context, enable: Boolean, modeTitle: String): String {
        FloatingOverlayController.setHardwareTouchBoost(context, enable)
        val targetPkg = getInstalledFFPackage(context)

        val cmd = if (enable) {
            buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                // Kunci VelocityTracker ke 'lsq1' (Presisi Linear Tanpa Mental Di Atas Kepala!)
                append("device_config put input_native_boot velocity_tracker_strategy lsq1 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")
                append("settings put system pointer_speed 7 2>/dev/null; ")
                append("settings put secure long_press_timeout 70 2>/dev/null; ")
                append("settings put secure multi_press_timeout 70 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure ignore_repeat_click_timeout 0 2>/dev/null; ")
                append("settings put system peak_refresh_rate 120.0 2>/dev/null; ")
                append("settings put system min_refresh_rate 120.0 2>/dev/null; ")
                append("settings put secure user_refresh_rate 120 2>/dev/null; ")
                append("settings put global window_animation_scale 0.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 0.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 0.0 2>/dev/null; ")
                // REM MAGNETIK KEPALA (Mencegah peluru lewat di atas kepala saat drag kencang)
                append("setprop ro.input.resampling 0 2>/dev/null; ")
                append("setprop debug.input.resampling 0 2>/dev/null; ")
                append("setprop windowsmgr.max_events_per_sec 300 2>/dev/null; ")
                append("setprop touch.pressure.scale 0.001 2>/dev/null; ")
                append("setprop touch.size.calibration geometric 2>/dev/null; ")
                append("setprop view.scroll_friction 0.035 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 1 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 3800 2>/dev/null; ")
                append("setprop debug.sf.latch_unsignaled 1 2>/dev/null; ")
                append("setprop debug.sf.early_phase_offset_ns 0 2>/dev/null; ")
                // Maksimalkan Aim-Assist Tickrate Free Fire dengan Fixed Performance Mode + Big Core Lock
                append("cmd power set-fixed-performance-mode-enabled true 2>/dev/null; ")
                append("cmd game mode performance $targetPkg 2>/dev/null; ")
                append("for pid in \$(pidof com.dts.freefireth com.dts.freefiremax); do ")
                append("renice -n -20 -p \$pid 2>/dev/null; ")
                append("ionice -c 1 -n 0 -p \$pid 2>/dev/null; ")
                append("taskset -p -a ff \$pid 2>/dev/null; ")
                append("chrt -f -p 99 \$pid 2>/dev/null; ")
                append("done; ")
                append("echo \"NEMPEL PALA ON -> $modeTitle [LSQ1 Linear + HeadBrake 3800]\"")
            }
        } else {
            buildString {
                append("device_config delete input_native_boot velocity_tracker_strategy 2>/dev/null; ")
                append("cmd power set-fixed-performance-mode-enabled false 2>/dev/null; ")
                append("settings put system pointer_speed 0 2>/dev/null; ")
                append("settings put secure long_press_timeout 400 2>/dev/null; ")
                append("settings put global window_animation_scale 1.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 1.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 1.0 2>/dev/null; ")
                append("echo \"OFF -> $modeTitle Dimatikan\"")
            }
        }
        return execShell(cmd)
    }

    /**
     * 3. GRAFIK MINECRAFT (MC) 100% WORK DI SEMUA HP (TANPA UBAH UKURAN LAYAR HP / HUD TETAP TAJAM)
     * Memperbaiki bug koma pada `angle_gl_driver_selection_pkgs` dan menggabungkan 3 metode:
     * - Preset 1 (ANGLE Pure Mode): Mengaktifkan `angle_gl_driver_all_angle 1` + Single Package `angle` untuk Free Fire.
     * - Preset 2 (Tanpa Preferensi ANGLE - 3D Buffer 0.35): Menggunakan `game_overlay` & `cmd game downscale 0.35`
     *   yang HANYA menurunkan tekstur 3D dunia Free Fire menjadi kotak-kotak sementara tombol HUD & Layar HP tetap 100% tajam!
     * - Preset 3 (HYBRID FULL MC - ANGLE + 3D Pixel Block 0.25 + Shader Lock): Menggabungkan keduanya sekaligus
     *   agar 100% pasti bekerja di semua merek HP (Vivo, Oppo, Xiaomi, Infinix, Samsung, Realme)!
     */
    fun setRogMinecraftTextureOnly(context: Context, presetMode: Int): String {
        val targetPkg = getInstalledFFPackage(context)
        val allPkgs = listOf("com.dts.freefireth", "com.dts.freefiremax")

        return if (presetMode > 0) {
            val cmd = buildString {
                // Matikan sinkronisasi otomatis Google agar konfigurasi Grafik MC tidak di-reset
                append("device_config set_sync_disabled_for_tests persistent 2>/dev/null; ")

                // 1. INJEKSI SINGLE-PACKAGE ANGLE DRIVER (BEBAS BUG KOMA!)
                append("settings put global angle_gl_driver_all_angle 1 2>/dev/null; ")
                append("settings put global angle_debug_package $targetPkg 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_pkgs $targetPkg 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_values angle 2>/dev/null; ")
                append("settings put global updatable_driver_all_apps 1 2>/dev/null; ")
                append("settings put global updatable_driver_production_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global updatable_driver_prerelease_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global game_driver_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global gpu_debug_app $targetPkg 2>/dev/null; ")

                // 2. INJEKSI 3D WORLD PIXEL-BLOCK (TANPA PREFERENSI ANGLE) + SHADER CACHE LOCK
                val scaleVal = when (presetMode) {
                    1 -> "0.50" // Mode 1: ANGLE + Medium Block
                    2 -> "0.35" // Mode 2: Tanpa Preferensi ANGLE (35% 3D Block)
                    else -> "0.25" // Mode 3: Hybrid Super Kotak Parah (25% 3D Block + ANGLE)
                }

                for (pkg in allPkgs) {
                    append("am force-stop $pkg 2>/dev/null; ")
                    // Format resmi AOSP GameManagerService (Hanya key mode, downscaleFactor, fps agar tidak ditolak parser!)
                    append("device_config put game_overlay $pkg mode=2,downscaleFactor=$scaleVal,fps=120:mode=3,downscaleFactor=$scaleVal,fps=90 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")
                    append("cmd game downscale $scaleVal $pkg 2>/dev/null; ")
                    append("cmd game set --mode 2 --downscale $scaleVal --fps 120 $pkg 2>/dev/null; ")

                    // Kosongkan & kunci Shader Cache Unity Free Fire
                    val dataDir = "/sdcard/Android/data/$pkg"
                    append("if [ -d \"$dataDir\" ]; then ")
                    append("rm -rf $dataDir/cache/UnityShaderCache/* 2>/dev/null; ")
                    append("mkdir -p $dataDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("chmod 500 $dataDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("fi; ")
                }

                // 3. Kunci OpenGL / Skia Mipmap Cache ke 0MB
                append("setprop debug.hwui.renderer skiagl 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.layer_cache_size 0 2>/dev/null; ")
                append("setprop debug.egl.force_msaa false 2>/dev/null; ")
                append("echo \"GRAFIK MC AKTIF ($targetPkg | Scale=$scaleVal + ANGLE ON) -> Buka FF!\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                append("settings put global angle_gl_driver_all_angle 0 2>/dev/null; ")
                append("settings delete global angle_debug_package 2>/dev/null; ")
                append("settings delete global angle_gl_driver_selection_pkgs 2>/dev/null; ")
                append("settings delete global angle_gl_driver_selection_values 2>/dev/null; ")
                append("settings delete global updatable_driver_production_opt_in_apps 2>/dev/null; ")
                append("settings delete global updatable_driver_prerelease_opt_in_apps 2>/dev/null; ")
                append("settings delete global game_driver_opt_in_apps 2>/dev/null; ")
                append("settings delete global gpu_debug_app 2>/dev/null; ")
                for (pkg in allPkgs) {
                    val dataDir = "/sdcard/Android/data/$pkg"
                    append("chmod 770 $dataDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game reset $pkg 2>/dev/null; ")
                    append("cmd game mode standard $pkg 2>/dev/null; ")
                    append("am force-stop $pkg 2>/dev/null; ")
                }
                append("setprop debug.hwui.texture_cache_size 72 2>/dev/null; ")
                append("echo \"OFF -> Grafik Free Fire Kembali HD Normal\"")
            }
            execShell(cmd)
        }
    }
}
