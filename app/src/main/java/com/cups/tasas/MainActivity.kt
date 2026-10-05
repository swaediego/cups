@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.cups.tasas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Claro: papel blanco, tinta azul noche. Oscuro: fondo azul noche, tinta clara.
// Dorado (dólar), azul (euro), rojo (Bs) y verde (USDT) vienen del logo y no cambian; solo sus "tintas" para texto.
// Los colores leen el estado del tema, así que toda la interfaz se redibuja al cambiarlo.
internal object AppTheme {
    var dark by mutableStateOf(false)
    /** "auto" (sigue al teléfono), "light" u "dark". */
    var mode by mutableStateOf("auto")
    var systemDark = false
    fun resolve() = when (mode) { "dark" -> true; "light" -> false; else -> systemDark }
}
private fun themed(light: Long, dark: Long) = Color(if (AppTheme.dark) dark else light)

private val Paper get() = themed(0xFFFFFFFF, 0xFF0B1220)
private val Surface1 get() = themed(0xFFF5F7FC, 0xFF151D30)
private val Line get() = themed(0xFFE2E8F3, 0xFF26324C)
private val Ink get() = themed(0xFF0B1220, 0xFFEAF0FF)
private val Muted get() = themed(0xFF5A6684, 0xFF9AA7C4)
private val Hint get() = themed(0xFF7F8BA8, 0xFF66728F)
private val Gold = Color(0xFFFFB92E)
private val GoldInk get() = themed(0xFF8F5A00, 0xFFFFC857)
private val Blue = Color(0xFF3D8BFF)
private val BlueInk get() = themed(0xFF1F5FD6, 0xFF7DB0FF)
private val Red = Color(0xFFFF5A4D)
private val RedInk get() = themed(0xFFC0291D, 0xFFFF8A80)
private val CoinInk = Color(0xFF05101F)
private val Warn get() = themed(0xFF9A5B00, 0xFFFFB84D)

private val Green = Color(0xFF2BC48A)
private val GreenInk get() = themed(0xFF0F7A55, 0xFF5FDDAE)

private fun accentOf(c: Cur) = when (c) { Cur.USD -> Gold; Cur.EUR -> Blue; Cur.USDT -> Green }
private fun inkOf(c: Cur) = when (c) { Cur.USD -> GoldInk; Cur.EUR -> BlueInk; Cur.USDT -> GreenInk }

private val Manrope = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)
private const val TNUM = "tnum"
// Curvas de la web: salida fuerte para entradas y respuestas; otra para el movimiento en pantalla
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseMove = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** Texto que, al cambiar, entra con un leve ascenso y fundido (la salida es más rápida). No anima la primera pintura. */
@Composable
private fun Tick(text: String, modifier: Modifier = Modifier, content: @Composable (String) -> Unit) {
    AnimatedContent(
        targetState = text, modifier = modifier, label = "tick",
        transitionSpec = {
            (fadeIn(tween(240, easing = EaseOut)) + slideInVertically(tween(260, easing = EaseOut)) { it / 4 })
                .togetherWith(fadeOut(tween(90)))
                .using(SizeTransform(clip = false))
        },
    ) { content(it) }
}

/** Primera carga: cada sección entra subiendo y apareciendo, en escalera (una sola vez). */
@Composable
private fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 45L); shown = true }
    val p by animateFloatAsState(if (shown) 1f else 0f, tween(550, easing = EaseOut), label = "reveal")
    Box(modifier.graphicsLayer { alpha = p; translationY = (1f - p) * 14.dp.toPx() }) { content() }
}

private val SoftShadow get() = if (AppTheme.dark) Color(0x99000000) else Color(0x330B1220)

