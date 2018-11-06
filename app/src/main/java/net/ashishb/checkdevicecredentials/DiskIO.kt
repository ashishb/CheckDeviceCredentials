package net.ashishb.checkdevicecredentials

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

private val sharedPreferenceName = "key_info"
private val keyCreateSharedPreferenceKey = "key_created"

fun hasCreatedKey(context: Context): Boolean {
    return context.getSharedPreferences(sharedPreferenceName, Context.MODE_PRIVATE)
        .getBoolean(keyCreateSharedPreferenceKey, false)
}

fun setHasCreatedKey(context: Context, value: Boolean): Boolean {
    return context.getSharedPreferences(sharedPreferenceName, Context.MODE_PRIVATE)
        .edit().putBoolean(keyCreateSharedPreferenceKey, value).commit()
}

fun saveData(context: Context, encryptedDataFile: String, data: ByteArray?): Boolean {
    if (data == null) {
        showMessage(context, "data is null")
        return false
    }
    try {
        val file = File(context.filesDir, encryptedDataFile)
        val fos = FileOutputStream(file)
        fos.write(data)
        fos.close()
        showMessage(context, "Saved data to " + file.absolutePath)
        return true
    } catch (e: IOException) {
        showMessage(context, e.toString())
        return false
    }
}

fun readData(context: Context, fileName: String): ByteArray {
    val file = File(context.filesDir, fileName)
    val fis = FileInputStream(file)
    fis.use { x -> return x.readBytes() }
}