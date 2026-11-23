package com.hak.hakbanggul.feature.settings.data

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

// Tink AEAD(AES-256-GCM)로 키를 암호화한다.
// Tink 키셋은 Android Keystore의 키로 감싸 앱 전용 SharedPreferences에 둔다.
@Singleton
class TinkApiKeyCipher @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ApiKeyCipher {

    // Keystore를 여는 일은 느리므로 처음 쓸 때 한 번만 한다.
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun encrypt(plain: String): String {
        val encrypted = aead.encrypt(plain.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
        return Base64.getEncoder().encodeToString(encrypted)
    }

    override fun decrypt(cipherText: String): String {
        val decrypted = aead.decrypt(Base64.getDecoder().decode(cipherText), ASSOCIATED_DATA)
        return String(decrypted, Charsets.UTF_8)
    }

    private companion object {
        const val KEYSET_NAME = "api_key_keyset"
        const val KEYSET_PREF_FILE = "api_key_keyset"
        const val MASTER_KEY_URI = "android-keystore://hakbanggul_api_key_master"
        val ASSOCIATED_DATA = "hakbanggul_api_key".toByteArray(Charsets.UTF_8)
    }
}
