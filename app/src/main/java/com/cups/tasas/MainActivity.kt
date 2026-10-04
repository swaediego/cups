@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cups.tasas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Modo claro: papel blanco, tinta azul noche. Dorado (dólar), azul (euro) y rojo (Bs) vienen del logo.
private val Paper = Color(0xFFFFFFFF)
private val Surface1 = Color(0xFFF5F7FC)
private val Line = Color(0xFFE2E8F3)
private val Ink = Color(0xFF0B1220)
private val Muted = Color(0xFF5A6684)
private val Hint = Color(0xFF7F8BA8)
private val Gold = Color(0xFFFFB92E)
private val GoldInk = Color(0xFF8F5A00)
private val Blue = Color(0xFF3D8BFF)
private val BlueInk = Color(0xFF1F5FD6)
private val Red = Color(0xFFFF5A4D)
private val RedInk = Color(0xFFC0291D)
private val CoinInk = Color(0xFF05101F)
private val Warn = Color(0xFF9A5B00)

private val Green = Color(0xFF2BC48A)
private val GreenInk = Color(0xFF0F7A55)

private fun accentOf(c: Cur) = when (c) { Cur.USD -> Gold; Cur.EUR -> Blue; Cur.USDT -> Green }
private fun inkOf(c: Cur) = when (c) { Cur.USD -> GoldInk; Cur.EUR -> BlueInk; Cur.USDT -> GreenInk }

private val Manrope = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)
private const val TNUM = "tnum"
private val SoftShadow = Color(0x330B1220)

class MainActivity : ComponentActivity() {
    private val vm: RatesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = BlueInk, onPrimary = Paper, background = Paper, surface = Paper,
                    onSurface = Ink, onSurfaceVariant = Muted, surfaceContainerHigh = Paper,
                    surfaceContainerHighest = Surface1, outlineVariant = Line,
                ),
            ) {
                Screen(vm)
            }
        }
    }
}

private fun money(v: Double) = String.format(Locale.GERMANY, "%,.2f", v)

private val esVE = Locale("es", "VE")
private fun shortDate(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("d MMM", esVE)).replace(".", "")
private fun longDate(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", esVE)).replace(".", "").replace(",", "")

@Composable
private fun Screen(vm: RatesViewModel) {
    val cur = vm.currency
    val accent by animateColorAsState(accentOf(cur), tween(450), label = "accent")
    val ink by animateColorAsState(inkOf(cur), tween(450), label = "ink")
    val keyboard = LocalSoftwareKeyboardController.current
    var picking by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(Paper).drawBehind {
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent), Offset(size.width * 0.1f, -40f), size.width))
            drawRect(Brush.radialGradient(listOf(Blue.copy(alpha = 0.08f), Color.Transparent), Offset(size.width, size.height * 0.8f), size.width * 0.9f))
        }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(vm.loading, onRefresh = vm::refresh)

            DateBar(vm, ink, onPick = { picking = true })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Cur.entries.forEach { c ->
                    RateTile(c, vm.rateFor(c), c == cur, Modifier.weight(1f)) { vm.onCurrency(c) }
                }
            }

            ChangeCard(cur, vm.changeFor(cur), ink)

            ModeToggle(vm.mode, ink, vm::onMode)

            val r = vm.rateFor(cur)
            val shape = RoundedCornerShape(28.dp)
            if (vm.mode == Mode.Calc) {
                CalcCard(vm, cur, r, accent, ink)
            } else Column(
                Modifier.fillMaxWidth()
                    .shadow(16.dp, shape, ambientColor = SoftShadow, spotColor = SoftShadow)
                    .clip(shape)
                    .background(Paper)
                    .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(accent.copy(alpha = 0.6f), Line))), shape)
                    .padding(vertical = 8.dp),
            ) {
                AmountRow(cur.label, cur.symbol, accent, ink, vm.foreignText, vm::onForeign, onDone = { keyboard?.hide() })
                Divider(cur, r)
                AmountRow("Bolívares", "Bs", Red, RedInk, vm.bsText, vm::onBs, onDone = { keyboard?.hide() })
            }
            if (vm.mode == Mode.Convert && (vm.foreignText.isNotEmpty() || vm.bsText.isNotEmpty())) {
                Pill("Reiniciar", ink, Modifier.align(Alignment.CenterHorizontally)) { vm.clearAmounts() }
            }

            vm.error?.let {
                Text(it, color = Warn, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (picking && vm.selectedDate != null) {
        PickDialog(vm, onDismiss = { picking = false })
    }
}

@Composable
private fun PickDialog(vm: RatesViewModel, onDismiss: () -> Unit) {
    val first = vm.firstDate ?: return
    val last = vm.lastDate ?: return
    val sel = vm.selectedDate ?: return
    val state = rememberDatePickerState(
        initialSelectedDateMillis = sel.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = first.year..last.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return !d.isBefore(first) && !d.isAfter(last)
            }
            override fun isSelectableYear(year: Int) = year in first.year..last.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { vm.pickDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Aceptar", fontFamily = Manrope, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", fontFamily = Manrope, color = Muted) } },
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Paper),
    ) { DatePicker(state = state) }
}

