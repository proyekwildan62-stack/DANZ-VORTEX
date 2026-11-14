package com.danz.vortex

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object LicenseEngine {
    private const val MASTER_SECRET = "DANZVORTEX_PRIVATE_SIGN_2026_KEY"
    const val DEV_MASTER_PIN = "DANZDEV"
    const val RESELLER_PIN = "DANZRESELLER"

    fun getDeviceHwid(context: Context): String {
        val rawId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        val digest = MessageDigest.getInstance("SHA-256").digest(rawId.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(10).uppercase()
    }

    // Format Key: DVX-[TARGET_HWID]-[EXPIRY_EPOCH_SEC]-[SIGNATURE]
    fun generateKey(targetHwid: String, days: Int): String {
        val cleanHwid = targetHwid.trim().uppercase()
        val expiryEpoch = if (days >= 9999) {
            4102444800L // Tahun 2100 (Permanent)
        } else {
            (System.currentTimeMillis() / 1000L) + (days * 86400L)
        }
        val payload = "$cleanHwid:$expiryEpoch"
        val sig = hmacSha256(payload, MASTER_SECRET).take(8).uppercase()
        return "DVX-$cleanHwid-$expiryEpoch-$sig"
    }

    fun verifyAndActivateKey(context: Context, inputKey: String): Pair<Boolean, String> {
        val trimmed = inputKey.trim()
        if (trimmed.equals(DEV_MASTER_PIN, ignoreCase = false)) {
            saveLicenseState(context, trimmed, 4102444800L, "DEVELOPER")
            return Pair(true, "Developer Master Access (DANZDEV) Activated!")
        }
        if (trimmed.equals(RESELLER_PIN, ignoreCase = false)) {
            saveLicenseState(context, trimmed, 4102444800L, "RESELLER")
            return Pair(true, "Reseller Panel Access Activated!")
        }

        val parts = trimmed.split("-")
        if (parts.size != 4 || parts[0] != "DVX") {
            return Pair(false, "Format Key salah atau tidak dikenal!")
        }

        val keyHwid = parts[1]
        val expiryEpoch = parts[2].toLongOrNull() ?: return Pair(false, "Data waktu Key rusak!")
        val providedSig = parts[3]

        val myHwid = getDeviceHwid(context)
        if (keyHwid != myHwid) {
            return Pair(false, "Key ditolak! Key ini terikat ke HWID HP lain (1 Key 1 Device).")
        }

        val nowSec = System.currentTimeMillis() / 1000L
        if (nowSec > expiryEpoch) {
            return Pair(false, "Masa aktif Key sudah habis (Expired)!")
        }

        val expectedSig = hmacSha256("$keyHwid:$expiryEpoch", MASTER_SECRET).take(8).uppercase()
        if (providedSig != expectedSig) {
            return Pair(false, "Signature Key tidak valid / palsu!")
        }

        saveLicenseState(context, trimmed, expiryEpoch, "USER")
        return Pair(true, "Lisensi VIP Aktif!")
    }

    private fun saveLicenseState(context: Context, key: String, expiry: Long, role: String) {
        val prefs = context.getSharedPreferences("danz_auth", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("saved_key", key)
            .putLong("expiry", expiry)
            .putString("role", role)
            .apply()
    }

    fun getSavedRole(context: Context): String? {
        val prefs = context.getSharedPreferences("danz_auth", Context.MODE_PRIVATE)
        val expiry = prefs.getLong("expiry", 0L)
        val nowSec = System.currentTimeMillis() / 1000L
        if (nowSec > expiry) return null
        return prefs.getString("role", null)
    }

    fun logout(context: Context) {
        context.getSharedPreferences("danz_auth", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
