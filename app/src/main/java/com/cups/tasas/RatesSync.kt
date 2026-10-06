package com.cups.tasas

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val LIVE_URL = "https://ve.dolarapi.com/v1/dolares"
/** Segunda fuente del BCV: publica la tasa del siguiente día antes que dolarapi (fecha valor + USD/EUR). */
private const val NEXT_URL = "https://datos.cromstudio.com.ve/tasa"
private val MONTHS = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

/**
 * Descarga y guardado de tasas, compartido por la pantalla y los trabajos en segundo plano.
 *
 * - El USDT se refresca cada hora en segundo plano (sin avisos).
 * - El BCV publica por la tarde la tasa del siguiente día hábil: de 1:30 pm a 7:00 pm (hora de Venezuela)
 *   se consulta cada 15 min hasta que aparece, y entonces se avisa con una notificación silenciosa.
 */
object RatesSync {
    private val CARACAS = ZoneId.of("America/Caracas")
    private val WINDOW_START = LocalTime.of(13, 30)
    private val WINDOW_END = LocalTime.of(19, 0)
    private const val CHANNEL = "rates"
    private const val NOTIFIED = "rate_notified"
    private const val LAST_SYNC = "last_sync"
    private const val NEXT_CHECK = "next_check"

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("rates", 0)