@Composable
private fun Header(loading: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("cups", color = Ink, fontSize = 30.sp, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
        Spacer(Modifier.weight(1f))
        val spin = rememberInfiniteTransition(label = "spin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "deg",
        )
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Surface1)
                .border(1.dp, Line, CircleShape)
                .clickable(enabled = !loading, role = Role.Button, onClick = onRefresh)
                .semantics { contentDescription = "Actualizar tasas" },
            contentAlignment = Alignment.Center,
        ) {
            RefreshIcon(Ink, Modifier.size(20.dp).rotate(if (loading) spin.value else 0f))
        }
    }
}

@Composable
private fun DateBar(vm: RatesViewModel, ink: Color, onPick: () -> Unit) {
    val sel = vm.selectedDate
    val current = vm.currentDate
    val today = LocalDate.now()
    val label = when {
        sel == null -> "Sin tasas"
        current != null && sel.isAfter(current) -> "Próxima tasa"
        sel == current && sel == today -> "Tasa de hoy"
        sel == current -> "Tasa vigente"
        else -> "Tasa anterior"
    }
    val newAvailable = vm.nextDate != null && sel == current
    val shape = RoundedCornerShape(24.dp)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Surface1).border(1.dp, Line, shape).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavButton(left = true, enabled = vm.prevDate != null, description = "Tasa del día anterior", onClick = vm::goPrev)
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = sel != null, role = Role.Button, onClick = onPick)
                    .semantics { contentDescription = "Elegir fecha de la tasa" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CalendarIcon(ink, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(label, color = ink, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                    Text(
                        sel?.let { longDate(it) } ?: "—", color = Ink, fontSize = 17.sp,
                        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp,
                    )
                }
            }
            NavButton(left = false, enabled = vm.nextDate != null, description = "Tasa del día siguiente", badge = newAvailable, onClick = vm::goNext)
        }
        if (sel != null && sel != current) {
            Text(
                "Volver a la tasa de hoy", color = ink, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 40.dp)
                    .clip(CircleShape).clickable(role = Role.Button, onClick = vm::goCurrent)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun NavButton(left: Boolean, enabled: Boolean, description: String, badge: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Paper).border(1.dp, Line, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        ChevronIcon(left, Ink.copy(alpha = if (enabled) 1f else 0.25f), Modifier.size(20.dp))
        if (badge) {
            Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(10.dp).clip(CircleShape).background(Red).border(2.dp, Paper, CircleShape))
        }
    }
}

