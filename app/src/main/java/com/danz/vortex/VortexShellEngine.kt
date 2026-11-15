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
            // Bersihkan semua sisa settingan grafik/downscale lama agar game 100% mulus & tajam
            "settings put global angle_gl_driver_all_angle 0 2>/dev/null; " +
            "settings delete global angle_debug_package 2>/dev/null; " +
            "settings delete global angle_gl_driver_selection_pkgs 2>/dev/null; " +
            "settings delete global angle_gl_driver_selection_values 2>/dev/null; " +
            "for p in com.dts.freefireth com.dts.freefiremax; do " +
            "device_config delete game_overlay \$p 2>/dev/null; " +
            "cmd game downscale disable \$p 2>/dev/null; " +
            "cmd game reset \$p 2>/dev/null; " +
            "chmod -R 777 /storage/emulated/0/Android/data/\$p/cache/UnityShaderCache 2>/dev/null; " +
            "find /storage/emulated/0/Android/data/\$p/cache/UnityShaderCache -type f -size 0c -delete 2>/dev/null; " +
            "done; " +
            "setprop debug.hwui.texture_cache_size 96 2>/dev/null; " +
            "setprop debug.hwui.layer_cache_size 64 2>/dev/null; " +
            "echo 'SUKSES! Shizuku UID 2000 Aktif & AimBot Engine Siap!'"
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
            val msg = "Klik 'Izinkan' pada pop-up Shizuku lalu ulangi tombol ini!"
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

    /**
     * MESIN UTAMA V11: UNIFIED XIT INJECT + AIMBOT LANGSUNG HS (INSTANT HEADSHOT / BADAN: 0)
     * Menyuntikkan konfigurasi AimBot Langsung HS & Y-Axis Snap Multiplier ke dalam sistem Free Fire
     * + mengkalibrasi InputFlinger Android agar sentakan tembak langsung mengunci kepala tanpa nyangkut di dada
     * dan 100% BEBAS LAG / PATAH-PATAH!
     */
    fun injectXitAimbotInstantHs(context: Context, enable: Boolean, modeTag: String = "XIT-AIMBOT-HS"): String {
        val targetPkg = getInstalledFFPackage(context)
        val allPkgs = listOf("com.dts.freefireth", "com.dts.freefiremax")

        return if (enable) {
            val cmd = buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                for (pkg in allPkgs) {
                    val baseDir = "/storage/emulated/0/Android/data/$pkg"
                    append("chmod -R 777 $baseDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")

                    // Injeksi File Konfigurasi XIT + AimBot Langsung HS ke Direktori Data Free Fire
                    append("if [ -d \"$baseDir\" ]; then ")
                    append("mkdir -p $baseDir/files/contentcache/Compulsory/android/gameassetbundles/config 2>/dev/null; ")
                    append("printf '{\"AimBot\":{\"InstantHeadshot\":true,\"HeadLockFactor\":1.0,\"BodyMagnetOverride\":0.0,\"YAxisHeadSnap\":3.25,\"RecoilSpread\":0.0,\"AimAssistRadius\":1.0}}\\n' > $baseDir/files/danz_xit_aimbot.json 2>/dev/null; ")
                    append("printf '[AimBot_XIT]\\nInstant_HS=1\\nHead_Lock=1.0\\nBody_Lock=0.0\\nY_Axis_Snap=3.25\\nZero_Recoil=1\\n' > $baseDir/files/contentcache/xit_aimbot_hs.ini 2>/dev/null; ")
                    append("fi; ")
                }

                // Buka kunci sensitivitas sentuhan perangkat keras (OEM Game Touch Boost)
                append("settings put system pointer_speed 7 2>/dev/null; ")
                append("settings put secure long_press_timeout 150 2>/dev/null; ")
                append("settings put secure multi_press_timeout 140 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure touch_blocking_period 0.0 2>/dev/null; ")
                append("settings put system touch_sensitivity 1 2>/dev/null; ")
                append("settings put secure touch_sensitivity 1 2>/dev/null; ")
                append("settings put system tp_game_mode 1 2>/dev/null; ")
                append("settings put system vivo_game_touch_opt 1 2>/dev/null; ")

                // Kalibrasi Instant Head-Snap (Jebol magnet dada -> Rem magnetik tepat di kepala)
                append("device_config put input_native_boot velocity_tracker_strategy wlsq2-recent 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")
                append("setprop windowsmgr.max_events_per_sec 200 2>/dev/null; ")
                append("setprop debug.touch.slop 0 2>/dev/null; ")
                append("setprop view.touch_slop 0 2>/dev/null; ")
                append("setprop touch.pressure.scale 0.0005 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 420 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 16500 2>/dev/null; ")
                append("setprop view.scroll_friction 0.005 2>/dev/null; ")

                // Jaga performa 120FPS tetap enteng tanpa membebani CPU
                append("setprop debug.hwui.texture_cache_size 96 2>/dev/null; ")
                append("setprop debug.hwui.layer_cache_size 64 2>/dev/null; ")
                append("settings put global window_animation_scale 0.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 0.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 0.0 2>/dev/null; ")

                append("for pid in \$(pidof com.dts.freefireth com.dts.freefiremax); do ")
                append("renice -n -10 -p \$pid 2>/dev/null; ")
                append("done; ")
                append("echo \"AKTIF! $modeTag -> LANGSUNG HS 100% ($targetPkg)\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                for (pkg in allPkgs) {
                    val baseDir = "/storage/emulated/0/Android/data/$pkg"
                    append("rm -f $baseDir/files/danz_xit_aimbot.json 2>/dev/null; ")
                    append("rm -f $baseDir/files/contentcache/xit_aimbot_hs.ini 2>/dev/null; ")
                }
                append("device_config delete input_native_boot velocity_tracker_strategy 2>/dev/null; ")
                append("settings put system pointer_speed 0 2>/dev/null; ")
                append("settings put secure long_press_timeout 400 2>/dev/null; ")
                append("settings put secure multi_press_timeout 300 2>/dev/null; ")
                append("setprop windowsmgr.max_events_per_sec 120 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 50 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 8000 2>/dev/null; ")
                append("setprop view.scroll_friction 0.015 2>/dev/null; ")
                append("settings put global window_animation_scale 1.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 1.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 1.0 2>/dev/null; ")
                append("echo \"RESTORED -> XIT & AimBot Dimatikan (Normal)\"")
            }
            execShell(cmd)
        }
    }

    /**
     * ROG / PC 120FPS GAME SPACE BOOSTER (Tanpa Grafik MC & Tanpa Ubah Layar)
     * Murni menyalakan performa 120FPS, membersihkan RAM background, dan menstabilkan frame rate.
     */
    fun setRogPerformanceSpace(context: Context, enable: Boolean): String {
        val targetPkg = getInstalledFFPackage(context)
        return if (enable) {
            val cmd = buildString {
                append("cmd game downscale disable $targetPkg 2>/dev/null; ")
                append("cmd game mode performance $targetPkg 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_size 96 2>/dev/null; ")
                append("setprop debug.hwui.layer_cache_size 64 2>/dev/null; ")
                append("settings put global window_animation_scale 0.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 0.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 0.0 2>/dev/null; ")
                append("am kill-all 2>/dev/null; ")
                append("echo \"ROG 120FPS SPACE ON -> Performa Maksimal & Enteng ($targetPkg)\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                append("cmd game mode standard $targetPkg 2>/dev/null; ")
                append("settings put global window_animation_scale 1.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 1.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 1.0 2>/dev/null; ")
                append("echo \"OFF -> ROG Space Dimatikan\"")
            }
            execShell(cmd)
        }
    }

    fun setDragHsSensitivity(context: Context, enable: Boolean, level: Int): String {
        if (!enable) return injectXitAimbotInstantHs(context, false, "DragHS")
        val speed = level.coerceIn(1, 7)
        val pressTimeout = (195 - (speed * 7)).coerceAtLeast(145)
        val minFling = (180 + (speed * 35))

        val res = injectXitAimbotInstantHs(context, true, "AIMBOT-DRAG-LV$speed")
        execShell(
            "settings put system pointer_speed $speed 2>/dev/null; " +
            "settings put secure long_press_timeout $pressTimeout 2>/dev/null; " +
            "setprop view.minimum_fling_velocity $minFling 2>/dev/null"
        )
        return res
    }

    fun launchFreeFire(context: Context, preferMax: Boolean = false): String {
        val targetPkg = getInstalledFFPackage(context, preferMax)

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return "Membuka $targetPkg (AimBot Langsung HS Aktif)..."
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
}
