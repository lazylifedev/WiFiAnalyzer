package com.lazyapps.wifianalyzer.kintone

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedToken(val ciphertext: ByteArray, val iv: ByteArray)

interface TokenCipher {
    fun encrypt(workspaceUuid: String, token: CharArray): EncryptedToken
    fun decrypt(workspaceUuid: String, encrypted: EncryptedToken): CharArray
}

class AndroidKeystoreTokenCipher : TokenCipher {
    override fun encrypt(workspaceUuid: String, token: CharArray): EncryptedToken {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(workspaceUuid.toByteArray(Charsets.UTF_8))
            return EncryptedToken(cipher.doFinal(String(token).toByteArray(Charsets.UTF_8)), cipher.iv)
        } catch (error: Exception) { throw KintoneException(KintoneErrorCode.KINTONE_SECURE_STORAGE_FAILED, error) }
        finally { token.fill('\u0000') }
    }

    override fun decrypt(workspaceUuid: String, encrypted: EncryptedToken): CharArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.iv))
        cipher.updateAAD(workspaceUuid.toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(encrypted.ciphertext), Charsets.UTF_8).toCharArray()
    } catch (error: Exception) { throw KintoneException(KintoneErrorCode.KINTONE_SECURE_STORAGE_FAILED, error) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    private companion object { const val ALIAS = "wifi_analyzer_kintone_token_v1"; const val TRANSFORMATION = "AES/GCM/NoPadding" }
}
