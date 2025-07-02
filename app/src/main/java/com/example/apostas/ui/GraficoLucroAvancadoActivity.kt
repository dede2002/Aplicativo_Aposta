package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.Aposta
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlin.math.abs
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Size
import com.example.apostas.data.NotaEntity
import androidx.compose.ui.platform.LocalDensity
import android.app.DatePickerDialog
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.OutlinedTextFieldDefaults


fun agruparLucroPorDia(apostas: List<Aposta>): List<Aposta> {
    if (apostas.isEmpty()) return emptyList()

    val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val apostasPorData = apostas.groupBy { it.data }

    val datas = apostasPorData.keys.mapNotNull { runCatching { formato.parse(it) }.getOrNull() }
    val dataInicio = datas.minOrNull()!!
    val dataFim = datas.maxOrNull()!!

    val diasCompletos = mutableListOf<Aposta>()
    val cal = Calendar.getInstance()
    cal.time = dataInicio

    while (!cal.time.after(dataFim)) {
        val dataStr = formato.format(cal.time)
        val lucro = apostasPorData[dataStr]?.sumOf { it.lucro } ?: 0.0

        diasCompletos.add(
            Aposta(
                descricao = "Lucro Diário",
                casa = "",
                valor = 0.0,
                odds = 0.0,
                retornoPotencial = 0.0,
                lucro = lucro,
                data = dataStr
            )
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
            val totalLucro = lucros.sumOf { it.second }
            Aposta(descricao = "", casa = "", valor = 0.0, odds = 0.0, retornoPotencial = 0.0, lucro = totalLucro, data = mes)
        }
        .sortedBy {
            runCatching { sdfSaida.parse(it.data) }.getOrNull()
        }
}





class GraficoLucroAvancadoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var apostas by remember { mutableStateOf(emptyList<Aposta>()) }
    var lucroTotal by remember { mutableDoubleStateOf(0.0) }
    var lucroHoje by remember { mutableDoubleStateOf(0.0) }
    var showDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    var apostasFiltradas by remember { mutableStateOf(emptyList<Aposta>()) }

    val calendar = remember { Calendar.getInstance() }

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
        if (notaSalva != null) {
            noteText = notaSalva.conteudo
        }
    }

    LaunchedEffect(selectedPeriod, selectedDate) {
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val todasApostas = withContext(Dispatchers.IO) {
                db.apostaDao().getAll()
            }

            val cal = Calendar.getInstance()

            apostas = when (selectedPeriod) {
                "1d" -> {
                    val hojeStr = formato.format(Date())
                    apostasFiltradas = todasApostas.filter { it.data == hojeStr }
                    apostasFiltradas
                }
                "1s", "1m" -> {
                    val dataLimite = when (selectedPeriod) {
                        "1s" -> cal.apply { add(Calendar.WEEK_OF_YEAR, -1) }.time
                        "1m" -> cal.apply { add(Calendar.MONTH, -1) }.time
                        else -> cal.time
                    }
                    apostasFiltradas = todasApostas.filter {
                        val data = runCatching { formato.parse(it.data) }.getOrNull()
                        data != null && !data.before(dataLimite)
                    }
                    agruparLucroPorDia(apostasFiltradas)
                }
                "6m" -> {
                    val dataLimite = cal.apply { add(Calendar.MONTH, -6) }.time
                    apostasFiltradas = todasApostas.filter {
                        val data = runCatching { formato.parse(it.data) }.getOrNull()
                        data != null && !data.before(dataLimite)
                    }
                    agruparLucroPorMes(apostasFiltradas)
                }
                "Data" -> {
                    selectedDate?.let {
                        val selectedStr = formato.format(it)
                        apostasFiltradas = todasApostas.filter { aposta -> aposta.data == selectedStr }
                        apostasFiltradas
                    } ?: emptyList()
                }
                else -> emptyList()
            }

            lucroTotal = apostasFiltradas.sumOf { it.lucro }
            lucroHoje = apostasFiltradas.filter { it.data == formato.format(Date()) }.sumOf { it.lucro }
        }
    }

    val apostasParaGrafico = when (selectedPeriod) {
        "1s", "1m" -> agruparLucroPorDia(apostasFiltradas)
        "6m" -> agruparLucroPorMes(apostasFiltradas)
        else -> apostasFiltradas
    }

    val periodoExibido = when {
        selectedPeriod == "1d" -> "Hoje"
        selectedPeriod == "1s" -> "1 semana"
        selectedPeriod == "1m" -> "1 mês"
        selectedPeriod == "6m" -> "6 meses"
        selectedPeriod == "Data" && selectedDate != null -> formato.format(selectedDate!!)
        else -> selectedPeriod.uppercase()
    }

    val tituloGrafico = when (selectedPeriod) {
        "1d" -> "Lucro do dia"
        "1s" -> "Lucro por dia na última semana"
        "1m" -> "Lucro por dia no último mês"
        "6m" -> "Lucro por mês nos últimos 6 meses"
        "Data" -> "Lucro no dia selecionado"
        else -> "Lucro por período"
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E2235))
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Spacer(modifier = Modifier.height(32.dp))

        Text(tituloGrafico, style = MaterialTheme.typography.titleLarge, color = Color.White)

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            GraficoCanvasSuave(apostasParaGrafico)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            periodOptions.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = {
                        selectedPeriod = period
                        if (period == "Data") showDatePicker = true
                    },
                    label = {
                        if (period == "Data" && selectedDate == null) {
                            Icon(Icons.Default.DateRange, contentDescription = "Selecionar Data")
                        } else {
                            Text(
                                if (period == "Data" && selectedDate != null)
                                    formato.format(selectedDate!!)
                                else
                                    period.uppercase()
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            item {
                EstatisticaCard("APOSTAS", apostasFiltradas.size.toString())
            }
            item {
                EstatisticaCard("LUCRO", "R$ %.2f".format(lucroTotal))
            }
            item(span = { GridItemSpan(2) }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(periodoExibido, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Bloco de notas")
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Bloco de notas", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = { Text("Digite suas anotações...", color = Color.Gray) },
                        textStyle = LocalTextStyle.current.copy(color = Color.White),
                        maxLines = 10,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFF1565C0),
                            unfocusedBorderColor = Color(0xFF90A4AE),
                            focusedPlaceholderColor = Color.Gray,
                            unfocusedPlaceholderColor = Color.Gray
                        )
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val dao = AppDatabase.getDatabase(context).notaDao()
                                dao.limparNotas()
                                dao.salvar(NotaEntity(conteudo = noteText))
                            }
                        }
                        showDialog = false
                    }) {
                        Text("Fechar")
                    }
                },
                containerColor = Color(0xFF2C3E50)
            )
        }
    }
}


