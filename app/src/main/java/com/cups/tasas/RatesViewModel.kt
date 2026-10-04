package com.cups.tasas

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

enum class Cur(val label: String, val symbol: String, val url: String, val official: Boolean = true) {
    USD("Dólar", "$", "https://ve.dolarapi.com/v1/historicos/dolares/oficial"),
    EUR("Euro", "€", "https://ve.dolarapi.com/v1/historicos/euros/oficial"),
    /** Promedio USDT (mercado paralelo / P2P); se actualiza durante el día. */
    USDT("USDT", "₮", "https://ve.dolarapi.com/v1/historicos/dolares/paralelo", official = false),
}

private const val LIVE_URL = "https://ve.dolarapi.com/v1/dolares"

enum class Mode { Convert, Calc }

/** Tasa publicada por el BCV con su fecha valor. */
data class Rate(val value: Double, val date: LocalDate)

/** Variación de una tasa respecto a la publicación anterior. */
data class Change(val diff: Double, val pct: Double, val since: LocalDate)

/** Evalúa una expresión con + − × ÷ (con precedencia). Ignora un operador final. */
fun evalExpr(src: String): Double? {
    val s = src.trimEnd { it in "+−×÷" }
    if (s.isEmpty()) return null
    val nums = ArrayList<Double>()
    val ops = ArrayList<Char>()
    var i = 0
    while (i < s.length) {
        val neg = s[i] == '−'
        if (neg) i++
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == ',')) i++
        val n = s.substring(st, i).replace(',', '.').toDoubleOrNull() ?: return null
        nums += if (neg) -n else n
        if (i < s.length) ops += s[i++]
    }
    var k = 0
    while (k < ops.size) {
        if (ops[k] == '×' || ops[k] == '÷') {
            val v = if (ops[k] == '×') nums[k] * nums[k + 1] else nums[k] / nums[k + 1]
            nums[k] = v
            nums.removeAt(k + 1)
            ops.removeAt(k)
        } else k++
    }
    var acc = nums[0]
    for (j in ops.indices) acc = if (ops[j] == '+') acc + nums[j + 1] else acc - nums[j + 1]
    return acc.takeIf { it.isFinite() }
}

private enum class Field { Foreign, Bs }

class RatesViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("rates", 0)

    /** Historial completo por moneda, ordenado por fecha ascendente. */
    var history by mutableStateOf(Cur.entries.associateWith { load(it) })
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var currency by mutableStateOf(Cur.USD)
        private set
    var foreignText by mutableStateOf("")
        private set
    var bsText by mutableStateOf("")
        private set
    var mode by mutableStateOf(Mode.Convert)
        private set
    var expr by mutableStateOf("")
        private set
    /** true: la calculadora trabaja en bolívares y muestra la divisa; false: al revés. */
    var calcInBs by mutableStateOf(true)
        private set

    private var justEvaluated = false
    private var usdtLive by mutableStateOf(loadLive())

    /** Fecha de tasa elegida por el usuario; null = seguir la tasa vigente de hoy. */
    private var pinned by mutableStateOf<LocalDate?>(null)
    private var lastEdited = Field.Foreign

    /** Versión más nueva publicada en GitHub (null = al día o sin dato). */
    var update by mutableStateOf<Update?>(null)
        private set
    /** Avance de la descarga (0..1); null = no se está descargando. */
    var updateProgress by mutableStateOf<Float?>(null)
        private set
    var updateMsg by mutableStateOf<String?>(null)
        private set

    init { refresh() }

    fun checkUpdate() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val u = withContext(Dispatchers.IO) { runCatching { Updater.fetchLatest() }.getOrNull() }
            update = u?.takeIf { Updater.isNewer(it.version, Updater.installedVersion(ctx)) }
        }
    }

    fun clearUpdateMsg() { updateMsg = null }

    /** Tema elegido por el usuario ("dark" | "light"); sin elección sigue al sistema. */
    fun initialDark(system: Boolean): Boolean = when (prefs.getString("theme", null)) {
        "dark" -> true
        "light" -> false
        else -> system
    }

    fun toggleTheme() {
        val next = !AppTheme.dark
        AppTheme.dark = next
        prefs.edit().putString("theme", if (next) "dark" else "light").apply()
    }

    /** Descarga el APK y abre el instalador del sistema; el usuario solo confirma. */
    fun startUpdate() {
        val u = update ?: return
        if (updateProgress != null) return
        val ctx = getApplication<Application>()
        if (!Updater.canInstall(ctx)) {
            updateMsg = "Permite instalar desde cups en la pantalla que se abre y vuelve a tocar Actualizar."
            Updater.askInstallPermission(ctx)
            return
        }
        viewModelScope.launch {
            updateProgress = 0f
            updateMsg = null
            try {
                val file = withContext(Dispatchers.IO) { Updater.download(ctx, u) { updateProgress = it } }
                Updater.install(ctx, file)
            } catch (e: Exception) {
                updateMsg = "No se pudo descargar la actualización. Revisa tu conexión."
            }
            updateProgress = null
        }
    }

    /** Fechas de publicación del BCV (el USDT no marca fechas de navegación). */
    val dates: List<LocalDate>
        get() = history.filterKeys { it.official }.values.flatMap { l -> l.map { it.date } }.distinct().sorted()

    /** Última fecha con tasa en vigor hoy (la más reciente que no es futura). */
    val currentDate: LocalDate?
        get() {
            val d = dates
            return d.lastOrNull { !it.isAfter(LocalDate.now()) } ?: d.firstOrNull()
        }

    val selectedDate: LocalDate? get() = pinned ?: currentDate
    val prevDate: LocalDate? get() = selectedDate?.let { s -> dates.lastOrNull { it.isBefore(s) } }
    val nextDate: LocalDate? get() = selectedDate?.let { s -> dates.firstOrNull { it.isAfter(s) } }
    val firstDate: LocalDate? get() = dates.firstOrNull()
    val lastDate: LocalDate? get() = dates.lastOrNull()

    fun rateFor(c: Cur): Rate? {
        if (c == Cur.USDT && pinned == null) usdtLive?.let { return it }
        val s = selectedDate ?: return null
        return history[c]?.lastOrNull { !it.date.isAfter(s) } ?: history[c]?.firstOrNull()
    }

    /** Cambio frente a la publicación anterior de la misma moneda. */
    fun changeFor(c: Cur): Change? {
        val r = rateFor(c) ?: return null
        val prev = history[c]?.lastOrNull { it.date.isBefore(r.date) } ?: return null
        val diff = r.value - prev.value
        return Change(diff, diff / prev.value * 100, prev.date)
    }

    private fun loadLive(): Rate? {
        val p = prefs.getString("usdt_live", null)?.split('=') ?: return null
        return try { Rate(p[1].toDouble(), LocalDate.parse(p[0])) } catch (x: Exception) { null }
    }

    private fun load(c: Cur): List<Rate> {
        val raw = prefs.getString("hist_${c.name}", null) ?: return emptyList()
        return raw.split(';').mapNotNull { e ->
            val p = e.split('=')
            if (p.size != 2) null else try { Rate(p[1].toDouble(), LocalDate.parse(p[0])) } catch (x: Exception) { null }
        }.sortedBy { it.date }
    }

    fun refresh() {
        checkUpdate()
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val (fetched, live) = withContext(Dispatchers.IO) {
                    Cur.entries.associateWith { c -> runCatching { fetch(c) }.getOrNull() } to runCatching { fetchLive() }.getOrNull()
                }
                if (Cur.entries.filter { it.official }.all { fetched[it] == null }) throw java.io.IOException()
                val fresh = Cur.entries.associateWith { fetched[it] ?: history[it].orEmpty() }
                prefs.edit().apply {
                    fresh.forEach { (c, list) ->
                        putString("hist_${c.name}", list.joinToString(";") { "${it.date}=${it.value}" })
                    }
                    live?.let { putString("usdt_live", "${it.date}=${it.value}") }
                }.apply()
                history = fresh
                if (live != null) usdtLive = live
                recalc()
            } catch (e: Exception) {
                error = if (dates.isEmpty()) "Sin conexión y sin tasas guardadas" else "Sin conexión, usando las tasas guardadas"
            }
            loading = false
        }
    }

    private fun fetch(c: Cur): List<Rate> {
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

    private fun fetchLive(): Rate? {
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

    private fun parse(s: String) = s.replace(',', '.').toDoubleOrNull()
    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v).replace('.', ',')

    /** Recalcula el campo que el usuario no editó, con la tasa de la fecha elegida. */
    private fun recalc() {
        val r = rateFor(currency)?.value ?: return
        if (lastEdited == Field.Foreign) {
            bsText = parse(foreignText)?.let { fmt(it * r) } ?: ""
        } else {
            foreignText = parse(bsText)?.let { fmt(it / r) } ?: ""
        }
    }

    fun onForeign(t: String) {
        foreignText = t
        lastEdited = Field.Foreign
        recalc()
    }

    fun onBs(t: String) {
        bsText = t
        lastEdited = Field.Bs
        recalc()
    }

    /** Reinicia ambos montos a 0 de un solo toque. */
    fun clearAmounts() {
        foreignText = ""
        bsText = ""
    }

    /** Cambia de moneda manteniendo los bolívares y recalculando la otra divisa. */
    fun onCurrency(c: Cur) {
        currency = c
        lastEdited = Field.Bs
        recalc()
    }

    private fun pin(d: LocalDate?) {
        pinned = if (d == currentDate) null else d
        recalc()
    }

    fun goPrev() = pin(prevDate)
    fun goNext() = pin(nextDate)
    fun goCurrent() = pin(null)

    /** Elige un día del calendario: se usa la tasa vigente ese día. */
    fun pickDate(day: LocalDate) {
        val d = dates
        pin(d.lastOrNull { !it.isAfter(day) } ?: d.firstOrNull())
    }

    fun onMode(m: Mode) { mode = m }
    fun toggleCalcSide() { calcInBs = !calcInBs }

    val calcResult: Double? get() = evalExpr(expr)

    /** Resultado de la calculadora convertido a la otra moneda con la tasa elegida. */
    val calcConverted: Double?
        get() {
            val v = calcResult ?: return null
            val r = rateFor(currency)?.value ?: return null
            return if (calcInBs) v / r else v * r
        }

    private fun lastNumber() = expr.takeLastWhile { it.isDigit() || it == ',' }

    fun key(k: String) {
        when (k) {
            "C" -> { expr = ""; justEvaluated = false }
            "⌫" -> if (justEvaluated) { expr = ""; justEvaluated = false } else expr = expr.dropLast(1)
            "=" -> calcResult?.let {
                expr = String.format(Locale.US, "%.6f", it).trimEnd('0').trimEnd('.').replace('.', ',').replace('-', '−')
                justEvaluated = true
            }
            "+", "−", "×", "÷" -> {
                if (expr.isEmpty()) { if (k == "−") expr = k; return }
                justEvaluated = false
                expr = if (expr.last() in "+−×÷") { if (expr.length == 1) expr else expr.dropLast(1) + k } else expr + k
            }
            "," -> {
                if (justEvaluated) { expr = ""; justEvaluated = false }
                val n = lastNumber()
                if (',' in n || expr.length >= 40) return
                expr += if (n.isEmpty()) "0," else ","
            }
            else -> { // dígitos y "00"
                if (justEvaluated) { expr = ""; justEvaluated = false }
                if (expr.length + k.length > 40) return
                val n = lastNumber()
                if (n == "0" && k.all { it == '0' }) return
                expr = if (n == "0") expr.dropLast(1) + k.trimStart('0').ifEmpty { "0" } else expr + k
            }
        }
    }
}
