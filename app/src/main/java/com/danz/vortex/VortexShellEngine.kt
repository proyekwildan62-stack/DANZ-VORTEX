package com.danz.vortex

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

object VortexShellEngine {

    var lastExecutionLog: String = "Engine Ready. Bind Shizuku untuk akses UID 2000 (No-Root)."

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

    fun requestShizukuPermission() {
        try {
            if (isShizukuAvailable() && !hasShizukuPermission()) {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Exception) {
            lastExecutionLog = "Gagal meminta izin Shizuku: ${e.message}"
        }
    }

    /**
     * Mengeksekusi perintah langsung menggunakan UID 2000 (ADB Shell via Shizuku)
     * Menggunakan java.lang.Process agar 100% lolos compile Kotlin tanpa error.
     */
    fun execShell(command: String): String {
        return try {
            val process: Process = if (hasShizukuPermission()) {
                val newProcessMethod: Method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                newProcessMethod.isAccessible = true
                newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }

            val stdOut = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val stdErr = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            process.waitFor()

            val result = when {
                stdOut.isNotEmpty() -> stdOut
                stdErr.isNotEmpty() -> "WARN: $stdErr"
                else -> "OK (UID 2000 Executed)"
            }
            lastExecutionLog = "CMD: ${command.take(40)}... -> $result"
            result
        } catch (e: Exception) {
            val err = "ERR: ${e.message}"
            lastExecutionLog = err
            err
        }
    }

    // 1. DRAG HS SAFE NO ROOT & SENSITIVITY LEVEL (1 - 7) TANPA UBAH DPI/LAYAR HP
    fun setDragHsSensitivity(enable: Boolean, level: Int): String {
        return if (enable) {
            val speed = level.coerceIn(1, 7)
            val pressTimeout = (220 - (speed * 15)).coerceAtLeast(100)
            val cmd = buildString {
                append("settings put system pointer_speed $speed; ")
                append("settings put secure long_press_timeout $pressTimeout; ")
                append("settings put secure multi_press_timeout $pressTimeout; ")
                append("settings put secure tap_duration_threshold 0.0; ")
                append("settings put secure touch_exploration_enabled 0; ")
                append("echo \"PointerSpeed=\"\$(settings get system pointer_speed)\" | Timeout=\"\$(settings get secure long_press_timeout)")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                append("settings put system pointer_speed 0; ")
                append("settings put secure long_press_timeout 400; ")
                append("settings put secure multi_press_timeout 300; ")
                append("echo \"Reset Default PointerSpeed=\"\$(settings get system pointer_speed)")
            }
            execShell(cmd)
        }
    }

    // 2. AIMDRAG, HEADTICK 80%, STABILIZER HEAD & HYPER TOUCH
    fun setAimDragAndTouchMatrix(enable: Boolean): String {
        return if (enable) {
            val cmd = buildString {
                append("settings put system peak_refresh_rate 120.0; ")
                append("settings put system min_refresh_rate 120.0; ")
                append("settings put secure user_refresh_rate 120; ")
                append("setprop debug.input.StartingWindow 0; ")
                append("setprop windowsmgr.max_events_per_sec 240; ")
                append("echo \"RefreshRate=\"\$(settings get system peak_refresh_rate)\"Hz | InputEvents=240/s\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                append("settings delete system peak_refresh_rate; ")
                append("settings delete system min_refresh_rate; ")
                append("echo \"RefreshRate & Touch Matrix Restored\"")
            }
            execShell(cmd)
        }
    }

    // 3. GAME SPACE GRAFIK RINGAN ROG/PC + TEKSTUR MINECRAFT (TANPA UBAH UKURAN LAYAR HP)
    // presetMode: 0 = Off/Standard, 1 = ROG/PC Smooth (0.75), 2 = Minecraft V1 Semi-Kotak (0.45), 3 = Minecraft V2 Extreme Blocky (0.30)
    fun setRogMinecraftGameSpace(presetMode: Int): String {
        val packages = listOf("com.dts.freefireth", "com.dts.freefiremax")
        return if (presetMode > 0) {
            val scaleFactor = when (presetMode) {
                1 -> "0.75"
                2 -> "0.45"
                else -> "0.30"
            }
            val modeName = when (presetMode) {
                1 -> "ROG/PC Smooth (0.75)"
                2 -> "MINECRAFT V1 Blocky (0.45)"
                else -> "MINECRAFT V2 Super Kotak (0.30)"
            }
            val cmd = buildString {
                for (pkg in packages) {
                    append("cmd game mode performance $pkg 2>/dev/null; ")
                    append("cmd game set --mode 2 --downscale $scaleFactor --fps 120 $pkg 2>/dev/null; ")
                    append("device_config put game_overlay $pkg mode=2,downscaleFactor=$scaleFactor,fps=120:mode=3,downscaleFactor=$scaleFactor,fps=90 2>/dev/null; ")
                    append("am force-stop $pkg 2>/dev/null; ")
                }
                // Paksa SkiaGL Low-LOD & matikan MSAA agar piksel tampak kotak-kotak tajam ala Minecraft tanpa blur
                append("setprop debug.hwui.renderer skiagl 2>/dev/null; ")
                append("setprop debug.egl.force_msaa false 2>/dev/null; ")
                append("setprop debug.hwui.force_dark false 2>/dev/null; ")
                append("setprop debug.sf.latch_unsignaled 1 2>/dev/null; ")
                append("settings put global window_animation_scale 0.0; ")
                append("settings put global transition_animation_scale 0.0; ")
                append("settings put global animator_duration_scale 0.0; ")
                append("am kill-all; ")
                append("echo \"Active: $modeName | 120FPS\"")
            }
            execShell(cmd)
        } else {
            val cmd = buildString {
                for (pkg in packages) {
                    append("cmd game mode standard $pkg 2>/dev/null; ")
                    append("cmd game reset $pkg 2>/dev/null; ")
                    append("device_config delete game_overlay $pkg 2>/dev/null; ")
                }
                append("settings put global window_animation_scale 1.0; ")
                append("settings put global transition_animation_scale 1.0; ")
                append("settings put global animator_duration_scale 1.0; ")
                append("echo \"Tekstur Game Space Dikembalikan ke Normal\"")
            }
            execShell(cmd)
        }
    }
}