@Composable
fun EstatisticaCard(label: String, value: String) {
    Card {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}



@Composable
fun GraficoCanvasSuave(apostas: List<Aposta>) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    var selectedInfo by remember { mutableStateOf<Pair<Aposta, Offset>?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val agrupadas = remember(apostas) {
        // Para mais de 7 apostas, assume que é um período longo e agrupa por dia
        if (apostas.size > 7) agruparLucroPorDia(apostas) else apostas
    }

    val apostasOrdenadas = agrupadas.sortedBy {
        runCatching { formato.parse(it.data) }.getOrNull()
    }

    val density = LocalDensity.current

    LaunchedEffect(apostas) {
        touchX = null
        selectedInfo = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier
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

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF))
                ),
                size = size
            )

            val gridCount = 4
            for (i in 0..gridCount) {
                val y = padding + i * (height / gridCount)
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(padding, y),
                    end = Offset(size.width - padding, y),
                    strokeWidth = 1f
                )
            }

            if (apostasOrdenadas.isEmpty()) return@Canvas

            val maxLucro = apostasOrdenadas.maxOf { it.lucro }.toFloat()
            val minLucro = apostasOrdenadas.minOf { it.lucro }.toFloat()
            val range = if ((maxLucro - minLucro) == 0f) 1f else (maxLucro - minLucro)

            if (minLucro < 0 && maxLucro > 0) {
                val zeroY = padding + (maxLucro / range) * height

                drawLine(
                    color = Color.White,
                    start = Offset(padding, zeroY),
                    end = Offset(size.width - padding, zeroY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        "R$ 0",
                        padding,
                        zeroY - 8f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 30f
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
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val control1 = Offset((prev.x + curr.x) / 2, prev.y)
                        val control2 = Offset((prev.x + curr.x) / 2, curr.y)
                        cubicTo(control1.x, control1.y, control2.x, control2.y, curr.x, curr.y)
                    }
                }
                drawPath(path, Color.White, style = Stroke(width = 4f))
            }

            points.forEach {
                drawCircle(Color.Red, 6f, it)
            }

            touchX?.let { xPos ->
                val closest = points.minByOrNull { abs(it.x - xPos) }
                val index = points.indexOf(closest)
                if (closest != null && index != -1) {
                    drawCircle(Color.Yellow, 10f, center = closest)
                    selectedInfo = apostasOrdenadas[index] to closest
                }
            }
        }

        selectedInfo?.let { (aposta, offset) ->
            val boxWidthDp = 160.dp
            val boxHeightDp = 60.dp
            val marginDp = 8.dp

            val boxWidthPx = with(density) { boxWidthDp.toPx() }
            val boxHeightPx = with(density) { boxHeightDp.toPx() }
            val marginPx = with(density) { marginDp.toPx() }

            val xRaw = if (offset.x + boxWidthPx + marginPx > canvasSize.width) {
                offset.x - boxWidthPx - marginPx
            } else {
                offset.x + marginPx
            }

            val yRaw = (offset.y - boxHeightPx - marginPx).coerceAtLeast(marginPx)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            xRaw.toInt().coerceIn(
                                marginPx.toInt(),
                                (canvasSize.width - boxWidthPx - marginPx).toInt()
                            ),
                            yRaw.toInt().coerceIn(
                                marginPx.toInt(),
                                (canvasSize.height - boxHeightPx - marginPx).toInt()
                            )
                        )
                    }
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    Text(aposta.data, color = Color.Black, style = MaterialTheme.typography.labelSmall)
                    Text("R$ %.2f".format(aposta.lucro), color = Color.Black, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (apostas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sem dados para exibir", color = Color.White)
            }
        }
    }
}















