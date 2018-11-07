package net.ashishb.checkdevicecredentials

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec


class MainActivity : AppCompatActivity() {

    private val requestCodeDecrypt = 1
    private val requestCodeEncrypt = 2
    private val keyName = "ourKeyToEncryptAndDecryptData"
    private val plainTextMessage = "You are seeing the decrypted message"
    private val encryptedDataFilename = "encryptedData.txt"
    private val cipherIvFileName = "ivData.txt"
    private val keyguardManager : KeyguardManager by lazy {
        getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        refreshButtonLabelAndAction()
    }

    private fun refreshButtonLabelAndAction() {
        if (!hasCreatedKey(this)) {
            findViewById<Button>(R.id.decrypt_button).text = "Click to generate key and save encrypted data"
            findViewById<Button>(R.id.decrypt_button).setOnClickListener { performEncryption() }
        } else {
            findViewById<Button>(R.id.decrypt_button).text = "Click to decrypt"
            findViewById<Button>(R.id.decrypt_button).setOnClickListener { performDecryption() }
        }
    }

    @RequiresApi(23)
    private fun performEncryption() {
        if (!keyguardManager.isDeviceSecure) {
            // Even biometrics count
            showMessage("Device is not secure with a PIN/Pattern/Password/Biometrics")
            return
        }
//            showMessage("Device is secure with a PIN/Pattern/Password/Biometrics")
//            showMessage("First time, creating the key")
        createKey(keyName)
        setHasCreatedKey(this, true)
        val encryptedData = encrypt(keyName, plainTextMessage)
        saveData(this, encryptedDataFilename, encryptedData)
        refreshButtonLabelAndAction()
    }

    @RequiresApi(23)
    private fun performDecryption() {
        if (!keyguardManager.isDeviceSecure) {
            // Even biometrics counts
            showMessage("First secure device with a " +
                    "PIN/Pattern/Password/Biometrics by going to" +
                    " System settings -> Security")
        } else {
//            showMessage("Device is secure with a PIN/Pattern/Password/Biometrics")
            showMessage("Key exists, decrypting data")
            val decryptedData = decrypt(keyName, readData(this, encryptedDataFilename))
            findViewById<TextView>(R.id.text_view).text = decryptedData
        }
    }

    @RequiresApi(23)
    private fun encrypt(keyName: String, plainTextMessage: String): ByteArray? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKey = keyStore.getKey(keyName, null) as SecretKey
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        // Try encrypting something, it will only work if the user authenticated within
        // the last 10 seconds.
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encryptedData = cipher.doFinal(plainTextMessage.toByteArray(Charset.defaultCharset()))
            saveData(this, cipherIvFileName, cipher.iv)
            return encryptedData
        } catch (e: UserNotAuthenticatedException) {
            showMessage("You did not authenticate recently")
            authenticateUser(requestCodeEncrypt)
            return null
        }
    }

    @RequiresApi(23)
    private fun decrypt(keyName: String, encryptedData: ByteArray): String? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKey = keyStore.getKey(keyName, null) as SecretKey
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        try {
            val ivParams = IvParameterSpec(readData(this, cipherIvFileName))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams)
            return String(cipher.doFinal(encryptedData))
        } catch (e: UserNotAuthenticatedException){
            showMessage("You did not authenticate recently")
            authenticateUser(requestCodeDecrypt)
            return null
        }

    }

    @RequiresApi(21)
    private fun authenticateUser(requestCode: Int) {
        val intent = keyguardManager.createConfirmDeviceCredentialIntent(null, null)
        if (intent != null) {
            startActivityForResult(intent, requestCode)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == requestCodeEncrypt) {
            // Challenge completed, proceed with using cipher
            if (resultCode == Activity.RESULT_OK) {
                showMessage("You authenticated successfully")
                val encryptedData = encrypt(keyName, plainTextMessage)
                saveData(this, encryptedDataFilename, encryptedData)
                refreshButtonLabelAndAction()
            } else {
                // The user canceled or didn’t complete the lock screen
                // operation. Go to error/cancellation flow.
                showMessage("You canceled authentication")
            }
        } else if (requestCode == requestCodeDecrypt) {
            // Challenge completed, proceed with using cipher
            if (resultCode == Activity.RESULT_OK) {
                showMessage("You authenticated successfully")
                val decryptedData = decrypt(keyName, readData(this, encryptedDataFilename))
                findViewById<TextView>(R.id.text_view).text = decryptedData
            } else {
                // The user canceled or didn’t complete the lock screen
                // operation. Go to error/cancellation flow.
                showMessage("You canceled authentication")
            }
        }
    }

    @RequiresApi(23)
    private fun createKey(keyName: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")

        val keyPurpose = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(keyName, keyPurpose)
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setUserAuthenticationRequired(true)
            // Require that the user has unlocked in the last 10 seconds
            .setUserAuthenticationValidityDurationSeconds(10)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        val key = keyGenerator.generateKey()
        showMessage("Generated a new symmetric key")
        return key
    }

    private fun showMessage(msg: String) {
        showMessage(this, msg)
    }

}

fun showMessage(context: Context, msg: String) {
    Log.d("MainActivity", msg)
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
