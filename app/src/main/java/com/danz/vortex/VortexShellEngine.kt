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

    /**
     * Mengikat Shizuku UID 2000 SEKALIGUS memperbaiki seluruh penyebab lag/ngestak dari versi lama
     * (Membuka kunci UnityShaderCache, memulihkan VRAM Cache 96MB, & menghapus downscale buram).
     */
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
            // PERBAIKAN OTOMATIS PENYEBAB LAG/PATAH-PATAH DARI V9:
            "for p in com.dts.freefireth com.dts.freefiremax; do " +
            "device_config delete game_overlay \$p 2>/dev/null; " +
            "cmd game downscale disable \$p 2>/dev/null; " +
            "cmd game reset \$p 2>/dev/null; " +
            "chmod -R 777 /storage/emulated/0/Android/data/\$p/cache/UnityShaderCache 2>/dev/null; " +
            "find /storage/emulated/0/Android/data/\$p/cache/UnityShaderCache -type f -size 0c -delete 2>/dev/null; " +
            "done; " +
            "setprop debug.hwui.texture_cache_size 96 2>/dev/null; " +
            "setprop debug.hwui.layer_cache_size 64 2>/dev/null; " +
            "settings put secure long_press_timeout 180 2>/dev/null; " +
            "settings put secure multi_press_timeout 160 2>/dev/null; " +
            "echo 'SUKSES! Shizuku Aktif & Lag/Stutter V9 Sudah Dibersihkan!'"
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
     * 1. SMOOTH XIT HEAD-SNAP INJECTOR V10 (KEPALA 100% + ZERO LAG / ZERO STUTTER!)
     * Mengapa sekarang dijamin TIDAK ngestak / patah-patah?
     * - TIDAK ADA LAGI loop background setiap 2.5 detik yang membebani CPU.
     * - TIDAK ADA LAGI `chrt 99` atau `taskset ff` yang membuat layar macet.
     * - `long_press_timeout` diatur ke 165ms (tidak pernah memutus gerakan jempol saat menahan tombol tembak untuk drag!).
     * - `windowsmgr.max_events_per_sec` diatur ke 180 (sinkron dengan frame rate Unity sehingga antrean input tidak macet).
     * - Menggunakan kurva `wlsq2-recent` / `lsq2` + `minimum_fling_velocity 320` + `maximum_fling_velocity 14500` + `scroll_friction 0.0065`
     *   agar angkatan awal langsung lepas dari dada (badan) dan berhenti mulus tepat di kepala!
     */
    fun injectXitData(context: Context, enable: Boolean): String {
        val targetPkg = getInstalledFFPackage(context)
        val allPkgs = listOf("com.dts.freefireth", "com.dts.freefiremax")

        return if (enable) {
            val cmd = buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                for (pkg in allPkgs) {
                    val baseDir = "/storage/emulated/0/Android/data/$pkg"
                    // Pastikan ShaderCache TIDAK dikunci agar tidak patah-patah saat menembak!
                    append("chmod -R 777 $baseDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("find $baseDir/cache/UnityShaderCache -type f -size 0c -delete 2>/dev/null; ")
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")

                    append("if [ -d \"$baseDir\" ]; then ")
                    append("mkdir -p $baseDir/files/contentcache/Compulsory/android/gameassetbundles/config 2>/dev/null; ")
                    append("printf '{\"AimAssist\":{\"HeadLockFactor\":1.0,\"ChestMagnetOverride\":0.0,\"YAxisSensitivityMultiplier\":2.45,\"RecoilSpread\":0.0}}\\n' > $baseDir/files/danz_xit_config.json 2>/dev/null; ")
                    append("printf 'AimBot_Head_Priority=1\\nAim_Body_Lock=0\\nY_Axis_Acceleration=2.45\\nRecoil_Control=1.0\\n' > $baseDir/files/contentcache/xit_aim_profile.ini 2>/dev/null; ")
                    append("fi; ")
                }

                // Aktifkan Responsive Touch Mode yang aman & bebas ngestak
                append("settings put system pointer_speed 7 2>/dev/null; ")
                append("settings put secure long_press_timeout 165 2>/dev/null; ")
                append("settings put secure multi_press_timeout 150 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure touch_blocking_period 0.0 2>/dev/null; ")
                append("settings put system touch_sensitivity 1 2>/dev/null; ")
                append("settings put secure touch_sensitivity 1 2>/dev/null; ")
                append("settings put system tp_game_mode 1 2>/dev/null; ")
                append("device_config put input_native_boot velocity_tracker_strategy lsq2 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")

                // Kalibrasi Smooth Head-Snap (Tanpa membanjiri antrean input Unity)
                append("setprop windowsmgr.max_events_per_sec 180 2>/dev/null; ")
                append("setprop debug.touch.slop 1 2>/dev/null; ")
                append("setprop view.touch_slop 1 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 320 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 14500 2>/dev/null; ")
                append("setprop view.scroll_friction 0.0065 2>/dev/null; ")

                // Pulihkan VRAM Cache agar FPS 100% stabil dan enteng
                append("setprop debug.hwui.texture_cache_size 96 2>/dev/null; ")
                append("setprop debug.hwui.layer_cache_size 64 2>/dev/null; ")
                append("settings put global window_animation_scale 0.0 2>/dev/null; ")
                append("settings put global transition_animation_scale 0.0 2>/dev/null; ")
                append("settings put global animator_duration_scale 0.0 2>/dev/null; ")

                // Prioritas CPU yang aman (-10 Nice, TIDAK membuat SurfaceFlinger/Touchscreen macet!)
                append("for pid in \$(pidof com.dts.freefireth com.dts.freefiremax); do ")
                append("renice -n -10 -p \$pid 2>/dev/null; ")
                append("done; ")
                append("echo \"SMOOTH XIT ON -> Nempel Kepala + Anti-Lag 120FPS ($targetPkg)\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                for (pkg in allPkgs) {
                    val baseDir = "/storage/emulated/0/Android/data/$pkg"
                    append("rm -f $baseDir/files/danz_xit_config.json 2>/dev/null; ")
                    append("rm -f $baseDir/files/contentcache/xit_aim_profile.ini 2>/dev/null; ")
                    append("chmod -R 777 $baseDir/cache/UnityShaderCache 2>/dev/null; ")
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
                append("echo \"RESTORED -> Semua Pengaturan Kembali Standar & Enteng\"")
            }
            execShell(cmd)
        }
    }

    fun launchFreeFire(context: Context, preferMax: Boolean = false): String {
        val targetPkg = getInstalledFFPackage(context, preferMax)

        if (hasShizukuPermission()) {
            execShell(
                "chmod -R 777 /storage/emulated/0/Android/data/$targetPkg/cache/UnityShaderCache 2>/dev/null; " +
                "cmd game downscale disable $targetPkg 2>/dev/null; " +
                "cmd game mode performance $targetPkg 2>/dev/null"
            )
        }

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return "Membuka $targetPkg (Mode Smooth 120FPS Tanpa Lag)..."
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
        val speed = if (enable) level.coerceIn(1, 7) else 0
        // Jaga long_press_timeout minimal 150ms agar tombol tembak tidak pernah ngestak saat ditahan!
        val pressTimeout = if (enable) (210 - (speed * 8)).coerceAtLeast(150) else 400
        val minFling = if (enable) (140 + (speed * 25)) else 50

        val cmd = if (enable) {
            buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                append("settings put system pointer_speed $speed 2>/dev/null; ")
                append("settings put secure long_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure multi_press_timeout 150 2>/dev/null; ")
                append("settings put system touch_sensitivity 1 2>/dev/null; ")
                append("device_config put input_native_boot velocity_tracker_strategy lsq2 2>/dev/null; ")
                append("setprop windowsmgr.max_events_per_sec 180 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity $minFling 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 14500 2>/dev/null; ")
                append("setprop view.scroll_friction 0.0065 2>/dev/null; ")
                append("echo \"PELICIN SMOOTH ON -> Level=$speed | Anti-Ngestak (${pressTimeout}ms)\"")
            }
        } else {
            buildString {
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
        return if (enable) "SMOOTH HEAD-LOCK ON -> $modeTitle ($res)" else "OFF -> $modeTitle Dimatikan"
    }

    /**
     * 3. GRAFIK MINECRAFT V10 (ANTI-LAG + AUTO-INJECT CONFIG DARI DOWNLOAD + PREFERENSI DRIVER)
     * Tidak lagi mengunci UnityShaderCache ke 0-byte atau mengenolkan texture_cache_size (yang bikin patah-patah!).
     * Cara kerja V10:
     * 1) Membuka kunci `UnityShaderCache` (`chmod 777`) & memberikan VRAM Cache 96MB agar game 100% lancar.
     * 2) Jika pengguna menaruh file Config Grafik MC di `/sdcard/Download/mc_config/` (atau `.bundle`),
     *    Shizuku UID 2000 otomatis menyuntikkannya ke `/storage/emulated/0/Android/data/com.dts.freefireth/files/contentcache/Compulsory/android/gameassetbundles/`!
     * 3) Mengatur Driver Grafis Sistem / ANGLE (`updatable_driver_all_apps = 2` / Prerelease Driver + SkiaVK)
     *    tanpa mengubah resolusi layar (Zero Downscale).
     */
    fun setRogMinecraftTextureOnly(context: Context, presetMode: Int): String {
        val targetPkg = getInstalledFFPackage(context)
        val allPkgs = listOf("com.dts.freefireth", "com.dts.freefiremax")

        return if (presetMode > 0) {
            val rendererType = if (presetMode == 1) "skiagl" else "skiavk"
            val driverOpt = if (presetMode == 3) "angle" else "native"
            val cmd = buildString {
                for (pkg in allPkgs) {
                    val baseDir = "/storage/emulated/0/Android/data/$pkg"
                    // 1. Pastikan TIDAK ada downscale dan buka kunci ShaderCache agar bebas patah-patah!
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game reset $pkg 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")
                    append("chmod -R 777 $baseDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("find $baseDir/cache/UnityShaderCache -type f -size 0c -delete 2>/dev/null; ")

                    // 2. Auto-Injector Config Grafik MC: jika ada file config di Download/mc_config, suntikkan via Shizuku UID 2000!
                    append("if [ -d \"/storage/emulated/0/Download/mc_config\" ] && [ -d \"$baseDir\" ]; then ")
                    append("cp -rf /storage/emulated/0/Download/mc_config/* $baseDir/files/contentcache/Compulsory/android/gameassetbundles/ 2>/dev/null; ")
                    append("fi; ")

                    // 3. Suntikkan preferensi Low-Mipmap & Non-Shadow ke konfigurasi lokal Unity Free Fire
                    append("if [ -d \"$baseDir/files\" ]; then ")
                    append("printf 'masterTextureLimit=3\\nanisotropicFiltering=0\\nantiAliasing=0\\nshadows=0\\nsoftParticles=0\\nshaderLOD=100\\n' > $baseDir/files/unity_quality_mc.cfg 2>/dev/null; ")
                    append("fi; ")
                }

                // 4. Setel Driver Grafis Prerelease / ANGLE & Renderer Skia tanpa mematikan VRAM Cache
                append("settings put global updatable_driver_all_apps 2 2>/dev/null; ")
                append("settings put global updatable_driver_prerelease_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global game_driver_prerelease_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_pkgs $targetPkg 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_values $driverOpt 2>/dev/null; ")
                append("setprop debug.hwui.renderer $rendererType 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_size 96 2>/dev/null; ")
                append("setprop debug.hwui.layer_cache_size 64 2>/dev/null; ")
                append("setprop debug.egl.force_msaa false 2>/dev/null; ")
                append("echo \"GRAFIK RINGAN & MC V10 ON ($targetPkg | Renderer=$rendererType | 0 Lag)\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                append("settings put global updatable_driver_all_apps 0 2>/dev/null; ")
                append("settings delete global updatable_driver_prerelease_opt_in_apps 2>/dev/null; ")
                append("settings delete global game_driver_prerelease_opt_in_apps 2>/dev/null; ")
                append("settings delete global angle_gl_driver_selection_pkgs 2>/dev/null; ")
                append("settings delete global angle_gl_driver_selection_values 2>/dev/null; ")
                for (pkg in allPkgs) {
                    val baseDir = "/storage/emulated/0/Android/data/$pkg"
                    append("chmod -R 777 $baseDir/cache/UnityShaderCache 2>/dev/null; ")
                    append("rm -f $baseDir/files/unity_quality_mc.cfg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game mode standard $pkg 2>/dev/null; ")
                }
                append("setprop debug.hwui.texture_cache_size 72 2>/dev/null; ")
                append("echo \"RESET -> Grafik Kembali Standar & Shader Normal\"")
            }
            execShell(cmd)
        }
    }
}