@Composable
private fun RateTile(c: Cur, r: Rate?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = accentOf(c)
    val fill by animateColorAsState(if (selected) accent.copy(alpha = 0.14f) else Surface1, tween(300), label = "fill")
    val stroke by animateColorAsState(if (selected) accent else Line, tween(300), label = "stroke")
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(fill).border(if (selected) 1.5.dp else 1.dp, stroke, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Coin(c.symbol, accent, 22)
            Text(c.label, color = if (selected) Ink else Muted, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
        }
        Text(
            r?.let { money(it.value) } ?: "—",
            color = Ink, fontSize = 20.sp, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp, style = TextStyle(fontFeatureSettings = TNUM),
        )
        Text(
            r?.let { if (c == Cur.USDT && it.date == LocalDate.now()) "en vivo" else shortDate(it.date) } ?: "sin datos",
            color = Muted, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Coin(label: String, color: Color, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = CoinInk, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
            fontSize = (size * if (label.length > 1) 0.4f else 0.54f).sp,
        )
    }
}

@Composable
private fun Divider(cur: Cur, rate: Rate?) {
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Line))
        if (rate != null) {
            Text(
                "1 ${cur.symbol} = Bs. ${money(rate.value)}",
                color = Muted, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = TNUM),
                modifier = Modifier.clip(CircleShape).background(Surface1).border(1.dp, Line, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AmountRow(
    label: String, symbol: String, tint: Color, tintInk: Color, value: String,
    onChange: (String) -> Unit, onDone: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val selection = TextSelectionColors(handleColor = tintInk, backgroundColor = tint.copy(alpha = 0.35f))

    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Coin(symbol, tint, 20)
                Text(label, color = Muted, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
                if (copied) {
                    Text(
                        "· Copiado", color = tintInk, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            CompositionLocalProvider(LocalTextSelectionColors provides selection) {
                BasicTextField(
                    value = value,
                    onValueChange = { t -> if (t.length <= 14 && t.all { it.isDigit() || it == ',' || it == '.' }) onChange(t) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    textStyle = TextStyle(
                        color = Ink, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = if (value.length > 10) 28.sp else 38.sp,
                        fontFeatureSettings = TNUM, letterSpacing = (-0.8).sp,
                    ),
                    cursorBrush = SolidColor(tintInk),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) Text("0,00", color = Hint, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 38.sp, letterSpacing = (-0.8).sp)
                            inner()
                        }
                    },
                )
            }
        }
        val enabled = value.isNotEmpty()
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (copied) tint.copy(alpha = 0.2f) else Surface1)
                .border(1.dp, if (copied) tintInk else Line, CircleShape)
                .clickable(
                    enabled = enabled, role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) {
                    scope.launch {
                        clipboard.setText(AnnotatedString(value))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        copied = true
                        delay(1600)
                        copied = false
                    }
                }
                .semantics { contentDescription = if (copied) "Copiado" else "Copiar $label" },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(copied, animationSpec = tween(180), label = "copy") { done ->
                if (done) CheckIcon(tintInk, Modifier.size(22.dp))
                else CopyIcon(Ink.copy(alpha = if (enabled) 1f else 0.3f), Modifier.size(22.dp))
            }
        }
    }
}