    fun fetch(c: Cur): List<Rate> {
        val conn = URL(c.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        val out = ArrayList<Rate>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.isNull("promedio")) continue
            out += Rate(o.getDouble("promedio"), LocalDate.parse(o.getString("fecha").take(10)))
        }
        return out.sortedBy { it.date }
    }

    fun fetchLive(): Rate? {
        val conn = URL(LIVE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getString("fuente") == "paralelo" && !o.isNull("promedio")) return Rate(o.getDouble("promedio"), LocalDate.now())
        }
        return null
    }

    /** Fecha valor y tasas USD/EUR que el BCV tiene publicadas ahora mismo (puede ser la del siguiente día). */
    fun fetchNextBcv(): Pair<LocalDate, Map<Cur, Double>>? {
        val conn = URL(NEXT_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val m = Regex("""(\d{1,2})\s+([A-Za-zñÑ]+)\s+(\d{4})""").find(o.getString("fecha_valor")) ?: return null
        val month = MONTHS.indexOf(m.groupValues[2].lowercase()) + 1
        if (month == 0) return null
        val date = LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
        val t = o.getJSONObject("tasas")
        val rates = mapOf(Cur.USD to t.optDouble("USD"), Cur.EUR to t.optDouble("EUR")).filterValues { it.isFinite() && it > 0 }
        return if (rates.isEmpty()) null else date to rates
    }

    /** Si aún no hay tasa futura guardada, la busca en la segunda fuente; descarta valores muy distintos a la última tasa. */
    private fun addNext(h: Map<Cur, List<Rate>>): Map<Cur, List<Rate>> {
        val last = lastOfficialDate(h)
        if (last != null && last.isAfter(LocalDate.now(CARACAS))) return h
        val n = runCatching { fetchNextBcv() }.getOrNull() ?: return h
        if (last != null && !n.first.isAfter(last)) return h
        return h.mapValues { (c, l) ->
            val v = n.second[c]
            if (v == null || l.isEmpty() || Math.abs(v / l.last().value - 1) > 0.2) l else l + Rate(v, n.first)
        }
    }

    fun load(prefs: SharedPreferences, c: Cur): List<Rate> {
        val raw = prefs.getString("hist_${c.name}", null) ?: return emptyList()
        return raw.split(';').mapNotNull { e ->
            val p = e.split('=')
            if (p.size != 2) null else try { Rate(p[1].toDouble(), LocalDate.parse(p[0])) } catch (x: Exception) { null }
        }.sortedBy { it.date }
    }

    fun loadLive(prefs: SharedPreferences): Rate? {
        val p = prefs.getString("usdt_live", null)?.split('=') ?: return null
        return try { Rate(p[1].toDouble(), LocalDate.parse(p[0])) } catch (x: Exception) { null }
    }

    /** Muestras (momento ms, valor) del USDT en vivo, para comparar con hace ~1 hora. */
    fun loadSamples(prefs: SharedPreferences): List<Pair<Long, Double>> =
        prefs.getString("usdt_samples", null)?.split(';')?.mapNotNull { e ->
            val p = e.split('=')
            val t = p.getOrNull(0)?.toLongOrNull()
            val v = p.getOrNull(1)?.toDoubleOrNull()
            if (t == null || v == null) null else t to v
        } ?: emptyList()

    private fun addSample(prefs: SharedPreferences, v: Double): String {
        val now = System.currentTimeMillis()
        val all = loadSamples(prefs).filter { now - it.first <= 8 * 3_600_000L }
        val out = if (all.isEmpty() || now - all.last().first >= 10 * 60_000L) all + (now to v) else all
        return out.joinToString(";") { "${it.first}=${it.second}" }
    }

    fun loadAll(prefs: SharedPreferences) = Cur.entries.associateWith { load(prefs, it) }

    /** Momento (ms) de la última descarga exitosa, de la pantalla o de segundo plano. */
    fun lastSync(prefs: SharedPreferences) = prefs.getLong(LAST_SYNC, 0L)

    /** Última fecha de tasa BCV conocida (puede ser futura: el BCV publica con un día hábil de adelanto). */
    fun lastOfficialDate(history: Map<Cur, List<Rate>>): LocalDate? =
        history.filterKeys { it.official }.values.flatMap { l -> l.map { it.date } }.maxOrNull()

    class Result(val history: Map<Cur, List<Rate>>, val live: Rate?)

    /** Une lo descargado con lo guardado: una tasa futura guardada no se pierde si la respuesta aún no la trae. */
    private fun merge(fetched: List<Rate>?, stored: List<Rate>): List<Rate> {
        val last = fetched?.lastOrNull()?.date ?: return stored
        return fetched + stored.filter { it.date.isAfter(last) }
    }

    /**
     * Pide las tasas y las guarda. Devuelve null si no hubo conexión con el BCV.
     * [officialOnly]: solo USD/EUR del BCV (comprobación de la tasa futura); no toca el USDT ni [lastSync].
     */
    fun pull(prefs: SharedPreferences, officialOnly: Boolean = false): Result? {
        prefs.edit().putLong(NEXT_CHECK, System.currentTimeMillis()).apply()
        val curs = Cur.entries.filter { it.official || !officialOnly }
        val fetched = curs.associateWith { c -> runCatching { fetch(c) }.getOrNull() }
        val live = if (officialOnly) null else runCatching { fetchLive() }.getOrNull()
        if (Cur.entries.filter { it.official }.all { fetched[it] == null }) return null
        val fresh = addNext(Cur.entries.associateWith { c ->
            if (c.official) merge(fetched[c], load(prefs, c)) else fetched[c] ?: load(prefs, c)
        })
        prefs.edit().apply {
            fresh.forEach { (c, list) -> putString("hist_${c.name}", list.joinToString(";") { "${it.date}=${it.value}" }) }
            live?.let { putString("usdt_live", "${it.date}=${it.value}"); putString("usdt_samples", addSample(prefs, it.value)) }
            if (!officialOnly) putLong(LAST_SYNC, System.currentTimeMillis())
        }.apply()
        return Result(fresh, live)
    }

    /**
     * ¿Toca preguntar si el BCV ya publicó la tasa futura? Solo si no hay una guardada y la última consulta
     * (de cualquier tipo) fue hace más de 5 min. No depende de [lastSync]: el resto de tasas pueden estar frescas.
     */
    fun nextCheckDue(prefs: SharedPreferences): Boolean =
        !isPublished(prefs) && System.currentTimeMillis() - prefs.getLong(NEXT_CHECK, 0L) > 5 * 60 * 1000

    /** Descarga desde un trabajo en segundo plano y avisa (en silencio) si el BCV publicó una tasa nueva. */
    fun syncInBackground(ctx: Context) {
        val prefs = prefs(ctx)
        val before = lastOfficialDate(loadAll(prefs))
        val r = pull(prefs) ?: return
        val after = lastOfficialDate(r.history) ?: return
        if (before != null && after.isAfter(before)) notifyNewRate(ctx, r.history, after)
    }

    private fun notifyNewRate(ctx: Context, history: Map<Cur, List<Rate>>, date: LocalDate) {
        val prefs = prefs(ctx)
        if (prefs.getString(NOTIFIED, null) == date.toString()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        fun value(c: Cur) = history[c]?.lastOrNull { it.date == date }?.value
        val money = { v: Double -> String.format(Locale.GERMANY, "%,.2f", v) }
        val parts = listOfNotNull(
            value(Cur.USD)?.let { "Dólar ${money(it)}" },
            value(Cur.EUR)?.let { "Euro ${money(it)}" },
        )
        if (parts.isEmpty()) return
        val day = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "VE"))).replace(".", "").replace(",", "")
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Importancia baja: aparece en la barra de notificaciones sin sonido ni vibración
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Tasas del BCV", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Nueva tasa BCV · $day")
            .setContentText(parts.joinToString(" · "))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(2, n)
        prefs.edit().putString(NOTIFIED, date.toString()).apply()
    }

    private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Programa el USDT cada hora y la ventana de la tarde del BCV. */
    fun schedule(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "rates-hourly", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RatesWorker>(1, TimeUnit.HOURS).setConstraints(net).build(),
        )
        scheduleBcv(ctx, published = isPublished(prefs(ctx)), replace = false)
    }

    /** ¿Ya está publicada la tasa del siguiente día hábil? (hay una fecha posterior a hoy) */
    fun isPublished(prefs: SharedPreferences): Boolean =
        lastOfficialDate(loadAll(prefs))?.isAfter(LocalDate.now(CARACAS)) == true

    /** Próxima consulta del BCV: hoy 1:30 pm, cada 15 min hasta 7:00 pm, o mañana 1:30 pm si ya salió. */
    fun scheduleBcv(ctx: Context, published: Boolean, replace: Boolean = true) {
        val now = ZonedDateTime.now(CARACAS)
        val next = when {
            !published && now.toLocalTime() < WINDOW_START -> now.with(WINDOW_START)
            !published && now.toLocalTime() < WINDOW_END -> now.plusMinutes(15)
            else -> now.plusDays(1).with(WINDOW_START)
        }
        val req = OneTimeWorkRequestBuilder<BcvWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(net).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "bcv-window", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req,
        )
    }
}

/** USDT (y cualquier tasa nueva del BCV) cada hora; de paso, aviso si hay versión nueva de la app. */
class RatesWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        RatesSync.syncInBackground(applicationContext)
        Updater.checkAndNotify(applicationContext)
        return Result.success()
    }
}

/** Ventana de la tarde: consulta el BCV hasta que publique la tasa nueva y se reprograma sola. */
class BcvWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = RatesSync.prefs(applicationContext)
        if (!RatesSync.isPublished(prefs)) RatesSync.syncInBackground(applicationContext)
        RatesSync.scheduleBcv(applicationContext, RatesSync.isPublished(prefs))
        return Result.success()
    }
}
