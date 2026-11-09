package com.music.zenith.playback.smart

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Dua model ONNX Automix: tidak dibundel di APK, diunduh dari repo setelah pengguna setuju. */
object AutomixModels {
    private const val BASE_URL =
        "https://github.com/rezapranata089-bit/Zenith/raw/main/app/src/main/assets/"

    private val names get() = listOf(BeatTracker.MODEL_ASSET, VocalTracker.MODEL_ASSET)

    /** Apakah kedua model sudah ada (hasil unduhan atau masih dibundel di build lama). */
    fun isReady(context: Context): Boolean = names.all { present(context, it) }

    private fun present(context: Context, name: String): Boolean {
        val file = File(context.filesDir, name)
        if (file.exists() && file.length() > 0L) return true
        return runCatching { context.assets.open(name).close() }.isSuccess
    }

    /**
     * Mengunduh model yang belum ada. Memblokir, jadi panggil dari thread latar.
     * [onProgress] 0..100 untuk semua model; [isCancelled] dicek terus selama mengunduh.
     */
    fun download(
        context: Context,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
    ): Boolean {
        val missing = names.filterNot { present(context, it) }
        if (missing.isEmpty()) return true
        missing.forEachIndexed { index, name ->
            val ok = fetch(context, name, isCancelled) { fraction ->
                onProgress((((index + fraction) / missing.size) * 100).toInt())
            }
            if (!ok) return false
        }
        onProgress(100)
        return true
    }

    /** Ditulis ke file sementara dan di-rename setelah lengkap, supaya tidak ada model terpotong. */
    private fun fetch(
        context: Context,
        name: String,
        isCancelled: () -> Boolean,
        onFraction: (Float) -> Unit,
    ): Boolean {
        val target = File(context.filesDir, name)
        val temp = File(context.filesDir, "$name.part")
        var connection: HttpURLConnection? = null
        try {
            connection = URL(BASE_URL + name).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != 200) return false
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (isCancelled()) return false
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onFraction((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            return temp.length() > 0L && temp.renameTo(target)
        } catch (e: Exception) {
            return false
        } finally {
            connection?.disconnect()
            if (temp.exists()) temp.delete()
        }
    }
}