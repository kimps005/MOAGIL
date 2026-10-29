package kr.moagil.vehicle.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the driver capability token encrypted with an app-private Android Keystore key. */
class DriverVault(context: Context) {
    private val preferences = context.getSharedPreferences("moagil-driver-vault", Context.MODE_PRIVATE)
    private val keyAlias = "moagil.driver.v1"

    @Synchronized
    fun save(credentials: DriverCredentials) {
        val payload = JSONObject().apply {
            put("apiBaseUrl", credentials.apiBaseUrl)
            put("joinBaseUrl", credentials.joinBaseUrl)
            put("sessionId", credentials.sessionId)
            put("driverToken", credentials.driverToken)
            put("inviteToken", credentials.inviteToken)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(payload)
        preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun restore(): DriverCredentials? {
        val ivText = preferences.getString("iv", null) ?: return null
        val payloadText = preferences.getString("payload", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            val payload = String(cipher.doFinal(Base64.decode(payloadText, Base64.NO_WRAP)), Charsets.UTF_8)
            val json = JSONObject(payload)
            DriverCredentials(
                apiBaseUrl = json.getString("apiBaseUrl"),
                joinBaseUrl = json.getString("joinBaseUrl"),
                sessionId = json.getString("sessionId"),
                driverToken = json.getString("driverToken"),
                inviteToken = json.getString("inviteToken"),
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() { preferences.edit().clear().apply() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }
}
