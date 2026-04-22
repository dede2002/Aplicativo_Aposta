package com.example.apostas.ui

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.NotaEntity
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class GraficoLucroAvancadoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ApostasTheme {
                GraficoLucroAvancadoScreen()
            }
        }
    }
}

@Composable
fun GraficoLucroAvancadoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val periodOptions = listOf("1d", "1s", "1m", "6m", "Data")

    var selectedPeriod by remember { mutableStateOf("1d") }
    var showDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    var apostasFiltradas by remember { mutableStateOf(emptyList<Aposta>()) }
    var lucroTotal by remember { mutableDoubleStateOf(0.0) }

    val calendar = remember { Calendar.getInstance() }

    val bgPrimary = Color(0xFF141824)
    val bgCard    = Color(0xFF1A1F30)
    val accent    = Color(0xFF4F6FFF)

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = bgPrimary, darkIcons = false)
        systemUiController.setNavigationBarColor(color = bgPrimary, darkIcons = false)
    }

    LaunchedEffect(showDatePicker) {
        if (showDatePicker) {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = calendar.time
                    showDatePicker = false
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        val notaSalva = withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(context).notaDao().getUltimaNota()
        }
        if (notaSalva != null) noteText = notaSalva.conteudo
    }

    LaunchedEffect(selectedPeriod, selectedDate) {
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val todasApostas = withContext(Dispatchers.IO) { db.apostaDao().getAll() }
            val cal = Calendar.getInstance()

            apostasFiltradas = when (selectedPeriod) {
                "1d" -> {
                    val hojeStr = formato.format(Date())
                    todasApostas.filter { it.data == hojeStr }
                }
                "1s", "1m" -> {
                    val dataLimite = when (selectedPeriod) {
                        "1s" -> cal.apply { add(Calendar.WEEK_OF_YEAR, -1) }.time
                        else -> cal.apply { add(Calendar.MONTH, -1) }.time
                    }
                    todasApostas.filter {
                        val data = runCatching { formato.parse(it.data) }.getOrNull()
                        data != null && !data.before(dataLimite)
                    }
                }
                "6m" -> {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.add(Calendar.MONTH, -5)
                    val dataLimite = cal.time
                    todasApostas.filter {
                        val data = runCatching { formato.parse(it.data) }.getOrNull()
                        data != null && !data.before(dataLimite)
                    }
                }
                "Data" -> {
                    selectedDate?.let {
                        val selectedStr = formato.format(it)
                        todasApostas.filter { aposta -> aposta.data == selectedStr }
                    } ?: emptyList()
                }
                else -> emptyList()
            }

            lucroTotal = apostasFiltradas.sumOf { it.lucro }
        }
    }

    val apostasParaGrafico = when (selectedPeriod) {
        "1s" -> agruparLucroPorDia(apostasFiltradas, dias = 7)
        "1m" -> agruparLucroPorDia(apostasFiltradas, dias = 30)
        "6m" -> agruparLucroPorMes(apostasFiltradas)
        else -> apostasFiltradas
    }

    val tituloGrafico = when (selectedPeriod) {
        "1d"   -> "Lucro do dia"
        "1s"   -> "Lucro por dia na última semana"
        "1m"   -> "Lucro por dia no último mês"
        "6m"   -> "Lucro por mês nos últimos 6 meses"
        "Data" -> "Lucro no dia selecionado"
        else   -> "Lucro por período"
    }

    val periodoExibido = when {
        selectedPeriod == "1d"                             -> "Hoje"
        selectedPeriod == "1s"                             -> "1 semana"
        selectedPeriod == "1m"                             -> "1 mês"
        selectedPeriod == "6m"                             -> "6 meses"
        selectedPeriod == "Data" && selectedDate != null   -> formato.format(selectedDate!!)
        else                                               -> selectedPeriod.uppercase()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgPrimary)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ── Header ───────────────────────────────────────────────
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            Text("Histórico", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(tituloGrafico, color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
        }

        // ── Gráfico ──────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgCard),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                GraficoCanvasSuave(apostasParaGrafico)
            }
        }

        // ── Chips de período ─────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            periodOptions.forEach { period ->
                val selected = selectedPeriod == period
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedPeriod = period
                        if (period == "Data") showDatePicker = true
                    },
                    shape = RoundedCornerShape(50.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent,
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(alpha = 0.06f),
                        labelColor = Color.White.copy(alpha = 0.55f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = accent,
                        borderColor = Color.White.copy(alpha = 0.12f),
                        borderWidth = 0.5.dp,
                        selectedBorderWidth = 0.5.dp
                    ),
                    label = {
                        if (period == "Data" && selectedDate == null) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(15.dp))
                        } else {
                            Text(
                                text = if (period == "Data" && selectedDate != null)
                                    formato.format(selectedDate!!)
                                else
                                    period.uppercase(),
                                fontSize = 12.sp
                            )
                        }
                    }
                )
            }
        }

        // ── Cards de estatísticas ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Apostas
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = bgCard),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        apostasFiltradas.size.toString(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "APOSTAS",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            // Lucro
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = bgCard),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "R$ %.2f".format(lucroTotal),
                        color = when {
                            lucroTotal > 0 -> Color(0xFF81C784)
                            lucroTotal < 0 -> Color(0xFFEF9A9A)
                            else           -> Color.White
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "LUCRO",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }

        // ── Card período ─────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = bgCard),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    periodoExibido,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Botão Bloco de notas ─────────────────────────────────
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White.copy(alpha = 0.07f),
                contentColor = Color.White.copy(alpha = 0.85f)
            ),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
            contentPadding = PaddingValues(vertical = 13.dp)
        ) {
            Text("📝 Bloco de notas", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }

    // ── Diálogo Bloco de notas ───────────────────────────────────
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1A1F30),
            title = { Text("Bloco de notas", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("Digite suas anotações...", color = Color.White.copy(alpha = 0.25f)) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White),
                    maxLines = 10,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF4F6FFF),
                        focusedBorderColor = Color(0xFF4F6FFF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color(0xFF1E2338),
                        unfocusedContainerColor = Color(0xFF1E2338)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val dao = AppDatabase.getDatabase(context).notaDao()
                                dao.limparNotas()
                                dao.salvar(NotaEntity(conteudo = noteText))
                            }
                        }
                        showDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F6FFF))
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

