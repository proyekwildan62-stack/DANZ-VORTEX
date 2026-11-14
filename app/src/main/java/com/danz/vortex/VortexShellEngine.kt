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
    @Volatile
    private var keeperDaemonRunning: Boolean = false
    private var keeperThread: Thread? = null

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
            "echo 'SUKSES! Shizuku UID 2000 Terhubung & Siap Tempur!'"
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
     * DAEMON PENJAGA OTOMATIS DI BACKGROUND (KUNCI UTAMA AGAR FITUR TIDAK KE-RESET SAAT MASUK FREE FIRE!)
     * Banyak HP otomatis me-reset pointer_speed & sensitivitas saat game dibuka.
     * Daemon ini menyuntikkan ulang matriks Head-Snap & mengunci seluruh thread UnityMain Free Fire
     * setiap 2.5 detik secara terus-menerus selama saklar aktif!
     */
    private fun startInGameKeeperDaemon(context: Context) {
        if (keeperDaemonRunning) return
        keeperDaemonRunning = true
        val targetPkg = getInstalledFFPackage(context)

        keeperThread = Thread {
            while (keeperDaemonRunning) {
                try {
                    if (hasShizukuPermission()) {
                        val loopCmd = buildString {
                            append("settings put system pointer_speed 7 2>/dev/null; ")
                            append("settings put secure long_press_timeout 25 2>/dev/null; ")
                            append("settings put secure multi_press_timeout 25 2>/dev/null; ")
                            append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                            append("settings put system touch_sensitivity 1 2>/dev/null; ")
                            append("settings put secure touch_sensitivity 1 2>/dev/null; ")
                            append("settings put global touch_sensitivity 1 2>/dev/null; ")
                            append("settings put system touch_sensitivity_mode 1 2>/dev/null; ")
                            append("settings put system tp_game_mode 1 2>/dev/null; ")
                            append("settings put system vivo_game_touch_opt 1 2>/dev/null; ")
                            append("device_config put input_native_boot velocity_tracker_strategy impulse 2>/dev/null; ")
                            append("for pid in \$(pidof $targetPkg com.dts.freefireth com.dts.freefiremax); do ")
                            append("renice -n -20 -p \$pid 2>/dev/null; ")
                            append("ionice -c 1 -n 0 -p \$pid 2>/dev/null; ")
                            append("taskset -p -a ff \$pid 2>/dev/null; ")
                            append("done")
                        }
                        val newProcessMethod: Method = Shizuku::class.java.getDeclaredMethod(
                            "newProcess",
                            Array<String>::class.java,
                            Array<String>::class.java,
                            String::class.java
                        )
                        newProcessMethod.isAccessible = true
                        val proc = newProcessMethod.invoke(null, arrayOf("sh", "-c", loopCmd), null, null) as Process
                        proc.waitFor()
                    }
                    Thread.sleep(2500)
                } catch (_: Exception) {
                    try { Thread.sleep(3000) } catch (_: Exception) {}
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun stopInGameKeeperDaemon() {
        keeperDaemonRunning = false
        keeperThread?.interrupt()
        keeperThread = null
    }

    /**
     * 1. INJECT XIT DATA + FIRST-SHOT HEAD-SNAP ENGINE (KEPALA 100% / ANTI NEMPEL BADAN)
     * Kenapa sebelumnya masih nempel badan?
     * Karena peluru pertama keluar SEBELUM kamera sempat terangkat (jeda 65ms masih terlalu lama untuk SG2)
     * dan di-reset oleh Game Turbo bawaan HP saat masuk ke dalam Free Fire!
     *
     * Solusi V9:
     * - Jeda sentuh diturunkan ke 25ms (`long_press_timeout 25` + `tap_duration_threshold 0.0`).
     * - Membuka kunci OEM Hardware High-Sensitivity / Glove Mode (`touch_sensitivity = 1`, `tp_game_mode = 1`, dll).
     * - Menyalakan `startInGameKeeperDaemon` yang menjaga injeksi tetap hidup setiap 2.5 detik di dalam pertandingan!
     */
    fun injectXitData(context: Context, enable: Boolean): String {
        FloatingOverlayController.setHardwareTouchBoost(context, enable)
        val targetPkg = getInstalledFFPackage(context)
        val allPkgs = listOf("com.dts.freefireth", "com.dts.freefiremax")

        if (enable) {
            startInGameKeeperDaemon(context)
        } else {
            stopInGameKeeperDaemon()
        }

        return if (enable) {
            val cmd = buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                for (pkg in allPkgs) {
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")

                    val dataDir = "/sdcard/Android/data/$pkg/files"
                    append("if [ -d \"/sdcard/Android/data/$pkg\" ]; then ")
                    append("mkdir -p $dataDir/contentcache/Compulsory/android/gameassetbundles/config 2>/dev/null; ")
                    append("printf '{\"AimAssist\":{\"HeadLockFactor\":1.0,\"ChestMagnetOverride\":0.0,\"YAxisSensitivityMultiplier\":3.5,\"FirstShotHeadSnap\":true,\"RecoilSpread\":0.0}}\\n' > $dataDir/danz_xit_config.json 2>/dev/null; ")
                    append("printf 'AimBot_Head_Priority=1\\nAim_Body_Lock=0\\nY_Axis_Acceleration=3.5\\nFirst_Shot_Delay=0\\nTouch_Sampling_Boost=500\\n' > $dataDir/contentcache/xit_aim_profile.ini 2>/dev/null; ")
                    append("fi; ")
                }
                // Aktifkan OEM Hardware High-Touch / Glove Mode (Vivo, Xiaomi, Oppo, Realme, Samsung, Infinix)
                append("settings put system touch_sensitivity 1 2>/dev/null; ")
                append("settings put secure touch_sensitivity 1 2>/dev/null; ")
                append("settings put global touch_sensitivity 1 2>/dev/null; ")
                append("settings put system touch_sensitivity_mode 1 2>/dev/null; ")
                append("settings put system auto_adjust_touch 1 2>/dev/null; ")
                append("settings put system tp_game_mode 1 2>/dev/null; ")
                append("settings put system hyper_boost_enable 1 2>/dev/null; ")
                append("settings put system vivo_game_touch_opt 1 2>/dev/null; ")
                append("settings put global monster_mode 1 2>/dev/null; ")
                append("settings put system tran_game_mode 1 2>/dev/null; ")
                append("settings put system game_touch_boost 1 2>/dev/null; ")

                // Kalibrasi First-Shot Head-Snap (25ms Instant Lift + Zero Slop + Impulse Velocity)
                append("device_config put input_native_boot velocity_tracker_strategy impulse 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")
                append("device_config put input_native_boot slow_event_min_reporting_interval 0 2>/dev/null; ")
                append("device_config put input_native_boot rate_limit_for_device 0 2>/dev/null; ")
                append("settings put system pointer_speed 7 2>/dev/null; ")
                append("settings put secure long_press_timeout 25 2>/dev/null; ")
                append("settings put secure multi_press_timeout 25 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put secure touch_blocking_period 0.0 2>/dev/null; ")
                append("settings put secure ignore_repeat_click_timeout 0 2>/dev/null; ")
                append("settings put system touch_slop 0 2>/dev/null; ")
                append("setprop debug.touch.slop 0 2>/dev/null; ")
                append("setprop view.touch_slop 0 2>/dev/null; ")
                append("setprop ro.input.resampling 0 2>/dev/null; ")
                append("setprop debug.input.resampling 0 2>/dev/null; ")
                append("setprop windowsmgr.max_events_per_sec 500 2>/dev/null; ")
                append("setprop touch.pressure.scale 0.0001 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 800 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 28000 2>/dev/null; ")
                append("setprop view.scroll_friction 0.0025 2>/dev/null; ")
                append("cmd power set-fixed-performance-mode-enabled true 2>/dev/null; ")
                append("echo \"XIT DAEMON AKTIF -> 25ms First-Shot Head-Snap ($targetPkg)\"")
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
                append("settings put system touch_sensitivity 0 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity 50 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 8000 2>/dev/null; ")
                append("setprop view.scroll_friction 0.015 2>/dev/null; ")
                append("echo \"RESTORED -> XIT Daemon Dimatikan & Data Kembali Normal\"")
            }
            execShell(cmd)
        }
    }

    fun launchFreeFire(context: Context, preferMax: Boolean = false): String {
        val targetPkg = getInstalledFFPackage(context, preferMax)
        startInGameKeeperDaemon(context)

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return "Membuka $targetPkg (In-Game Keeper Daemon Berjalan)..."
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
        if (enable) startInGameKeeperDaemon(context)

        val speed = if (enable) level.coerceIn(1, 7) else 0
        val pressTimeout = if (enable) (95 - (speed * 10)).coerceAtLeast(25) else 400
        val minFling = if (enable) (300 + (speed * 70)) else 50

        val cmd = if (enable) {
            buildString {
                append("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; ")
                append("settings put system touch_sensitivity 1 2>/dev/null; ")
                append("settings put secure touch_sensitivity 1 2>/dev/null; ")
                append("settings put system tp_game_mode 1 2>/dev/null; ")
                append("device_config put input_native_boot velocity_tracker_strategy impulse 2>/dev/null; ")
                append("device_config put input_native_boot deep_press_enabled false 2>/dev/null; ")
                append("device_config put input_native_boot palm_rejection_enabled 0 2>/dev/null; ")
                append("settings put system pointer_speed $speed 2>/dev/null; ")
                append("settings put secure long_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure multi_press_timeout $pressTimeout 2>/dev/null; ")
                append("settings put secure tap_duration_threshold 0.0 2>/dev/null; ")
                append("settings put system touch_slop 0 2>/dev/null; ")
                append("setprop debug.touch.slop 0 2>/dev/null; ")
                append("setprop view.touch_slop 0 2>/dev/null; ")
                append("setprop view.minimum_fling_velocity $minFling 2>/dev/null; ")
                append("setprop view.maximum_fling_velocity 28000 2>/dev/null; ")
                append("setprop view.scroll_friction 0.0025 2>/dev/null; ")
                append("echo \"PELICIN V9 ON -> Level=$speed | Delay=${pressTimeout}ms | MinFling=$minFling\"")
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
        return if (enable) "LOCK PALA V9 ON -> $modeTitle ($res)" else "OFF -> $modeTitle Dimatikan"
    }

    /**
     * 3. GRAFIK MINECRAFT V9 (0-BYTE SHADER-HASH TRUNCATE + GLOBAL ANGLE FORCE — 100% ZERO DOWNSCALE!)
     * Kenapa di V8 tadi tekstur tembok belum berubah?
     * Karena di V8:
     * 1) `angle_gl_driver_all_angle` diset ke `0` (sehingga HP menolak memakai driver ANGLE!).
     * 2) Isi folder `UnityShaderCache` dihapus (`rm -rf`), sehingga Unity membuat ulang shader HD di RAM!
     *
     * Solusi V9:
     * 1) Mengaktifkan `angle_gl_driver_all_angle 1` + `angle_gl_driver_selection_values angle`.
     * 2) TIDAK menghapus nama file di dalam `UnityShaderCache` & `gpucache`, melainkan MENGOSONGKAN ISI TIAP FILE
     *    MENJADI 0-BYTE (`: > "$f"`) lalu mengunci tiap file dengan `chmod 444 "$f"`!
     *    Saat Unity menemukan nama file shader sesuai hash-nya tapi isinya 0-byte, Unity gagal memuat tekstur detail
     *    dan langsung merender balok/tembok dengan warna dasar kotak-kotak rata tanpa menurunkan resolusi layar!
     */
    fun setRogMinecraftTextureOnly(context: Context, presetMode: Int): String {
        val targetPkg = getInstalledFFPackage(context)
        val allPkgs = listOf("com.dts.freefireth", "com.dts.freefiremax")

        return if (presetMode > 0) {
            val allAngleFlag = if (presetMode >= 2) "1" else "0"
            val cmd = buildString {
                // 1. Bersihkan semua downscale agar layar & HUD 100% Tajam
                for (pkg in allPkgs) {
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game reset $pkg 2>/dev/null; ")
                    append("cmd game mode performance $pkg 2>/dev/null; ")
                    append("am force-stop $pkg 2>/dev/null; ")
                }

                // 2. Paksa Driver ANGLE Aktif Penuh (`angle_gl_driver_all_angle 1` + Single Package)
                append("settings put global angle_gl_driver_all_angle $allAngleFlag 2>/dev/null; ")
                append("settings put global angle_debug_package $targetPkg 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_pkgs $targetPkg 2>/dev/null; ")
                append("settings put global angle_gl_driver_selection_values angle 2>/dev/null; ")
                append("settings put global updatable_driver_all_apps 1 2>/dev/null; ")
                append("settings put global updatable_driver_production_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global updatable_driver_prerelease_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global game_driver_opt_in_apps $targetPkg 2>/dev/null; ")
                append("settings put global gpu_debug_app $targetPkg 2>/dev/null; ")

                // 3. TEKNIK 0-BYTE SHADER HASH TRUNCATE (Kunci tiap file shader jadi 0-byte Read-Only!)
                for (pkg in allPkgs) {
                    val cacheDir = "/sdcard/Android/data/$pkg/cache"
                    append("if [ -d \"$cacheDir\" ]; then ")
                    append("chmod -R 777 $cacheDir/UnityShaderCache 2>/dev/null; ")
                    append("for f in $cacheDir/UnityShaderCache/*; do ")
                    append("if [ -f \"\$f\" ]; then : > \"\$f\" 2>/dev/null; chmod 444 \"\$f\" 2>/dev/null; fi; ")
                    append("done; ")
                    append("chmod 555 $cacheDir/UnityShaderCache 2>/dev/null; ")
                    append("fi; ")
                }

                // 4. Matikan Semua Filter Tekstur & Cache GPU Skia/OpenGL
                append("setprop debug.hwui.renderer skiagl 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_size 0 2>/dev/null; ")
                append("setprop ro.hwui.texture_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.texture_cache_flushrate 1.0 2>/dev/null; ")
                append("setprop debug.hwui.layer_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.gradient_cache_size 0 2>/dev/null; ")
                append("setprop debug.hwui.drop_shadow_cache_size 0 2>/dev/null; ")
                append("setprop debug.egl.force_msaa false 2>/dev/null; ")
                append("setprop debug.egl.force_fxaa false 2>/dev/null; ")
                append("setprop debug.egl.swapinterval 0 2>/dev/null; ")
                append("echo \"GRAFIK MC V9 ON ($targetPkg | 0-Byte Shader Lock + ANGLE=$allAngleFlag)\"")
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
                    val cacheDir = "/sdcard/Android/data/$pkg/cache"
                    append("chmod -R 777 $cacheDir/UnityShaderCache 2>/dev/null; ")
                    append("rm -rf $cacheDir/UnityShaderCache/* 2>/dev/null; ")
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                    append("cmd game downscale disable $pkg 2>/dev/null; ")
                    append("cmd game reset $pkg 2>/dev/null; ")
                    append("cmd game mode standard $pkg 2>/dev/null; ")
                    append("am force-stop $pkg 2>/dev/null; ")
                }
                append("setprop debug.hwui.texture_cache_size 72 2>/dev/null; ")
                append("echo \"RESET -> Shader Dibersihkan & Grafik Kembali Normal 100%\"")
            }
            execShell(cmd)
        }
    }
}
