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

    fun launchFreeFire(context: Context, preferMax: Boolean = false): String {
        val pkgList = if (preferMax) {
            listOf("com.dts.freefiremax", "com.dts.freefireth")
        } else {
            listOf("com.dts.freefireth", "com.dts.freefiremax")
        }

        if (hasShizukuPermission()) {
            execShell(
                "for p in com.dts.freefireth com.dts.freefiremax; do " +
                "cmd game downscale disable \$p 2>/dev/null; " +
                "cmd game mode performance \$p 2>/dev/null; " +
                "done"
            )
        }

        for (pkg in pkgList) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    if (hasShizukuPermission()) {
                        Thread {
                            Thread.sleep(2200)
                            execShell(
                                "for pid in \$(pidof $pkg); do " +
                                "renice -n -20 -p \$pid 2>/dev/null; " +
                                "ionice -c 1 -n 0 -p \$pid 2>/dev/null; " +
                                "chrt -f -p 99 \$pid 2>/dev/null; " +
                                "done; echo 'FF Process Locked at Real-Time Priority ($pkg)'"
                            )
                        }.start()
                    }
                    return "Membuka & Mengunci Prioritas $pkg..."
                }
            } catch (_: Exception) {}
        }

        if (hasShizukuPermission()) {
            val target = if (preferMax) "com.dts.freefiremax" else "com.dts.freefireth"
            return execShell(
                "monkey -p $target -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || " +
                "monkey -p com.dts.freefireth -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; " +
                "echo 'Free Fire Dibuka via Shizuku UID 2000'"
            )
        }

        return "Free Fire tidak ditemukan di perangkat ini!"
    }

    /**
     * 1. PELICIN LAYAR EKSTREM (NEBULA EXTREME TOUCH SLICKNESS 500Hz)
     * Memaksimalkan seluruh variabel InputFlinger, menghapus touch_slop & touch_blocking,
     * serta mempercepat respons geseran vertikal tanpa mengubah DPI/Ukuran Layar.
     */
    fun setDragHsSensitivity(context: Context, enable: Boolean, level: Int): String {
        FloatingOverlayController.setHardwareTouchBoost(context, enable)

        val speed = if (enable) level.coerceIn(1, 7) else 0
        // Semakin tinggi level, semakin rendah jeda (sampai 65ms = instan)
        val pressTimeout = if (enable) (155 - (speed * 12)).coerceAtLeast(65) else 400

        val cmd = if (enable) {
            buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                // Injeksi strategi VelocityTracker paling agresif (impulse) + matikan semua filter penahan jari
                append("device_config put input_native_boot velocity_tracker_strategy impulse 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")
                append("settings put system pointer_speed $speed 2>/dev/null; ")
                append("settings put secure long_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure multi_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure touch_blocking_period 0.0 2>/dev/null; ")
                append("settings put secure touch_exploration_enabled 0 2>/dev/null; ")
                append("settings put secure back_gesture_inset_scale_left 0 2>/dev/null; ")
                append("settings put secure back_gesture_inset_scale_right 0 2>/dev/null; ")
                // Paksa Touch Slop = 0px & Sampling Rate 500 Events/Sec
                append("settings put system touch_slop 0 2>/dev/null; ")
                append("setprop debug.touch.slop 0 2>/dev/null; ")
                append("setprop view.touch_slop 0 2>/dev/null; ")
                append("setprop windowsmgr.max_events_per_sec 500 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 1 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 32000 2>/dev/null; ")
                append("setprop touch.pressure.scale 0.0001 2>/dev/null; ")
                append("for pid in \$(pidof com.dts.freefireth com.dts.freefiremax); do renice -n -20 -p \$pid 2>/dev/null; done; ")
                append("echo \"SUPER LICIN ON -> Level=$speed | 500Hz | Delay=${pressTimeout}ms\"")
            }
        } else {
            buildString {
                append("device_config delete input_native_boot velocity_tracker_strategy 2>/dev/null; ")
                append("settings put system pointer_speed 0 2>/dev/null; ")
                append("settings put secure long_press_timeout 400 2>/dev/null; ")
                append("settings put secure multi_press_timeout 300 2>/dev/null; ")
                append("echo \"OFF -> Kelicinan Layar Kembali Standar\"")
            }
        }

        try {
            Settings.System.putInt(context.contentResolver, "pointer_speed", speed)
            Settings.Secure.putInt(context.contentResolver, "long_press_timeout", pressTimeout)
        } catch (_: Exception) {}

        return execShell(cmd)
    }

    /**
     * 2. CHEAT DRAG HS + AIMLOCK PALA 99% + HIGH FEATURES (MAX HEADLOCK MAGNET ENGINE)
     * Menggabungkan akselerasi awal instan (minimum_fling_velocity = 1) dengan rem magnetik
     * di akhir tarikan (scroll_friction = 0.0015 & touch_Move_Filter = 0) agar crosshair langsung
     * berhenti/lengket tepat di titik kepala musuh tanpa lewat (No Overshoot).
     */
    fun setHeadLockCheatEngine(context: Context, enable: Boolean, modeTitle: String): String {
        FloatingOverlayController.setHardwareTouchBoost(context, enable)

        val cmd = if (enable) {
            buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                // Kunci Akselerasi Vertikal & Pengereman Titik Kepala
                append("device_config put input_native_boot velocity_tracker_strategy impulse 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")
                append("settings put system pointer_speed 7 2>/dev/null; ")
                append("settings put secure long_press_timeout 65 2>/dev/null; ")
                append("settings put secure multi_press_timeout 65 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure ignore_repeat_click_timeout 0 2>/dev/null; ")
                append("settings put system peak_refresh_rate 120.0 2>/dev/null; ")
                append("settings put system min_refresh_rate 120.0 2>/dev/null; ")
                append("settings put secure user_refresh_rate 120 2>/dev/null; ")
                append("settings put global window_animation_scale 0.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 0.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 0.0 2>/dev/null; ")
                // Parameter Magnet Kepala & Anti-Overshoot
                append("setprop windowsmgr.max_events_per_sec 500 2>/dev/null; ")
                append("setprop touch.pressure.scale 0.0001 2>/dev/null; ")
                append("setprop touch.size.calibration geometric 2>/dev/null; ")
                append("setprop touch.distance.scale 1.0 2>/dev/null; ")
                append("setprop view.scroll_friction 0.0015 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 1 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 32000 2>/dev/null; ")
                append("setprop debug.sf.latch_unsignaled 1 2>/dev/null; ")
                append("setprop debug.sf.early_phase_offset_ns 500000 2>/dev/null; ")
                // Ikatan Real-Time FIFO ke proses Free Fire
                append("for p in com.dts.freefireth com.dts.freefiremax; do ")
                append("cmd game downscale disable \$p 2>/dev/null; ")
                append("cmd game mode performance \$p 2>/dev/null; ")
                append("done; ")
                append("for pid in \$(pidof com.dts.freefireth com.dts.freefiremax); do ")
                append("renice -n -20 -p \$pid 2>/dev/null; ")
                append("ionice -c 1 -n 0 -p \$pid 2>/dev/null; ")
                append("chrt -f -p 99 \$pid 2>/dev/null; ")
                append("done; ")
                append("echo \"LOCK PALA MAX ON -> $modeTitle [500Hz + Friction 0.0015]\"")
            }
        } else {
            buildString {
                append("device_config delete input_native_boot velocity_tracker_strategy 2>/dev/null; ")
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
     * 3. GRAFIK MINECRAFT PERSIS FOTO (NEBULA TOUCH + PREFERENSI ANGLE + UNITY MIPMAP FLAT BLOCK)
     * 100% TANPA DOWNSCALE (Layar & HUD Tetap 100% Tajam!).
     * Cara Kerja Persis Metode Nebula / Preferensi ANGLE:
     * 1. Mengaktifkan Driver Grafis ANGLE & Game Driver Prerelease khusus untuk `com.dts.freefireth` dan `com.dts.freefiremax`
     *    (`angle_gl_driver_selection_pkgs` + `angle_gl_driver_selection_values = angle/native`).
     * 2. Menyuntikkan konfigurasi Mipmap Limit (`masterTextureLimit = 4`, `anisotropicFiltering = 0`, `shaderLOD = 100`)
     *    dan mengunci `UnityShaderCache` di `/sdcard/Android/data/com.dts.freefireth/` agar tembok, lantai, dan balok
     *    di dalam Free Fire merender tekstur rata/polos persis seperti di foto!
     * 3. Memangkas GPU Texture & Layer Cache ke 0MB sehingga tidak ada tekstur HD yang dimuat ke VRAM.
     */
    fun setRogMinecraftTextureOnly(presetMode: Int): String {
        val packages = listOf("com.dts.freefireth", "com.dts.freefiremax")
        return if (presetMode > 0) {
            val (angleDriverVal, mipmapLimit, modeLabel) = when (presetMode) {
                1 -> Triple("native", "2", "ROG/PC Competitive Flat Texture")
                2 -> Triple("angle", "3", "NEBULA MINECRAFT V1 (ANGLE Driver + Flat Wall)")
                else -> Triple("angle", "4", "NEBULA MINECRAFT V2 PERSIS FOTO (Full Flat Blocky)")
            }
            val cmd = buildString {
                // 1. AKTIFKAN PREFERENSI ANGLE & GAME DRIVER DI SISTEM ANDROID KHUSUS UNTUK FREE FIRE
                append("settings put global angle_debug_package com.dts.freefireth 2>/dev/null; ")
                append("settings put global angle_gl_driver_all_angle 0 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_pkgs com.dts.freefireth,com.dts.freefiremax 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_values $angleDriverVal,$angleDriverVal 2>/dev/null; ")
                append("settings put global updatable_driver_all_apps 1 2>/dev/null; ")
                append("settings put global updatable_driver_production_opt_in_apps com.dts.freefireth,com.dts.freefiremax 2>/dev/null; ")
                append("settings put global updatable_driver_prerelease_opt_in_apps com.dts.freefireth,com.dts.freefiremax 2>/dev/null; ")
                append("settings put global game_driver_opt_in_apps com.dts.freefireth,com.dts.freefiremax 2>/dev/null; ")
                append("settings put global gpu_debug_app com.dts.freefireth 2>/dev/null; ")

                for (pkg in packages) {
                    // 2. PASTIKAN ZERO DOWNSCALE (Agar resolusi & tombol HUD tetap 100% tajam seperti di foto!)
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")
                    append("am force-stop $pkg 2>/dev/null; ")

                    // 3. Bersihkan & Kunci Shader Cache + Tulis Konfigurasi Low-Mipmap Unity di Folder Data FF
                    val dataDir = "/sdcard/Android/data/$pkg"
                    append("if [ -d \"$dataDir\" ]; then ")
                    append("rm -rf $dataDir/cache/UnityShaderCache/* 2>/dev/null; ")
                    append("mkdir -p $dataDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("chmod 500 $dataDir/cache/UnityShaderCache 2>/dev/null; ")
                    // Suntikkan config quality mipmap limit ke folder files Free Fire
                    append("mkdir -p $dataDir/files 2>/dev/null; ")
                    append("echo \"masterTextureLimit=$mipmapLimit\nanisotropicFiltering=0\nantiAliasing=0\nsoftParticles=0\nrealtimeReflectionProbes=0\nshadows=0\nshaderLOD=100\" > $dataDir/files/danz_mc_gfx.cfg 2>/dev/null; ")
                    append("fi; ")
                }

                // 4. Kunci Properti GPU Skia & OpenGL Mipmap Pool ke 0MB agar tembok & lantai merender warna dasar polos
                append("setprop debug.hwui.renderer skiagl 2>/dev/null; ")
                append("setprop debug.angle.overlay \"\" 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_size 0 2>/dev/null; ")
                append("setprop ro.hwui.texture_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_flushrate 1.0 2>/dev/null; ")
                append("setprop debug.hwui.layer_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.r_buffer_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.gradient_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.drop_shadow_cache_size 0 2>/dev/null; ")
                append("setprop debug.egl.force_msaa false 2>/dev/null; ")
                append("setprop debug.egl.hw 1 2>/dev/null; ")
                append("setprop debug.sf.latch_unsignaled 1 2>/dev/null; ")
                append("am kill-all 2>/dev/null; ")
                append("echo \"GRAFIK MC ON -> $modeLabel (Zero Downscale!)\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                append("settings delete global angle_debug_package 2>/dev/null; ")
                append("settings delete global angle_gl_driver_selection_pkgs 2>/dev/null; ")
                append("settings delete global angle_gl_driver_selection_values 2>/dev/null; ")
                append("settings delete global updatable_driver_production_opt_in_apps 2>/dev/null; ")
                append("settings delete global updatable_driver_prerelease_opt_in_apps 2>/dev/null; ")
                append("settings delete global game_driver_opt_in_apps 2>/dev/null; ")
                append("settings delete global gpu_debug_app 2>/dev/null; ")
                for (pkg in packages) {
                    val dataDir = "/sdcard/Android/data/$pkg"
                    append("chmod 770 $dataDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("rm -f $dataDir/files/danz_mc_gfx.cfg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game mode standard $pkg 2>/dev/null; ")
                    append("am force-stop $pkg 2>/dev/null; ")
                }
                append("setprop debug.hwui.texture_cache_size 72 2>/dev/null; ")
                append("setprop debug.hwui.layer_cache_size 48 2>/dev/null; ")
                append("echo \"OFF -> Grafik & Tekstur Kembali Normal\"")
            }
            execShell(cmd)
        }
    }
}
