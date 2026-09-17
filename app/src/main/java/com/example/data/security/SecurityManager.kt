package com.example.data.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityManager(private val context: Context) {

    private val keyStoreAlias = "ExpenseTrackerMasterVaultKey"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val ivLength = 12

    private val receiptsDir: File
        get() {
            val dir = File(context.filesDir, "encrypted_receipts")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    init {
        ensureMasterKeyExists()
    }

    private fun ensureMasterKeyExists() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(keyStoreAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(
                keyStoreAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val entry = keyStore.getEntry(keyStoreAlias, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Master encryption key not found in AndroidKeyStore")
        return entry.secretKey
    }

    /**
     * Encrypts the raw image bytes (JPEG/PNG) and saves it to local encrypted storage.
     * Returns the unique filename of the saved encrypted file.
     */
    fun encryptAndSaveReceipt(imageBytes: ByteArray, extension: String = "jpg"): String {
        val fileName = "receipt_${UUID.randomUUID()}.$extension.enc"
        val targetFile = File(receiptsDir, fileName)

        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv // 12 bytes

        val cipherText = cipher.doFinal(imageBytes)

        FileOutputStream(targetFile).use { fos ->
            fos.write(iv) // Write IV first
            fos.write(cipherText) // Followed by ciphertext + auth tag
        }

        return fileName
    }

    /**
     * Helper to encrypt a Bitmap directly.
     */
    fun encryptAndSaveBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(format, 90, stream)
        val bytes = stream.toByteArray()
        val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
        return encryptAndSaveReceipt(bytes, extension)
    }

    /**
     * Reads and decrypts the encrypted receipt file from disk.
     * Returns raw decrypted image bytes.
     */
    fun decryptReceipt(fileName: String): ByteArray? {
        val targetFile = File(receiptsDir, fileName)
        if (!targetFile.exists() || targetFile.length() <= ivLength) {
            return null
        }

        return try {
            val fileBytes = FileInputStream(targetFile).use { it.readBytes() }
            val iv = fileBytes.copyOfRange(0, ivLength)
            val cipherText = fileBytes.copyOfRange(ivLength, fileBytes.size)

            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(gcmTagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            cipher.doFinal(cipherText)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypts and loads a receipt image as a Bitmap.
     */
    fun decryptReceiptAsBitmap(fileName: String): Bitmap? {
        val bytes = decryptReceipt(fileName) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Deletes a specific receipt file.
     */
    fun deleteReceipt(fileName: String): Boolean {
        val targetFile = File(receiptsDir, fileName)
        return if (targetFile.exists()) targetFile.delete() else true
    }

    /**
     * Deletes ALL encrypted receipt files (used in settings "Delete Data").
     */
    fun deleteAllReceipts() {
        val files = receiptsDir.listFiles() ?: return
        for (file in files) {
            file.delete()
        }
    }
}