class MainActivity : ComponentActivity() {
    private val vm: RatesViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        vm.checkUpdate(force = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Si el teléfono cambia de claro a oscuro, la actividad se recrea y esto se recalcula (modo automático)
        AppTheme.systemDark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        vm.loadTheme()
        // Aviso de "actualización disponible": permiso de notificaciones (Android 13+) y chequeo periódico
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        Updater.schedule(applicationContext)
        RatesSync.schedule(applicationContext)
        // Tasas al día: al abrir o volver al frente, y cada 5 min mientras la app está a la vista
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    vm.refreshIfStale()
                    delay(5 * 60 * 1000L)
                }
            }
        }
        setContent {
            val dark = AppTheme.dark
            // Barras del sistema e iconos según el tema, y fondo de ventana para que no parpadee en blanco
            LaunchedEffect(dark) {
                val bar = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(bar) else SystemBarStyle.light(bar, bar),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(bar) else SystemBarStyle.light(bar, bar),
                )
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Paper.toArgb()))
            }
            val scheme = if (dark) androidx.compose.material3.darkColorScheme(
                primary = BlueInk, onPrimary = Paper, background = Paper, surface = Paper,
                onSurface = Ink, onSurfaceVariant = Muted, surfaceContainerHigh = Paper,
                surfaceContainerHighest = Surface1, outlineVariant = Line,
            ) else lightColorScheme(
                primary = BlueInk, onPrimary = Paper, background = Paper, surface = Paper,
                onSurface = Ink, onSurfaceVariant = Muted, surfaceContainerHigh = Paper,
                surfaceContainerHighest = Surface1, outlineVariant = Line,
            )
            MaterialTheme(colorScheme = scheme) {
                Screen(vm)
            }
        }
    }
}

/** Medidas de diseño (dp): la interfaz se escala a partir de ellas para ajustarse a cada pantalla. */
private val DESIGN_H = 760.dp
private val DESIGN_W = 360.dp

private fun money(v: Double) = String.format(Locale.GERMANY, "%,.2f", v)

