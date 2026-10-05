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

enum class Mode { Convert, Calc }

/** Tasa publicada por el BCV con su fecha valor. */
data class Rate(val value: Double, val date: LocalDate)

/** Variación de una tasa respecto a la publicación anterior. */
data class Change(val diff: Double, val pct: Double, val since: LocalDate)

/**
 * Evalúa una expresión con + − × ÷, paréntesis y % (con precedencia).
 * Ignora operadores finales y cierra los paréntesis que falten.
 * "%" divide entre 100; tras + o − (ej. 200+10%) es ese porcentaje del valor de la izquierda.
 */
fun evalExpr(src: String): Double? {
    var s = src.trimEnd { it in "+−×÷(" }
    if (s.isEmpty()) return null
    s += ")".repeat(s.count { it == '(' } - s.count { it == ')' })
    var i = 0
    var pct = false // el último término fue un número/grupo seguido de %

    fun number(): Double? {
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == ',')) i++
        return s.substring(st, i).replace(',', '.').toDoubleOrNull()
    }
    lateinit var expr: () -> Double?
    fun factor(): Double? {
        if (i < s.length && s[i] == '−') { i++; return factor()?.let { -it } }
        var v = if (i < s.length && s[i] == '(') {
            i++
            val inner = expr() ?: return null
            if (i >= s.length || s[i] != ')') return null
            i++
            inner
        } else number() ?: return null
        pct = false
        while (i < s.length && s[i] == '%') { i++; v /= 100; pct = true }
        return v
    }
    fun term(): Double? {
        var v = factor() ?: return null
        var single = pct
        while (i < s.length && (s[i] in "×÷(")) {
            val op = if (s[i] == '(') '×' else s[i++]
            val r = factor() ?: return null
            v = if (op == '×') v * r else v / r
            single = false
        }
        pct = single
        return v
    }
    expr = fun(): Double? {
        var acc = term() ?: return null
        while (i < s.length && s[i] in "+−") {
            val op = s[i++]
            val r = term() ?: return null
            val d = if (pct) acc * r else r
            acc = if (op == '+') acc + d else acc - d
        }
        pct = false
        return acc
    }
    val v = expr() ?: return null
    return if (i == s.length) v.takeIf { it.isFinite() } else null
}

private enum class Field { Foreign, Bs }

class RatesViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("rates", 0)

    /** Historial completo por moneda, ordenado por fecha ascendente. */
    var history by mutableStateOf(RatesSync.loadAll(prefs))
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
    private var usdtLive by mutableStateOf(RatesSync.loadLive(prefs))

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


    private var lastUpdateCheck = 0L

    /** Busca versión nueva; si [force] es false, no repite la consulta antes de 2 minutos. */
    fun checkUpdate(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheck < 2 * 60 * 1000) return
        lastUpdateCheck = now
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val u = withContext(Dispatchers.IO) { runCatching { Updater.fetchLatest() }.getOrNull() }
            update = u?.takeIf { Updater.isNewer(it.version, Updater.installedVersion(ctx)) }
        }
    }

    fun clearUpdateMsg() { updateMsg = null }

    /** Tema elegido ("light" | "dark"); sin elección (o "auto") sigue al teléfono. */
    fun loadTheme() {
        AppTheme.mode = when (val t = prefs.getString("theme", null)) { "dark", "light" -> t; else -> "auto" }
        AppTheme.dark = AppTheme.resolve()
    }

    /** Alterna Automático → Claro → Oscuro → Automático. */
    fun toggleTheme() {
        AppTheme.mode = when (AppTheme.mode) { "auto" -> "light"; "light" -> "dark"; else -> "auto" }
        AppTheme.dark = AppTheme.resolve()
        prefs.edit().putString("theme", AppTheme.mode).apply()
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

    fun rateFor(c: Cur): Rate? = rateAt(c, selectedDate)

    /** Tasa de una fecha concreta (la pantalla conserva la "página" anterior mientras se desliza a la nueva). */
    fun rateAt(c: Cur, d: LocalDate?): Rate? {
        if (c == Cur.USDT && d != null && d == currentDate) usdtLive?.let { return it }
        val s = d ?: return null
        return history[c]?.lastOrNull { !it.date.isAfter(s) } ?: history[c]?.firstOrNull()
    }

    /** Cambio frente a la publicación anterior de la misma moneda. */
    fun changeFor(c: Cur): Change? = changeAt(c, selectedDate)

    fun changeAt(c: Cur, d: LocalDate?): Change? {
        val r = rateAt(c, d) ?: return null
        val prev = history[c]?.lastOrNull { it.date.isBefore(r.date) } ?: return null
        val diff = r.value - prev.value
        return Change(diff, diff / prev.value * 100, prev.date)
    }

    /** El USDT solo existe para la tasa vigente de hoy. */
    fun currenciesAt(d: LocalDate?): List<Cur> = if (d == currentDate) Cur.entries else Cur.entries.filter { it.official }

    private var lastSync = RatesSync.lastSync(prefs)

    /** Vuelve a leer lo guardado (los trabajos en segundo plano pueden haber traído tasas nuevas). */
    private fun reloadFromPrefs() {
        history = RatesSync.loadAll(prefs)
        RatesSync.loadLive(prefs)?.let { usdtLive = it }
        lastSync = RatesSync.lastSync(prefs)
        recalc()
    }

    /** Al volver al frente (y cada pocos minutos abierta): si lo guardado tiene más de 10 min, actualiza en silencio. */
    fun refreshIfStale() {
        if (loading) return
        reloadFromPrefs()
        if (System.currentTimeMillis() - lastSync > 10 * 60 * 1000) refresh(silent = dates.isNotEmpty())
    }

    /** [silent]: sin indicador de carga ni mensaje de error (actualización automática). */
    fun refresh(silent: Boolean = false) {
        if (!silent) checkUpdate()
        viewModelScope.launch {
            if (!silent) { loading = true; error = null }
            val r = withContext(Dispatchers.IO) { RatesSync.pull(prefs) }
            if (r != null) {
                history = r.history
                if (r.live != null) usdtLive = r.live
                lastSync = RatesSync.lastSync(prefs)
                recalc()
            } else if (!silent) {
                error = if (dates.isEmpty()) "Sin conexión y sin tasas guardadas" else "Sin conexión, usando las tasas guardadas"
            }
            loading = false
        }
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

    /** Monedas disponibles: el USDT solo existe para la tasa vigente de hoy, no en fechas anteriores ni futuras. */
    val currencies: List<Cur> get() = if (pinned == null) Cur.entries else Cur.entries.filter { it.official }

    private fun pin(d: LocalDate?) {
        pinned = if (d == currentDate) null else d
        if (pinned != null && !currency.official) { currency = Cur.USD; lastEdited = Field.Bs }
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
                val last = expr.last()
                expr = when {
                    last == '(' -> if (k == "−") expr + k else expr
                    last in "+−×÷" ->
                        if (expr.length == 1 || expr[expr.length - 2] == '(') expr else expr.dropLast(1) + k
                    else -> expr + k
                }
            }
            "(" -> {
                if (justEvaluated) { expr = ""; justEvaluated = false }
                if (expr.length >= 40) return
                expr += if (expr.isNotEmpty() && (expr.last().isDigit() || expr.last() == ',' || expr.last() in ")%")) "×(" else "("
            }
            ")" -> {
                val open = expr.count { it == '(' } - expr.count { it == ')' }
                if (open > 0 && expr.isNotEmpty() && (expr.last().isDigit() || expr.last() in ")%") && expr.length < 40) {
                    justEvaluated = false
                    expr += ")"
                }
            }
            "%" -> if (expr.isNotEmpty() && (expr.last().isDigit() || expr.last() == ')') && expr.length < 40) {
                justEvaluated = false
                expr += "%"
            }
            "," -> {
                if (justEvaluated) { expr = ""; justEvaluated = false }
                val n = lastNumber()
                if (',' in n || expr.length >= 40) return
                expr += if (n.isNotEmpty()) "," else if (expr.isNotEmpty() && expr.last() in ")%") "×0," else "0,"
            }
            else -> { // dígitos y "00"
                if (justEvaluated) { expr = ""; justEvaluated = false }
                if (expr.length + k.length > 40) return
                val n = lastNumber()
                if (n == "0" && k.all { it == '0' }) return
                expr = when {
                    n == "0" -> expr.dropLast(1) + k.trimStart('0').ifEmpty { "0" }
                    expr.isNotEmpty() && expr.last() in ")%" -> expr + "×" + k
                    else -> expr + k
                }
            }
        }
    }
}
