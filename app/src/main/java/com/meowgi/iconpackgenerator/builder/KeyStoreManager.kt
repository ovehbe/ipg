package com.meowgi.iconpackgenerator.builder

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

class KeyStoreManager(private val filesDir: File) {

    companion object {
        private const val KEYSTORE_FILENAME = "ipg_signing.bks"
        private const val KEY_ALIAS = "ipg_key"
        private const val STORE_PASSWORD = "ipg_store_pass"
        private const val KEY_PASSWORD = "ipg_key_pass"
        private const val KEYSTORE_TYPE = "BKS"
        private const val VALIDITY_YEARS = 25
    }

    private val keystoreFile = File(filesDir, KEYSTORE_FILENAME)

    data class SigningKey(
        val privateKey: PrivateKey,
        val certificate: X509Certificate
    )

    fun getSigningKey(): SigningKey {
        if (!keystoreFile.exists()) {
            generateKeyStore()
        }
        return loadSigningKey()
    }

    private fun loadSigningKey(): SigningKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        FileInputStream(keystoreFile).use { fis ->
            keyStore.load(fis, STORE_PASSWORD.toCharArray())
        }

        val privateKey = keyStore.getKey(KEY_ALIAS, KEY_PASSWORD.toCharArray()) as PrivateKey
        val certificate = keyStore.getCertificate(KEY_ALIAS) as X509Certificate

        return SigningKey(privateKey, certificate)
    }

    private fun generateKeyStore() {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(now + VALIDITY_YEARS.toLong() * 365 * 24 * 3600 * 1000)

        val cert = generateSelfSignedCert(keyPair, notBefore, notAfter)

        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        keyStore.load(null, STORE_PASSWORD.toCharArray())
        keyStore.setKeyEntry(
            KEY_ALIAS,
            keyPair.private,
            KEY_PASSWORD.toCharArray(),
            arrayOf(cert)
        )

        FileOutputStream(keystoreFile).use { fos ->
            keyStore.store(fos, STORE_PASSWORD.toCharArray())
        }
    }

    private fun generateSelfSignedCert(
        keyPair: java.security.KeyPair,
        notBefore: Date,
        notAfter: Date
    ): X509Certificate {
        // Try Android's bundled Bouncy Castle first
        try {
            return generateCertBouncyCastle(keyPair, notBefore, notAfter)
        } catch (_: Exception) {
            // Fallback to manual DER encoding
        }
        return generateCertManual(keyPair, notBefore, notAfter)
    }

    @Suppress("UNCHECKED_CAST")
    private fun generateCertBouncyCastle(
        keyPair: java.security.KeyPair,
        notBefore: Date,
        notAfter: Date
    ): X509Certificate {
        val certGenClass = Class.forName("org.bouncycastle.x509.X509V3CertificateGenerator")
        val certGen = certGenClass.getDeclaredConstructor().newInstance()
        val x509NameClass = Class.forName("org.bouncycastle.asn1.x509.X509Name")

        val issuerName = x509NameClass.getConstructor(String::class.java)
            .newInstance("CN=IPG, OU=IconPackGenerator, O=Meowgi")

        certGenClass.getMethod("setSerialNumber", BigInteger::class.java)
            .invoke(certGen, BigInteger.valueOf(System.currentTimeMillis()))
        certGenClass.getMethod("setIssuerDN", x509NameClass)
            .invoke(certGen, issuerName)
        certGenClass.getMethod("setSubjectDN", x509NameClass)
            .invoke(certGen, issuerName)
        certGenClass.getMethod("setNotBefore", Date::class.java)
            .invoke(certGen, notBefore)
        certGenClass.getMethod("setNotAfter", Date::class.java)
            .invoke(certGen, notAfter)
        certGenClass.getMethod("setPublicKey", java.security.PublicKey::class.java)
            .invoke(certGen, keyPair.public)
        certGenClass.getMethod("setSignatureAlgorithm", String::class.java)
            .invoke(certGen, "SHA256withRSA")

        return certGenClass.getMethod("generate", PrivateKey::class.java)
            .invoke(certGen, keyPair.private) as X509Certificate
    }

    private fun generateCertManual(
        keyPair: java.security.KeyPair,
        notBefore: Date,
        notAfter: Date
    ): X509Certificate {
        // SHA256withRSA OID: 1.2.840.113549.1.1.11
        val sigAlgOid = byteArrayOf(
            0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xf7.toByte(), 0x0d, 0x01, 0x01, 0x0b
        )

        val issuerBytes = javax.security.auth.x500.X500Principal(
            "CN=IPG, OU=IconPackGenerator, O=Meowgi"
        ).encoded

        val tbs = buildTbsCertificate(
            serialNumber = BigInteger.valueOf(System.currentTimeMillis()),
            issuer = issuerBytes,
            notBefore = notBefore,
            notAfter = notAfter,
            subject = issuerBytes,
            publicKey = keyPair.public.encoded,
            sigAlgOid = sigAlgOid
        )

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(tbs)
        val signature = sig.sign()

        val algId = derSequence(sigAlgOid, byteArrayOf(0x05, 0x00))
        val sigBits = derBitString(signature)
        val certBytes = derSequence(tbs, algId, sigBits)

        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(certBytes.inputStream()) as X509Certificate
    }

    private fun buildTbsCertificate(
        serialNumber: BigInteger,
        issuer: ByteArray,
        notBefore: Date,
        notAfter: Date,
        subject: ByteArray,
        publicKey: ByteArray,
        sigAlgOid: ByteArray
    ): ByteArray {
        val version = derExplicit(0, derInteger(BigInteger.valueOf(2)))
        val serial = derInteger(serialNumber)
        val algId = derSequence(sigAlgOid, byteArrayOf(0x05, 0x00))
        val validity = derSequence(derUtcTime(notBefore), derUtcTime(notAfter))
        return derSequence(version, serial, algId, issuer, validity, subject, publicKey)
    }

    // --- ASN.1 DER encoding helpers ---

    private fun derLength(length: Int): ByteArray = when {
        length < 0x80 -> byteArrayOf(length.toByte())
        length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
        length < 0x10000 -> byteArrayOf(
            0x82.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte()
        )
        else -> byteArrayOf(
            0x83.toByte(), (length shr 16).toByte(),
            ((length shr 8) and 0xFF).toByte(), (length and 0xFF).toByte()
        )
    }

    private fun derSequence(vararg parts: ByteArray): ByteArray {
        val content = parts.fold(byteArrayOf()) { acc, bytes -> acc + bytes }
        return byteArrayOf(0x30) + derLength(content.size) + content
    }

    private fun derInteger(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return byteArrayOf(0x02) + derLength(bytes.size) + bytes
    }

    private fun derBitString(data: ByteArray): ByteArray {
        val content = byteArrayOf(0x00) + data
        return byteArrayOf(0x03) + derLength(content.size) + content
    }

    private fun derExplicit(tag: Int, content: ByteArray): ByteArray {
        val tagByte = (0xA0 or tag).toByte()
        return byteArrayOf(tagByte) + derLength(content.size) + content
    }

    @Suppress("deprecation")
    private fun derUtcTime(date: Date): ByteArray {
        val formatted = String.format(
            "%02d%02d%02d%02d%02d%02dZ",
            date.year % 100, date.month + 1, date.date,
            date.hours, date.minutes, date.seconds
        )
        val bytes = formatted.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(0x17) + derLength(bytes.size) + bytes
    }
}
