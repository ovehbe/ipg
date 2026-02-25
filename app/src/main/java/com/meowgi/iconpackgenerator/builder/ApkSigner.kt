package com.meowgi.iconpackgenerator.builder

import com.android.apksig.ApkSigner as AndroidApkSigner
import java.io.File

class ApkSigner(private val keyStoreManager: KeyStoreManager) {

    fun sign(unsignedApk: File, signedApk: File) {
        val signingKey = keyStoreManager.getSigningKey()

        val signerConfig = AndroidApkSigner.SignerConfig.Builder(
            "ipg-signer",
            signingKey.privateKey,
            listOf(signingKey.certificate)
        ).build()

        val signer = AndroidApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()

        signer.sign()
    }
}
