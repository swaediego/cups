package com.cups.tasas

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Versión publicada en GitHub Releases. */
data class Update(val version: String, val apkUrl: String, val notes: String)

/**
 * Actualizaciones por GitHub Releases: se publica un release con tag "v1.2" y el APK adjunto;
 * la app compara ese tag con su versionName y, si es mayor, ofrece descargarlo e instalarlo.
 */
object Updater {
    private const val RELEASE_URL = "https://api.github.com/repos/swaediego/cups/releases/latest"
    private const val CHANNEL = "updates"
    private const val NOTIFIED = "update_notified"

    fun installedVersion(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    /** Último release con APK, o null si no hay ninguno. Lanza excepción si no hay conexión. */
    fun fetchLatest(): Update? {
        val conn = URL(RELEASE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode == 404) return null
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                return Update(
                    version = json.getString("tag_name").removePrefix("v"),
                    apkUrl = a.getString("browser_download_url"),
                    notes = json.optString("body", ""),
                )
            }
        }
        return null
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Descarga el APK a la caché de la app informando el avance (0..1). */
    fun download(ctx: Context, u: Update, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "cups-${u.version}.apk")
        val conn = URL(u.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(32 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        return file
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Lleva al usuario al ajuste "instalar apps desconocidas" de cups. */
    fun askInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Programa el chequeo en segundo plano (cada 6 h con conexión). */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Busca una versión nueva y, si la hay, avisa con una notificación (una sola vez por versión). */
    fun checkAndNotify(ctx: Context) {
        val u = runCatching { fetchLatest() }.getOrNull() ?: return
        if (isNewer(u.version, installedVersion(ctx))) notify(ctx, u)
    }

    /** Avisa una sola vez por versión. */
    fun notify(ctx: Context, u: Update) {
        val prefs = ctx.getSharedPreferences("rates", 0)
        if (prefs.getString(NOTIFIED, null) == u.version) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Actualización disponible")
            .setContentText("Actualiza a la versión ${u.version} para obtener mejoras.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Actualiza a la versión ${u.version} para obtener mejoras." +
                    (u.notes.trim().takeIf { it.isNotEmpty() }?.let { "\n\n$it" } ?: "") +
                    "\n\nAbre cups y toca el ícono de actualización.",
            ))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1, n)
        prefs.edit().putString(NOTIFIED, u.version).apply()
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Updater.checkAndNotify(applicationContext)
        return Result.success()
    }
}