private val esVE = Locale("es", "VE")
private fun shortDate(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("d MMM", esVE)).replace(".", "")
private fun longDate(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", esVE)).replace(".", "").replace(",", "")

@Composable
private fun Screen(vm: RatesViewModel) {
    val cur = vm.currency
    val accent by animateColorAsState(accentOf(cur), tween(500, easing = EaseOut), label = "accent")
    val ink by animateColorAsState(inkOf(cur), tween(500, easing = EaseOut), label = "ink")
    val keyboard = LocalSoftwareKeyboardController.current
    var picking by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(Paper).drawBehind {
            drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), Color.Transparent), Offset(size.width * 0.1f, -40f), size.width))
            drawRect(Brush.radialGradient(listOf(Blue.copy(alpha = 0.08f), Color.Transparent), Offset(size.width, size.height * 0.8f), size.width * 0.9f))
        }
    ) {
        // Se adapta a cualquier pantalla: la interfaz se diseña para 360 x 760 dp y se escala por igual
        // (tamaños y letras) según el alto y el ancho reales disponibles. No cambia con el teclado abierto.
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val scale = minOf(maxHeight / DESIGN_H, maxWidth / DESIGN_W).coerceIn(0.62f, 1f)
        val screenH = maxHeight
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale)) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        val calc = vm.mode == Mode.Calc
        // La calculadora se reparte en todo el alto (sin deslizar); si ni escalada cabe
        // (pantalla muy baja, p. ej. horizontal), queda el alto de diseño y entonces sí se desliza
        val contentH = maxOf(screenH / scale, DESIGN_H)
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp)
                .height(contentH - 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Reveal(0) { Header(vm.loading, vm.update != null, onRefresh = vm::refresh, onUpdate = { updating = true }, onTheme = vm::toggleTheme) }

            Reveal(1) { DateBar(vm, ink, onPick = { picking = true }) }

            Column {
                Reveal(2) {
                    // Monedas: tarjetas completas; en la calculadora se encogen a burbujas con el símbolo
                    AnimatedContent(
                        targetState = calc, label = "tiles",
                        transitionSpec = {
                            fadeIn(tween(220, delayMillis = 60, easing = EaseOut))
                                .togetherWith(fadeOut(tween(120)))
                                .using(SizeTransform(clip = true) { _, _ -> tween(320, easing = EaseMove) })
                        },
                    ) { compact ->
                        if (compact) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                                Cur.entries.forEach { c -> CurBubble(c, c == cur) { vm.onCurrency(c) } }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Cur.entries.forEach { c ->
                                    RateTile(c, vm.rateFor(c), c == cur, Modifier.weight(1f)) { vm.onCurrency(c) }
                                }
                            }
                        }
                    }
                }
                // La variación no cabe en la calculadora: se pliega
                AnimatedVisibility(
                    visible = !calc,
                    enter = fadeIn(tween(220, easing = EaseOut)) + expandVertically(tween(300, easing = EaseMove)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(300, easing = EaseMove)),
                ) {
                    Column {
                        Spacer(Modifier.height(18.dp))
                        Reveal(3) { ChangeCard(cur, vm.changeFor(cur), ink) }
                    }
                }
            }

            Reveal(4) { ModeToggle(vm.mode, ink, vm::onMode) }

            val r = vm.rateFor(cur)
            val shape = RoundedCornerShape(28.dp)
            Reveal(5, if (calc) Modifier.weight(1f) else Modifier) {
                // Convertir ↔ Calculadora: el panel nuevo sube con fundido y la altura se ajusta suavemente
                AnimatedContent(
                    targetState = vm.mode, label = "panel",
                    modifier = if (calc) Modifier.fillMaxSize() else Modifier,
                    transitionSpec = {
                        (fadeIn(tween(260, easing = EaseOut)) + slideInVertically(tween(280, easing = EaseOut)) { it / 24 } +
                            scaleIn(tween(280, easing = EaseOut), initialScale = 0.985f))
                            .togetherWith(fadeOut(tween(90)))
                            .using(SizeTransform(clip = false) { _, _ -> tween(300, easing = EaseMove) })
                    },
                ) { m ->
                    if (m == Mode.Calc) {
                        CalcCard(vm, cur, r, accent, ink, Modifier.fillMaxSize())
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
                }
            }
            if (vm.mode == Mode.Convert && (vm.foreignText.isNotEmpty() || vm.bsText.isNotEmpty())) {
                Pill("Reiniciar", ink, Modifier.align(Alignment.CenterHorizontally)) { vm.clearAmounts() }
            }

            vm.error?.let {
                Text(it, color = Warn, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.Medium)
            }
        }
        }
        }
        }
    }

    if (updating) vm.update?.let { UpdateDialog(vm, it, onDismiss = { updating = false }) }

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
private fun Header(loading: Boolean, hasUpdate: Boolean, onRefresh: () -> Unit, onUpdate: () -> Unit, onTheme: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("cups", color = Ink, fontSize = 30.sp, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
        Spacer(Modifier.weight(1f))
        if (hasUpdate) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Surface1)
                    .border(1.dp, Line, CircleShape)
                    .clickable(role = Role.Button, onClick = onUpdate)
                    .semantics { contentDescription = "Actualización disponible" },
                contentAlignment = Alignment.Center,
            ) {
                UpdateIcon(Ink, Modifier.size(22.dp))
                Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(10.dp).clip(CircleShape).background(Red).border(2.dp, Paper, CircleShape))
            }
            Spacer(Modifier.width(10.dp))
        }
        InfoButton()
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Surface1)
                .border(1.dp, Line, CircleShape)
                .clickable(role = Role.Button, onClick = onTheme)
                .semantics { contentDescription = "Tema: " + when (AppTheme.mode) { "dark" -> "oscuro"; "light" -> "claro"; else -> "automático" } },
            contentAlignment = Alignment.Center,
        ) {
            ThemeIcon(AppTheme.mode, Ink, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
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

/** Botón "i": abre una tarjeta flotante con la versión; se cierra al tocar fuera. */
@Composable
private fun InfoButton() {
    var open by remember { mutableStateOf(false) }
    // El popup vive mientras dure la animación de salida
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = open
    val press by animateFloatAsState(if (open) 1f else 0f, tween(260, easing = EaseOut), label = "infoPress")
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Surface1)
            .border(1.dp, Line, CircleShape)
            .clickable(role = Role.Button) { open = !open }
            .semantics { contentDescription = "Información de la app" },
        contentAlignment = Alignment.Center,
    ) {
        InfoIcon(Ink, Modifier.size(22.dp).graphicsLayer { scaleX = 1f - 0.12f * press; scaleY = 1f - 0.12f * press })
        if (visible.currentState || visible.targetState) {
            val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.roundToPx() }
            val edge = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.roundToPx() }
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                        IntOffset(
                            (anchorBounds.right - popupContentSize.width).coerceIn(edge, maxOf(edge, windowSize.width - popupContentSize.width - edge)),
                            anchorBounds.bottom + gap,
                        )
                },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(tween(160, easing = EaseOut)) +
                        scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.72f, transformOrigin = TransformOrigin(0.88f, 0f)) +
                        slideInVertically(tween(260, easing = EaseOut)) { -it / 10 },
                    exit = fadeOut(tween(120, easing = EaseOut)) +
                        scaleOut(tween(150, easing = EaseOut), targetScale = 0.9f, transformOrigin = TransformOrigin(0.88f, 0f)),
                ) { InfoCard() }
            }
        }
    }
}

