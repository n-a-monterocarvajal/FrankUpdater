/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.github.n_a_monterocarvajal.frankupdater.play

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class PlayCredentials(val email: String, val aasToken: String)

internal class PlayCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("play_credentials", Context.MODE_PRIVATE)

    fun save(credentials: PlayCredentials) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val value = cipher.doFinal("${credentials.email}\n${credentials.aasToken}".toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("value", Base64.encodeToString(value, Base64.NO_WRAP))
            .apply()
    }

    fun load(): PlayCredentials? = runCatching {
        val iv = Base64.decode(preferences.getString("iv", null), Base64.DEFAULT)
        val value = Base64.decode(preferences.getString("value", null), Base64.DEFAULT)
        val plain = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        }.doFinal(value).toString(StandardCharsets.UTF_8).split('\n', limit = 2)
        require(plain.size == 2 && plain[0].isNotBlank() && plain[1].isNotBlank())
        PlayCredentials(plain[0], plain[1])
    }.getOrNull()

    fun clear() { preferences.edit().clear().apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE).apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALIAS = "frankupdater.play.credentials"
        const val TAG_BITS = 128
    }
}
