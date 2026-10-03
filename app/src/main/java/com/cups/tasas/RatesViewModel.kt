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

enum class Cur(val label: String, val symbol: String, val url: String) {
    USD("Dólar", "$", "https://ve.dolarapi.com/v1/historicos/dolares/oficial"),
    EUR("Euro", "€", "https://ve.dolarapi.com/v1/historicos/euros/oficial"),
}

/** Tasa publicada por el BCV con su fecha valor. */
data class Rate(val value: Double, val date: LocalDate)

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

    /** Fecha de tasa elegida por el usuario; null = seguir la tasa vigente de hoy. */
    private var pinned by mutableStateOf<LocalDate?>(null)
    private var lastEdited = Field.Foreign

    init { refresh() }

    val dates: List<LocalDate>
        get() = history.values.flatMap { l -> l.map { it.date } }.distinct().sorted()

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
        val s = selectedDate ?: return null
        return history[c]?.lastOrNull { !it.date.isAfter(s) } ?: history[c]?.firstOrNull()
    }

    private fun load(c: Cur): List<Rate> {
        val raw = prefs.getString("hist_${c.name}", null) ?: return emptyList()
        return raw.split(';').mapNotNull { e ->
            val p = e.split('=')
            if (p.size != 2) null else try { Rate(p[1].toDouble(), LocalDate.parse(p[0])) } catch (x: Exception) { null }
        }.sortedBy { it.date }
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val fresh = withContext(Dispatchers.IO) { Cur.entries.associateWith { fetch(it) } }
                prefs.edit().apply {
                    fresh.forEach { (c, list) ->
                        putString("hist_${c.name}", list.joinToString(";") { "${it.date}=${it.value}" })
                    }
                }.apply()
                history = fresh
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
}
