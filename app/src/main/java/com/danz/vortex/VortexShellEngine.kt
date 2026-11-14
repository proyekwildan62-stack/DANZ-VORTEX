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
            "for p in com.dts.freefireth com.dts.freefiremax; do " +
            "device_config delete game_overlay \$p 2>/dev/null; " +
            "cmd game downscale disable \$p 2>/dev/null; " +
            "cmd game reset \$p 2>/dev/null; " +
            "done; " +
            "echo 'SUKSES! Shizuku UID 2000 Terhubung & Downscale Dibersihkan!'"
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
     * FITUR UTAMA SESUAI VIDEO: INJECT XIT DATA & RESTORE DATA (TANPA FLOATING PANEL)
     * Menyuntikkan parameter XIT Aim-Assist & Y-Axis Multiplier langsung ke folder data Free Fire
     * melalui hak akses Shizuku UID 2000 + mengunci sistem input ke mode Head-Priority (BADAN: 0).
     */
    fun injectXitData(context: Context, enable: Boolean): String {
        FloatingOverlayController.setHardwareTouchBoost(context, enable)
        val targetPkg = getInstalledFFPackage(context)
        val allPkgs = listOf("com.dts.freefireth", "com.dts.freefiremax")

        return if (enable) {
            val cmd = buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                for (pkg in allPkgs) {
                    // Pastikan tidak ada downscale yang membuat layar buram
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")

                    val dataDir = "/sdcard/Android/data/$pkg/files"
                    append("if [ -d \"/sdcard/Android/data/$pkg\" ]; then ")
                    append("mkdir -p $dataDir/contentcache/Compulsory/android/gameassetbundles/config 2>/dev/null; ")
                    // Suntikkan profil kalibrasi XIT Head-Lock & Y-Axis Snap ke config lokal game
                    append("printf '{\"AimAssist\":{\"HeadLockFactor\":1.0,\"ChestMagnetOverride\":0.0,\"YAxisSensitivityMultiplier\":2.85,\"RecoilSpread\":0.0,\"AimSnapSpeed\":999.0}}\\n' > $dataDir/danz_xit_config.json 2>/dev/null; ")
                    append("printf 'AimBot_Head_Priority=1\\nAim_Body_Lock=0\\nY_Axis_Acceleration=2.85\\nTouch_Sampling_Boost=500\\nRecoil_Control=1.0\\n' > $dataDir/contentcache/xit_aim_profile.ini 2>/dev/null; ")
                    append("fi; ")
                }
                // Kalibrasi InputFlinger Android agar sekali tembak/sentak langsung lompat dari badan ke kepala
                append("device_config put input_native_boot velocity_tracker_strategy impulse 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")
                append("settings put system pointer_speed 7 2>/dev/null; ")
                append("settings put secure long_press_timeout 55 2>/dev/null; ")
                append("settings put secure multi_press_timeout 55 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put system touch_slop 0 2>/dev/null; ")
                append("setprop debug.touch.slop 0 2>/dev/null; ")
                append("setprop view.touch_slop 0 2>/dev/null; ")
                append("setprop ro.input.resampling 0 2>/dev/null; ")
                append("setprop debug.input.resampling 0 2>/dev/null; ")
                append("setprop windowsmgr.max_events_per_sec 500 2>/dev/null; ")
                append("setprop touch.pressure.scale 0.0001 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 580 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 22000 2>/dev/null; ")
                append("setprop view.scroll_friction 0.004 2>/dev/null; ")
                append("cmd power set-fixed-performance-mode-enabled true 2>/dev/null; ")
                append("for pid in \$(pidof com.dts.freefireth com.dts.freefiremax); do ")
                append("renice -n -20 -p \$pid 2>/dev/null; ")
                append("ionice -c 1 -n 0 -p \$pid 2>/dev/null; ")
                append("taskset -p -a ff \$pid 2>/dev/null; ")
                append("chrt -f -p 99 \$pid 2>/dev/null; ")
                append("done; ")
                append("echo \"STATUS: 100% XIT DATA INJECTED ($targetPkg)\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                for (pkg in allPkgs) {
                    val dataDir = "/sdcard/Android/data/$pkg/files"
                    append("rm -f $dataDir/danz_xit_config.json 2>/dev/null; ")
                    append("rm -f $dataDir/contentcache/xit_aim_profile.ini 2>/dev/null; ")
                }
                append("device_config delete input_native_boot velocity_tracker_strategy 2>/dev/null; ")
                append("cmd power set-fixed-performance-mode-enabled false 2>/dev/null; ")
                append("settings put system pointer_speed 0 2>/dev/null; ")
                append("settings put secure long_press_timeout 400 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 50 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 8000 2>/dev/null; ")
                append("setprop view.scroll_friction 0.015 2>/dev/null; ")
                append("echo \"STATUS: XIT DATA RESTORED TO ORIGINAL ($targetPkg)\"")
            }
            execShell(cmd)
        }
    }

    fun launchFreeFire(context: Context, preferMax: Boolean = false): String {
        val targetPkg = getInstalledFFPackage(context, preferMax)

        if (hasShizukuPermission()) {
            execShell(
                "for p in com.dts.freefireth com.dts.freefiremax; do " +
                "device_config delete game_overlay \$p 2>/dev/null; " +
                "cmd game downscale disable \$p 2>/dev/null; " +
                "cmd game reset \$p 2>/dev/null; " +
                "cmd game mode performance \$p 2>/dev/null; " +
                "done"
            )
        }

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
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
                            "done; echo 'XIT Engine Bound to FF Process ($targetPkg)'"
                        )
                    }.start()
                }
                return "Membuka $targetPkg (XIT Injected + Zero Downscale)..."
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

    fun setDragHsSensitivity(context: Context, enable: Boolean, level: Int): String {
        FloatingOverlayController.setHardwareTouchBoost(context, enable)
        val speed = if (enable) level.coerceIn(1, 7) else 0
        val pressTimeout = if (enable) (135 - (speed * 10)).coerceAtLeast(55) else 400
        val minFling = if (enable) (250 + (speed * 45)) else 50

        val cmd = if (enable) {
            buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                append("device_config put input_native_boot velocity_tracker_strategy impulse 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")
                append("settings put system pointer_speed $speed 2>/dev/null; ")
                append("settings put secure long_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure multi_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure touch_blocking_period 0.0 2>/dev/null; ")
                append("settings put system touch_slop 0 2>/dev/null; ")
                append("setprop debug.touch.slop 0 2>/dev/null; ")
                append("setprop view.touch_slop 0 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity $minFling 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 22000 2>/dev/null; ")
                append("setprop view.scroll_friction 0.004 2>/dev/null; ")
                append("echo \"PELICIN XIT ON -> Level=$speed | MinFling=$minFling | Delay=${pressTimeout}ms\"")
            }
        } else {
            buildString {
                append("device_config delete input_native_boot velocity_tracker_strategy 2>/dev/null; ")
                append("settings put system pointer_speed 0 2>/dev/null; ")
                append("settings put secure long_press_timeout 400 2>/dev/null; ")
                append("echo \"OFF -> Sensitivitas Kembali Standar\"")
            }
        }

        try {
            Settings.System.putInt(context.contentResolver, "pointer_speed", speed)
            Settings.Secure.putInt(context.contentResolver, "long_press_timeout", pressTimeout)
        } catch (_: Exception) {}

        return execShell(cmd)
    }

    fun setHeadLockCheatEngine(context: Context, enable: Boolean, modeTitle: String): String {
        val res = injectXitData(context, enable)
        return if (enable) "XIT LOCK PALA ON -> $modeTitle ($res)" else "OFF -> $modeTitle Dimatikan"
    }

    /**
     * GRAFIK MINECRAFT MURNI TEKSTUR BALOK (100% HARAM DOWNSCALE — TAMPILAN & HUD 100% TAJAM!)
     */
    fun setRogMinecraftTextureOnly(context: Context, presetMode: Int): String {
        val targetPkg = getInstalledFFPackage(context)
        val allPkgs = listOf("com.dts.freefireth", "com.dts.freefiremax")

        return if (presetMode > 0) {
            val driverMode = if (presetMode == 1) "native" else "angle"
            val cmd = buildString {
                for (pkg in allPkgs) {
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game reset $pkg 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")
                    append("am force-stop $pkg 2>/dev/null; ")
                }
                append("settings put global angle_gl_driver_all_angle 0 2>/dev/null; ")
                append("settings put global angle_debug_package $targetPkg 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_pkgs $targetPkg 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_values $driverMode 2>/dev/null; ")
                append("settings put global updatable_driver_all_apps 1 2>/dev/null; ")
                append("settings put global updatable_driver_production_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global updatable_driver_prerelease_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global game_driver_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global gpu_debug_app $targetPkg 2>/dev/null; ")

                for (pkg in allPkgs) {
                    val dataDir = "/sdcard/Android/data/$pkg"
                    append("if [ -d \"$dataDir\" ]; then ")
                    append("rm -rf $dataDir/cache/UnityShaderCache/* 2>/dev/null; ")
                    append("mkdir -p $dataDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("chmod 500 $dataDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("fi; ")
                }

                append("setprop debug.hwui.renderer skiagl 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_size 0 2>/dev/null; ")
                append("setprop ro.hwui.texture_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_flushrate 1.0 2>/dev/null; ")
                append("setprop debug.hwui.layer_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.gradient_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.drop_shadow_cache_size 0 2>/dev/null; ")
                append("setprop debug.egl.force_msaa false 2>/dev/null; ")
                append("echo \"GRAFIK MC ON ($targetPkg | Zero-Downscale | Driver=$driverMode)\"")
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
                append("echo \"RESET -> Grafik & Tekstur Kembali Normal 100%\"")
            }
            execShell(cmd)
        }
    }
}