// Iconos dibujados con un trazo único, extremos redondeados
@Composable
private fun CopyIcon(color: Color, modifier: Modifier) = Canvas(modifier) {
    val s = Stroke(width = size.width * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawRoundRect(color, Offset(size.width * 0.34f, size.height * 0.34f), Size(size.width * 0.54f, size.height * 0.54f), CornerRadius(size.width * 0.14f), s)
    val back = Path().apply {
        moveTo(size.width * 0.66f, size.height * 0.16f)
        lineTo(size.width * 0.30f, size.height * 0.16f)
        quadraticTo(size.width * 0.14f, size.height * 0.16f, size.width * 0.14f, size.height * 0.32f)
        lineTo(size.width * 0.14f, size.height * 0.68f)
        quadraticTo(size.width * 0.14f, size.height * 0.84f, size.width * 0.28f, size.height * 0.84f)
    }
    drawPath(back, color, style = s)
}

@Composable
private fun CheckIcon(color: Color, modifier: Modifier) = Canvas(modifier) {
    val p = Path().apply {
        moveTo(size.width * 0.2f, size.height * 0.54f)
        lineTo(size.width * 0.42f, size.height * 0.75f)
        lineTo(size.width * 0.8f, size.height * 0.28f)
    }
    drawPath(p, color, style = Stroke(width = size.width * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun RefreshIcon(color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width * 0.09f
    val inset = size.width * 0.16f
    drawArc(color, 40f, 280f, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(w, cap = StrokeCap.Round))
    val tip = Path().apply {
        moveTo(size.width * 0.80f, size.height * 0.12f)
        lineTo(size.width * 0.80f, size.height * 0.36f)
        lineTo(size.width * 0.56f, size.height * 0.30f)
    }
    drawPath(tip, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun ChevronIcon(left: Boolean, color: Color, modifier: Modifier) = Canvas(modifier) {
    val a = if (left) 0.62f else 0.38f
    val b = if (left) 0.38f else 0.62f
    val p = Path().apply {
        moveTo(size.width * a, size.height * 0.2f)
        lineTo(size.width * b, size.height * 0.5f)
        lineTo(size.width * a, size.height * 0.8f)
    }
    drawPath(p, color, style = Stroke(width = size.width * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun CalendarIcon(color: Color, modifier: Modifier) = Canvas(modifier) {
    val s = Stroke(width = size.width * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawRoundRect(color, Offset(size.width * 0.12f, size.height * 0.2f), Size(size.width * 0.76f, size.height * 0.68f), CornerRadius(size.width * 0.14f), s)
    drawLine(color, Offset(size.width * 0.12f, size.height * 0.44f), Offset(size.width * 0.88f, size.height * 0.44f), s.width, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.32f, size.height * 0.08f), Offset(size.width * 0.32f, size.height * 0.28f), s.width, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.68f, size.height * 0.08f), Offset(size.width * 0.68f, size.height * 0.28f), s.width, StrokeCap.Round)
}

@Composable
private fun ChangeCard(c: Cur, ch: Change?, ink: Color) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Surface1).border(1.dp, Line, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ch == null) {
            Text("Sin dato anterior para comparar", color = Muted, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.Medium)
            return@Row
        }
        val up = ch.diff > 0.0049
        val down = ch.diff < -0.0049
        val tone = if (up) RedInk else if (down) GreenInk else Muted
        val verb = if (up) "subió" else if (down) "bajó" else "se mantuvo"
        val sign = if (up) "▲ +" else if (down) "▼ −" else "• "
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${c.label} $verb vs. ${shortDate(ch.since)}", color = Muted, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
            Text(
                "$sign${money(kotlin.math.abs(ch.diff))} Bs", color = tone, fontSize = 20.sp, fontFamily = Manrope,
                fontWeight = FontWeight.ExtraBold, style = TextStyle(fontFeatureSettings = TNUM),
            )
        }
        Text(
            "${if (up) "+" else if (down) "−" else ""}${String.format(Locale.GERMANY, "%.2f", kotlin.math.abs(ch.pct))}%",
            color = tone, fontSize = 15.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(CircleShape).background(tone.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ModeToggle(mode: Mode, ink: Color, onMode: (Mode) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Surface1).border(1.dp, Line, shape).padding(4.dp)) {
        listOf(Mode.Convert to "Convertir", Mode.Calc to "Calculadora").forEach { (m, label) ->
            val sel = m == mode
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Paper else Color.Transparent)
                    .selectable(selected = sel, role = Role.Tab) { onMode(m) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (sel) ink else Muted, fontSize = 14.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Pill(text: String, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text, color = ink, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold,
        modifier = modifier.heightIn(min = 40.dp).clip(CircleShape).background(Surface1).border(1.dp, Line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun CalcCard(vm: RatesViewModel, cur: Cur, r: Rate?, accent: Color, ink: Color) {
    val inBs = vm.calcInBs
    val fromSym = if (inBs) "Bs" else cur.symbol
    val toSym = if (inBs) cur.symbol else "Bs"
    val toInk = if (inBs) ink else RedInk
    val shown = vm.expr.ifEmpty { "0" }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$fromSym → $toSym", color = ink, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                r?.let {
                    Text("1 ${cur.symbol} = ${money(it.value)}", color = Muted, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                shown, color = Ink, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End,
                fontSize = when { shown.length > 20 -> 30.sp; shown.length > 13 -> 40.sp; shown.length > 8 -> 52.sp; else -> 64.sp },
                maxLines = 2, letterSpacing = (-1.5).sp, style = TextStyle(fontFeatureSettings = TNUM),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text(
                "$toSym ${money(vm.calcConverted ?: 0.0)}", color = toInk, fontSize = 26.sp, fontFamily = Manrope,
                fontWeight = FontWeight.ExtraBold, maxLines = 1, style = TextStyle(fontFeatureSettings = TNUM),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val rows = listOf(
            listOf("C", "⇄", "00", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ",", "⌫", "="),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k -> CalcKey(k, accent, ink, Modifier.weight(1f), vm) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CalcKey(k: String, accent: Color, ink: Color, modifier: Modifier, vm: RatesViewModel) {
    val haptic = LocalHapticFeedback.current
    val isOp = k in listOf("÷", "×", "−", "+")
    val bg = when {
        k == "=" -> ink
        k == "C" -> accent.copy(alpha = 0.28f)
        isOp || k == "⇄" -> Blue.copy(alpha = 0.12f)
        k == "⌫" -> Line
        else -> Surface1
    }
    val fg = when {
        k == "=" -> Paper
        k == "C" || isOp || k == "⇄" -> ink
        else -> Ink
    }
    Box(
        modifier.height(64.dp).clip(CircleShape).background(bg)
            .combinedClickable(
                role = Role.Button,
                onClick = { if (k == "⇄") vm.toggleCalcSide() else vm.key(k) },
                // Mantener pulsado ⌫ reinicia todo el monto de golpe
                onLongClick = if (k == "⌫") ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.key("C") }) else null,
            )
            .semantics {
                contentDescription = when (k) {
                    "C" -> "Borrar todo"
                    "⌫" -> "Borrar un dígito, mantener para borrar todo"
                    "⇄" -> "Invertir conversión"
                    else -> k
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (k == "C") "AC" else k, color = fg, fontSize = if (k == "C") 20.sp else 26.sp, fontFamily = Manrope, fontWeight = if (isOp || k == "=") FontWeight.Bold else FontWeight.Medium)
    }
}