@Composable
private fun InfoCard() {
    val version = Updater.installedVersion(androidx.compose.ui.platform.LocalContext.current)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.width(200.dp)
            .shadow(20.dp, shape, ambientColor = SoftShadow, spotColor = SoftShadow)
            .clip(shape).background(Paper).border(1.dp, Line, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("cups", color = Ink, fontSize = 22.sp, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        Text("Versión $version", color = Muted, fontSize = 14.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoIcon(color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width * 0.09f
    drawCircle(color, radius = size.width * 0.42f, center = center, style = Stroke(width = w))
    drawCircle(color, radius = w * 0.75f, center = Offset(center.x, size.height * 0.31f))
    drawLine(color, Offset(center.x, size.height * 0.45f), Offset(center.x, size.height * 0.70f), w, StrokeCap.Round)
}

@Composable
private fun UpdateDialog(vm: RatesViewModel, u: Update, onDismiss: () -> Unit) {
    val progress = vm.updateProgress
    AlertDialog(
        onDismissRequest = { if (progress == null) { vm.clearUpdateMsg(); onDismiss() } },
        containerColor = Paper,
        title = { Text("Actualización disponible", fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("cups ${u.version} está listo para instalar.", fontFamily = Manrope, color = Muted, fontSize = 14.sp)
                if (u.notes.isNotBlank()) Text(u.notes.trim().take(300), fontFamily = Manrope, color = Ink, fontSize = 13.sp)
                if (progress != null) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = BlueInk, trackColor = Line)
                vm.updateMsg?.let { Text(it, fontFamily = Manrope, color = Warn, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = progress == null, onClick = vm::startUpdate) {
                Text(if (progress == null) "Actualizar" else "Descargando…", fontFamily = Manrope, fontWeight = FontWeight.Bold, color = BlueInk)
            }
        },
        dismissButton = {
            TextButton(enabled = progress == null, onClick = { vm.clearUpdateMsg(); onDismiss() }) {
                Text("Más tarde", fontFamily = Manrope, color = Muted)
            }
        },
    )
}

@Composable
private fun ThemeIcon(mode: String, color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width * 0.09f
    val s = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (mode == "auto") { // círculo medio lleno: sigue al teléfono
        val r = size.width * 0.38f
        drawCircle(color, radius = r, center = center, style = s)
        drawArc(color, -90f, 180f, true, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r))
    } else if (mode == "light") { // sol
        drawCircle(color, radius = size.width * 0.18f, center = center, style = s)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val c = Math.cos(a).toFloat(); val sn = Math.sin(a).toFloat()
            drawLine(color, Offset(center.x + c * size.width * 0.32f, center.y + sn * size.width * 0.32f),
                Offset(center.x + c * size.width * 0.44f, center.y + sn * size.width * 0.44f), w, StrokeCap.Round)
        }
    } else { // luna
        val p = Path().apply {
            moveTo(size.width * 0.84f, size.height * 0.60f)
            cubicTo(size.width * 0.62f, size.height * 0.80f, size.width * 0.26f, size.height * 0.70f, size.width * 0.22f, size.height * 0.36f)
            cubicTo(size.width * 0.20f, size.height * 0.24f, size.width * 0.26f, size.height * 0.16f, size.width * 0.34f, size.height * 0.12f)
            cubicTo(size.width * 0.14f, size.height * 0.20f, size.width * 0.10f, size.height * 0.52f, size.width * 0.28f, size.height * 0.72f)
            cubicTo(size.width * 0.46f, size.height * 0.90f, size.width * 0.76f, size.height * 0.86f, size.width * 0.84f, size.height * 0.60f)
        }
        drawPath(p, color, style = s)
    }
}

@Composable
private fun UpdateIcon(color: Color, modifier: Modifier) = Canvas(modifier) {
    val s = Stroke(width = size.width * 0.095f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(size.width * 0.5f, size.height * 0.14f), Offset(size.width * 0.5f, size.height * 0.64f), s.width, StrokeCap.Round)
    val head = Path().apply {
        moveTo(size.width * 0.28f, size.height * 0.44f)
        lineTo(size.width * 0.5f, size.height * 0.66f)
        lineTo(size.width * 0.72f, size.height * 0.44f)
    }
    drawPath(head, color, style = s)
    drawLine(color, Offset(size.width * 0.2f, size.height * 0.86f), Offset(size.width * 0.8f, size.height * 0.86f), s.width, StrokeCap.Round)
}

@Composable
private fun DateBar(vm: RatesViewModel, ink: Color, onPick: () -> Unit) {
    val sel = vm.selectedDate
    val current = vm.currentDate
    val today = LocalDate.now()
    fun labelFor(s: LocalDate?) = when {
        s == null -> "Sin tasas"
        current != null && s.isAfter(current) -> "Próxima tasa"
        s == current && s == today -> "Tasa de hoy"
        s == current -> "Tasa vigente"
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
                AnimatedContent(
                    targetState = sel, label = "date",
                    transitionSpec = {
                        val back = targetState != null && initialState != null && targetState!!.isBefore(initialState!!)
                        val dir = if (back) -1 else 1
                        (fadeIn(tween(260, easing = EaseOut)) + slideInHorizontally(tween(280, easing = EaseOut)) { dir * it / 8 })
                            .togetherWith(fadeOut(tween(100)) + slideOutHorizontally(tween(160, easing = EaseOut)) { -dir * it / 10 })
                            .using(SizeTransform(clip = false))
                    },
                ) { d ->
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(labelFor(d), color = ink, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                        Text(
                            d?.let { longDate(it) } ?: "—", color = Ink, fontSize = 17.sp,
                            fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp,
                        )
                    }
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
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(160, easing = EaseOut), label = "press")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(fill).border(if (selected) 1.5.dp else 1.dp, stroke, shape)
            .selectable(selected = selected, interactionSource = press, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Coin(c.symbol, accent, 22)
            Text(c.label, color = if (selected) Ink else Muted, fontSize = 13.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
        }
        Tick(r?.let { money(it.value) } ?: "—") { t ->
            Text(
                t, color = Ink, fontSize = 20.sp, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp, style = TextStyle(fontFeatureSettings = TNUM),
            )
        }
        Tick(r?.let { if (c == Cur.USDT && it.date == LocalDate.now()) "en vivo" else shortDate(it.date) } ?: "sin datos") { t ->
            Text(t, color = Muted, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.Medium)
        }
    }
}

/** Moneda en la calculadora: burbuja con el símbolo; la elegida se resalta con el color de su moneda. */
@Composable
private fun CurBubble(c: Cur, selected: Boolean, onClick: () -> Unit) {
    val accent = accentOf(c)
    val fill by animateColorAsState(if (selected) accent.copy(alpha = 0.18f) else Surface1, tween(300), label = "bfill")
    val stroke by animateColorAsState(if (selected) accent else Line, tween(300), label = "bstroke")
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else if (selected) 1.06f else 1f, tween(180, easing = EaseOut), label = "bpress")
    Box(
        Modifier.size(46.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(fill).border(if (selected) 1.5.dp else 1.dp, stroke, CircleShape)
            .selectable(selected = selected, interactionSource = press, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = c.label },
        contentAlignment = Alignment.Center,
    ) { Coin(c.symbol, accent, 26) }
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
    val d = ch?.diff ?: 0.0
    val tone by animateColorAsState(
        if (d > 0.0049) RedInk else if (d < -0.0049) GreenInk else Muted, tween(350, easing = EaseOut), label = "tone",
    )
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
        val verb = if (up) "subió" else if (down) "bajó" else "se mantuvo"
        val sign = if (up) "▲ +" else if (down) "▼ −" else "• "
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Tick("${c.label} $verb vs. ${shortDate(ch.since)}") { t ->
                Text(t, color = Muted, fontSize = 12.sp, fontFamily = Manrope, fontWeight = FontWeight.SemiBold)
            }
            Tick("$sign${money(kotlin.math.abs(ch.diff))} Bs") { t ->
                Text(
                    t, color = tone, fontSize = 20.sp, fontFamily = Manrope,
                    fontWeight = FontWeight.ExtraBold, style = TextStyle(fontFeatureSettings = TNUM),
                )
            }
        }
        Tick(
            "${if (up) "+" else if (down) "−" else ""}${String.format(Locale.GERMANY, "%.2f", kotlin.math.abs(ch.pct))}%",
            modifier = Modifier.clip(CircleShape).background(tone.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 6.dp),
        ) { t -> Text(t, color = tone, fontSize = 15.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ModeToggle(mode: Mode, ink: Color, onMode: (Mode) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    // La pastilla se desliza entre las dos opciones en vez de saltar
    val bias by animateFloatAsState(if (mode == Mode.Calc) 1f else -1f, tween(300, easing = EaseMove), label = "modeBias")
    Box(Modifier.fillMaxWidth().clip(shape).background(Surface1).border(1.dp, Line, shape).padding(4.dp)) {
        Box(Modifier.fillMaxWidth(0.5f).height(44.dp).align(BiasAlignment(bias, 0f)).clip(RoundedCornerShape(12.dp)).background(Paper))
        Row(Modifier.fillMaxWidth()) {
            listOf(Mode.Convert to "Convertir", Mode.Calc to "Calculadora").forEach { (m, label) ->
                val sel = m == mode
                val textColor by animateColorAsState(if (sel) ink else Muted, tween(250, easing = EaseOut), label = "modeText")
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                        .selectable(selected = sel, role = Role.Tab) { onMode(m) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = textColor, fontSize = 14.sp, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                }
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
private fun CalcCard(vm: RatesViewModel, cur: Cur, r: Rate?, accent: Color, ink: Color, modifier: Modifier = Modifier) {
    val inBs = vm.calcInBs
    val fromSym = if (inBs) "Bs" else cur.symbol
    val toSym = if (inBs) cur.symbol else "Bs"
    val toInk = if (inBs) ink else Muted
    val shown = vm.expr.ifEmpty { "0" }
    // El teclado reparte el alto que sobra; el monto y el resultado ocupan el resto
    BoxWithConstraints(modifier) {
        val keyH = ((maxHeight - 150.dp - 10.dp - 8.dp * 5) / 6).coerceIn(34.dp, 72.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween,
            ) {
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
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        shown, color = Ink, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End,
                        fontSize = when { shown.length > 20 -> 30.sp; shown.length > 13 -> 40.sp; shown.length > 8 -> 52.sp; else -> 64.sp },
                        maxLines = 2, letterSpacing = (-1.5).sp, style = TextStyle(fontFeatureSettings = TNUM),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Tick(
                        "$toSym ${money(vm.calcConverted ?: 0.0)}",
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    ) { t ->
                        Text(
                            t, color = toInk, fontSize = 26.sp, fontFamily = Manrope,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1, style = TextStyle(fontFeatureSettings = TNUM),
                        )
                    }
                }
            }
            val rows = listOf(
                listOf("C", "⇄", "⌫", "÷"),
                listOf("(", ")", "%", "×"),
                listOf("7", "8", "9", "−"),
                listOf("4", "5", "6", "+"),
                listOf("1", "2", "3", "="),
                listOf("0", ","),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { k -> CalcKey(k, accent, ink, Modifier.weight(if (k == "0") 3f else 1f).height(keyH), vm) }
                    }
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
    val isGroup = k in listOf("(", ")", "%")
    val bg = when {
        k == "=" -> ink
        k == "C" -> accent.copy(alpha = 0.28f)
        isOp || isGroup || k == "⇄" -> Blue.copy(alpha = 0.12f)
        k == "⌫" -> Line
        else -> Surface1
    }
    val fg = when {
        k == "=" -> Paper
        k == "C" || isOp || isGroup || k == "⇄" -> ink
        else -> Ink
    }
    Box(
        modifier.clip(CircleShape).background(bg)
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
                    "%" -> "Porcentaje"
                    "(" -> "Abrir paréntesis"
                    ")" -> "Cerrar paréntesis"
                    else -> k
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (k == "C") "AC" else k, color = fg, fontSize = if (k == "C") 20.sp else 26.sp, fontFamily = Manrope, fontWeight = if (isOp || k == "=") FontWeight.Bold else FontWeight.Medium)
    }
}