// ── Gráfico Canvas ────────────────────────────────────────────────
@Composable
fun GraficoCanvasSuave(apostas: List<Aposta>) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    var selectedInfo by remember { mutableStateOf<Pair<Aposta, Offset>?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val apostasOrdenadas = apostas.sortedBy {
        runCatching { formato.parse(it.data) }.getOrNull()
    }

    val density = LocalDensity.current

    LaunchedEffect(apostas) {
        touchX = null
        selectedInfo = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                touchX = change.position.x
                                change.consume()
                            }
                        } while (change.pressed)
                    }
                }
        ) {
            canvasSize = size
            val padding = 40f
            val width = size.width - padding * 2
            val height = size.height - padding * 2

            // Grid lines
            val gridCount = 4
            for (i in 0..gridCount) {
                val y = padding + i * (height / gridCount)
                drawLine(
                    color = Color.White.copy(alpha = 0.07f),
                    start = Offset(padding, y),
                    end = Offset(size.width - padding, y),
                    strokeWidth = 1f
                )
            }

            if (apostasOrdenadas.isEmpty()) return@Canvas

            val maxLucro = apostasOrdenadas.maxOf { it.lucro }.toFloat()
            val minLucro = apostasOrdenadas.minOf { it.lucro }.toFloat()
            val range = if ((maxLucro - minLucro) == 0f) 1f else (maxLucro - minLucro)

            // Linha do zero
            if (minLucro < 0 && maxLucro > 0) {
                val zeroY = padding + (maxLucro / range) * height
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(padding, zeroY),
                    end = Offset(size.width - padding, zeroY),
                    strokeWidth = 1.2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        "R$ 0",
                        padding + 4f,
                        zeroY - 8f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(150, 255, 255, 255)
                            textSize = 28f
                            isAntiAlias = true
                        }
                    )
                }
            }

            val points = apostasOrdenadas.mapIndexed { index, it ->
                val x = if (apostasOrdenadas.size == 1) padding + width / 2
                else padding + index * (width / (apostasOrdenadas.size - 1))
                val y = padding + (maxLucro - it.lucro.toFloat()) / range * height
                Offset(x, y)
            }

            if (points.size > 1) {
                // Área preenchida
                val areaPath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val c1 = Offset((prev.x + curr.x) / 2, prev.y)
                        val c2 = Offset((prev.x + curr.x) / 2, curr.y)
                        cubicTo(c1.x, c1.y, c2.x, c2.y, curr.x, curr.y)
                    }
                    lineTo(points.last().x, padding + height)
                    lineTo(points.first().x, padding + height)
                    close()
                }
                drawPath(
                    areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF4F6FFF).copy(alpha = 0.25f),
                            Color(0xFF4F6FFF).copy(alpha = 0.02f)
                        )
                    )
                )

                // Linha do gráfico
                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val c1 = Offset((prev.x + curr.x) / 2, prev.y)
                        val c2 = Offset((prev.x + curr.x) / 2, curr.y)
                        cubicTo(c1.x, c1.y, c2.x, c2.y, curr.x, curr.y)
                    }
                }
                drawPath(linePath, Color(0xFF4F6FFF), style = Stroke(width = 2.5f))
            }

            // Pontos
            points.forEach {
                drawCircle(Color(0xFF4F6FFF), 4f, it)
                drawCircle(Color(0xFF141824), 2f, it)
            }

            // Ponto selecionado
            touchX?.let { xPos ->
                val closest = points.minByOrNull { abs(it.x - xPos) }
                val index = points.indexOf(closest)
                if (closest != null && index != -1) {
                    drawCircle(Color.White, 7f, center = closest)
                    drawCircle(Color(0xFF4F6FFF), 4f, center = closest)
                    selectedInfo = apostasOrdenadas[index] to closest
                }
            }
        }

        // Tooltip
        selectedInfo?.let { (aposta, offset) ->
            val boxWidthDp = 150.dp
            val boxHeightDp = 56.dp
            val marginDp = 8.dp

            val boxWidthPx  = with(density) { boxWidthDp.toPx() }
            val boxHeightPx = with(density) { boxHeightDp.toPx() }
            val marginPx    = with(density) { marginDp.toPx() }

            val xRaw = if (offset.x + boxWidthPx + marginPx > canvasSize.width)
                offset.x - boxWidthPx - marginPx
            else
                offset.x + marginPx

            val yRaw = (offset.y - boxHeightPx - marginPx).coerceAtLeast(marginPx)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            xRaw.toInt().coerceIn(marginPx.toInt(), (canvasSize.width - boxWidthPx - marginPx).toInt()),
                            yRaw.toInt().coerceIn(marginPx.toInt(), (canvasSize.height - boxHeightPx - marginPx).toInt())
                        )
                    }
                    .background(Color(0xFF1E2338), RoundedCornerShape(8.dp))
                    .border(0.5.dp, Color(0xFF4F6FFF).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Column {
                    Text(aposta.data, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
                    Text(
                        "R$ %.2f".format(aposta.lucro),
                        color = when {
                            aposta.lucro > 0 -> Color(0xFF81C784)
                            aposta.lucro < 0 -> Color(0xFFEF9A9A)
                            else             -> Color.White
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (apostas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sem dados para exibir", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
            }
        }
    }
}

// ── Funções auxiliares (mantidas iguais) ──────────────────────────
fun agruparLucroPorDia(apostas: List<Aposta>, dias: Int = 7): List<Aposta> {
    val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val hoje = Calendar.getInstance()
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_MONTH, -(dias - 1))
    val apostasPorData = apostas.groupBy { it.data }
    val diasCompletos = mutableListOf<Aposta>()
    while (!cal.after(hoje)) {
        val dataStr = formato.format(cal.time)
        val lucro = apostasPorData[dataStr]?.sumOf { it.lucro } ?: 0.0
        diasCompletos.add(
            Aposta(descricao = "Lucro Diário", casa = "", valor = 0.0, odds = 0.0, retornoPotencial = 0.0, lucro = lucro, data = dataStr)
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return diasCompletos
}

fun agruparLucroPorMes(apostas: List<Aposta>): List<Aposta> {
    val sdfEntrada = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val sdfSaida = SimpleDateFormat("MM/yyyy", Locale.getDefault())
    return apostas.mapNotNull { aposta ->
        val date = runCatching { sdfEntrada.parse(aposta.data) }.getOrNull()
        date?.let { sdfSaida.format(it) to aposta.lucro }
    }
        .groupBy { it.first }
        .map { (mes, lucros) ->
            Aposta(descricao = "", casa = "", valor = 0.0, odds = 0.0, retornoPotencial = 0.0, lucro = lucros.sumOf { it.second }, data = mes)
        }
        .sortedBy { runCatching { sdfSaida.parse(it.data) }.getOrNull() }
}