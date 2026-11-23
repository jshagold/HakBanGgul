package com.hak.hakbanggul.feature.settings.data

// 키를 기기에 넣기 전에 암호화하고, 꺼낼 때 복호화한다.
interface ApiKeyCipher {
    fun encrypt(plain: String): String

    // 복호화하지 못하면 예외를 던진다.
    fun decrypt(cipherText: String): String
}
