package com.example.apostas.ui.screens

import com.example.apostas.util.parseValorBR
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.example.apostas.ui.CadastroSureActivity
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.example.apostas.ui.theme.TipsCores
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt


// ---------- FORMATTERS ----------
private val LOCALE_BR = Locale("pt", "BR")

fun formatCurrency(value: Double): String =
    NumberFormat.getCurrencyInstance(LOCALE_BR).format(value)

fun formatPercent(value: Double): String =
    String.format(LOCALE_BR, "%.2f", value)

fun formatOdd(value: Double): String =
    String.format(Locale.US, "%.2f", value)

// ---------- MODELOS ----------
sealed class SurebetResult {
    data class Calculated(
        val odds: List<Double>,
        val suggestedApostas: List<Double>,
        val isFreebet1: Boolean
    ) : SurebetResult()

    data object InvalidInput : SurebetResult()
}

data class DoubleGreenScenario(
    val i: Int,
    val j: Int,
    val retornoDuplo: Double,
    val lucroDuplo: Double,
    val percDuplo: Double
)

data class Metrics(
    val totalInvestido: Double,
    val retornos: List<Double>,        // payout por cenário (1 green)
    val lucros: List<Double>,          // lucro líquido por cenário (1 green)
    val piorLucro: Double,             // menor lucro (pior cenário)
    val piorPerc: Double,              // % do pior cenário
    val isSurebetDeFato: Boolean,      // piorLucro > 0
    val doubleGreen: List<DoubleGreenScenario>
)

private fun calcMetrics(odds: List<Double>, apostas: List<Double>, isFreebet1: Boolean): Metrics {
    // total investido: freebet não conta a aposta 1
    val totalInvestido = if (isFreebet1) apostas.drop(1).sum().coerceAtLeast(0.0)
    else apostas.sum().coerceAtLeast(0.0)

    fun payout(idx: Int): Double {
        val stake = apostas[idx].coerceAtLeast(0.0)
        return if (isFreebet1 && idx == 0) stake * (odds[idx] - 1.0) else stake * odds[idx]
    }

    val retornos = odds.indices.map { payout(it) }
    val lucros = retornos.map { it - totalInvestido }

    val piorLucro = lucros.minOrNull() ?: 0.0
    val piorPerc = if (totalInvestido > 0) (piorLucro / totalInvestido) * 100.0 else 0.0
    val isSurebetDeFato = piorLucro > 0

    val dg = mutableListOf<DoubleGreenScenario>()
    for (i in odds.indices) {
        for (j in (i + 1) until odds.size) {
            val retornoDuplo = payout(i) + payout(j)
            val lucroDuplo = retornoDuplo - totalInvestido
            val percDuplo = if (totalInvestido > 0) (lucroDuplo / totalInvestido) * 100.0 else 0.0
            dg.add(DoubleGreenScenario(i, j, retornoDuplo, lucroDuplo, percDuplo))
        }
    }

    return Metrics(
        totalInvestido = totalInvestido,
        retornos = retornos,
        lucros = lucros,
        piorLucro = piorLucro,
        piorPerc = piorPerc,
        isSurebetDeFato = isSurebetDeFato,
        doubleGreen = dg.sortedByDescending { it.lucroDuplo }
    )
}

// ---------- TELA ----------
@Composable
fun SurebetScreen() {

    val oddInputs = remember { mutableStateListOf("", "", "", "", "") } // até 5 odds
    var numApostas by remember { mutableIntStateOf(2) } // 2..5

    var isFreebet1 by remember { mutableStateOf(false) }

    var valorApostado1 by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SurebetResult?>(null) }

    var showDoubleGreen by remember { mutableStateOf(false) }

    val cardColor = Color.White.copy(alpha = 0.05f)
    val primaryTextColor = TipsCores.Texto
    val buttonColor = TipsCores.Laranja
    val backgroundColor = Color.Transparent

    val qtyButtonColor = buttonColor
    val qtyButtonDisabled = TipsCores.TextoApagado


    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = Color.Transparent, darkIcons = false)
        systemUiController.setNavigationBarColor(color = Color.Black, darkIcons = false)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        containerColor = backgroundColor
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Título como nas outras telas
                Column(Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(top = 12.dp, bottom = 14.dp)) {
                    Text("Calculadora", color = TipsCores.Texto, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text("Quanto apostar em cada casa", color = TipsCores.TextoSecundario, fontSize = 14.sp)
                }
                Card(
                    modifier = Modifier.widthIn(max = 400.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = BorderStroke(1.dp, TipsCores.VidroBorda)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        // Quantidade + / -
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Quantidade de apostas",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TipsCores.TextoSecundario
                                )
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (numApostas > 2) {
                                            numApostas--
                                            oddInputs[numApostas] = "" // limpa a removida
                                            result = null
                                        }
                                    },
                                    enabled = numApostas > 2
                                ) {
                                    Text(
                                        "-",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (numApostas > 2) qtyButtonColor else qtyButtonDisabled
                                    )
                                }

                                Text(
                                    "$numApostas",
                                    modifier = Modifier.width(28.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )

                                IconButton(
                                    onClick = {
                                        if (numApostas < 5) {
                                            numApostas++
                                            result = null
                                        }
                                    },
                                    enabled = numApostas < 5
                                ) {
                                    Text(
                                        "+",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (numApostas < 5) qtyButtonColor else qtyButtonDisabled
                                    )
                                }
                            }
                        }

                        // Odds dinâmicas (sem desalinhamento)
                        val labels = listOf("Odd 1", "Odd 2", "Odd 3", "Odd 4", "Odd 5")

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (i in 0 until minOf(3, numApostas)) {
                                    InputColumn(
                                        modifier = Modifier.weight(1f),
                                        label = labels[i],
                                        value = oddInputs[i],
                                        onValueChange = {
                                            oddInputs[i] = it
                                            result = null
                                        },
                                        placeholder = labels[i]
                                    )
                                }
                                repeat(3 - minOf(3, numApostas)) { Spacer(Modifier.weight(1f)) }
                            }

                            if (numApostas > 3) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    for (i in 3 until numApostas) {
                                        InputColumn(
                                            modifier = Modifier.weight(1f),
                                            label = labels[i],
                                            value = oddInputs[i],
                                            onValueChange = {
                                                oddInputs[i] = it
                                                result = null
                                            },
                                            placeholder = labels[i]
                                        )
                                    }
                                    repeat(5 - numApostas) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }

                        // Freebet SNR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Aposta 1 é FREEBET",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TipsCores.TextoSecundario
                                )
                            )
                            Switch(
                                checked = isFreebet1,
                                onCheckedChange = {
                                    isFreebet1 = it
                                    result = null
                                }
                            )
                        }

                        InputColumn(
                            label = if (isFreebet1) "Valor da Freebet na Odd 1 (R$)" else "Valor Apostado na Odd 1 (R$)",
                            value = valorApostado1,
                            onValueChange = {
                                valorApostado1 = it
                                result = null
                            },
                            placeholder = "Valor"
                        )

                        Button(
                            onClick = {
                                val a1 = parseValorBR(valorApostado1)

                                val oddsRaw: List<Double?> = (0 until numApostas)
                                    .map { idx -> parseValorBR(oddInputs[idx]) }

                                if (
                                    a1 == null || a1 <= 0.0 ||
                                    oddsRaw.any { it == null || it <= 1.0 }
                                ) {
                                    result = SurebetResult.InvalidInput
                                    return@Button
                                }

                                val odds = oddsRaw.map { it!! }

                                val retornoAlvo = if (isFreebet1) a1 * (odds[0] - 1.0) else a1 * odds[0]

                                val apostas = MutableList(odds.size) { 0.0 }
                                apostas[0] = a1
                                for (k in 1 until odds.size) {
                                    apostas[k] = retornoAlvo / odds[k]
                                }

                                // Arredonda para 2 casas decimais (exceto a aposta 1 que o usuário digitou)
                                val apostasArredondadas = apostas.mapIndexed { idx, v ->
                                    if (idx == 0) v else (v * 100.0).roundToInt() / 100.0
                                }

                                result = SurebetResult.Calculated(
                                    odds = odds,
                                    suggestedApostas = apostasArredondadas,
                                    isFreebet1 = isFreebet1
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                        ) {
                            Text(
                                "Calcular",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                AnimatedVisibility(visible = result != null) {
                    result?.let {
                        NewResultCard(
                            result = it,
                            showDoubleGreen = showDoubleGreen,
                            onToggleDoubleGreen = { showDoubleGreen = it }
                        )
                    }
                }

                // Espaço para a barra de navegação flutuante
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

// ---------- UI ----------
@Composable
fun InputColumn(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {

        Box(
            modifier = Modifier.height(36.dp), // altura fixa do label
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(
                    color = TipsCores.TextoSecundario,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }

        CustomTextField(value = value, onValueChange = onValueChange, placeholder = placeholder)
    }
}

@Composable
fun CustomTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(TipsCores.CampoOpaco)
            .border(1.dp, TipsCores.VidroBorda, RoundedCornerShape(16.dp))
            .onFocusChanged { focusState -> isFocused = focusState.isFocused },
        textStyle = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = TipsCores.Texto,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isEmpty() && !isFocused) {
                    Text(text = placeholder, color = TipsCores.TextoApagado, fontSize = 16.sp)
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun EditableStakeRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TipsCores.TextoSecundario)

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TipsCores.Texto,
                textAlign = TextAlign.End
            ),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TipsCores.CampoOpaco),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp)) {
                        if (value.isBlank()) {
                            Text("0,00", color = TipsCores.TextoApagado, fontSize = 14.sp, textAlign = TextAlign.End)
                        }
                        inner()
                    }
                }
            }
        )
    }
}

@Composable
fun NewResultCard(
    result: SurebetResult,
    showDoubleGreen: Boolean,
    onToggleDoubleGreen: (Boolean) -> Unit
) {
    val cardBackground = Color.White.copy(alpha = 0.05f)
    val textColor = TipsCores.Texto

    Card(
        modifier = Modifier.widthIn(max = 400.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = BorderStroke(1.dp, TipsCores.VidroBorda)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (result) {
                is SurebetResult.InvalidInput -> {
                    ResultTitle("Erro", TipsCores.Vermelho, Icons.Default.Warning)
                    Text(
                        "Por favor, preencha os campos corretamente.\n(Use odds > 1 e valor > 0)",
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is SurebetResult.Calculated -> {
                    val odds = result.odds
                    val suggested = result.suggestedApostas
                    val isFreebet1 = result.isFreebet1

                    // Modo manual (reseta quando muda o result)
                    var manualMode by remember(result) { mutableStateOf(false) }
                    val manualInputs = remember(result) {
                        mutableStateListOf<String>().apply {
                            clear()
                            addAll(suggested.map { String.format(LOCALE_BR, "%.2f", it) })
                        }
                    }

                    val apostasAtuais: List<Double> = if (manualMode) {
                        manualInputs.map { parseValorBR(it) ?: 0.0 }
                    } else {
                        suggested
                    }

                    val metrics = remember(odds, apostasAtuais, isFreebet1) {
                        calcMetrics(odds, apostasAtuais, isFreebet1)
                    }

                    val titleColor = if (metrics.isSurebetDeFato) TipsCores.Verde else TipsCores.Vermelho
                    val titleText = if (metrics.isSurebetDeFato) "Surebet Encontrada!" else "Não há Surebet"
                    val icon = if (metrics.isSurebetDeFato) Icons.Default.CheckCircle else Icons.Default.Warning

                    ResultTitle(titleText, titleColor, icon)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = textColor.copy(alpha = 0.2f))

                    if (isFreebet1) {
                        Text(
                            "FREEBET na stake 1 ativa.",
                            color = textColor
                        )
                    }

                    // Modo manual
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Modo manual", fontWeight = FontWeight.Bold, color = textColor)
                        Switch(
                            checked = manualMode,
                            onCheckedChange = { manualMode = it }
                        )
                    }

                    Text(
                        if (!manualMode) "Distribuição sugerida:" else "Ajuste os valores e veja o cálculo em tempo real:",
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    if (!manualMode) {
                        suggested.forEachIndexed { idx, valor ->
                            ResultRow(
                                "Apostar na Odd ${idx + 1} (odds ${formatOdd(odds[idx])}):",
                                formatCurrency(valor),
                                textColor
                            )
                        }
                    } else {
                        suggested.forEachIndexed { idx, _ ->
                            EditableStakeRow(
                                label = "Odd ${idx + 1} (odds ${formatOdd(odds[idx])})",
                                value = manualInputs[idx],
                                onValueChange = { newText ->
                                    val filtered = newText.filter { it.isDigit() || it == ',' || it == '.' }
                                    manualInputs[idx] = filtered
                                }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = textColor.copy(alpha = 0.2f))

                    ResultRow("Total Investido:", formatCurrency(metrics.totalInvestido), textColor)

                    ResultRow(
                        "Pior cenário (1 green):",
                        formatCurrency(metrics.piorLucro),
                        if (metrics.piorLucro >= 0) TipsCores.Verde else TipsCores.Vermelho
                    )

                    val roi = if (result.isFreebet1) {
                        val valorFreebet = apostasAtuais[0]
                        if (valorFreebet > 0) (metrics.piorLucro / valorFreebet) * 100.0 else 0.0
                    } else {
                        if (metrics.totalInvestido > 0) (metrics.piorLucro / metrics.totalInvestido) * 100.0 else 0.0
                    }
                    ResultRow(
                        "ROI da operação:",
                        "${if (roi >= 0) "+" else ""}${formatPercent(roi)}%",
                        if (roi >= 0) TipsCores.Verde else TipsCores.Vermelho
                    )

                    Text("Resultado por cenário (1 green):", fontWeight = FontWeight.Bold, color = textColor)

                    metrics.lucros.forEachIndexed { idx, lucroCenario ->
                        val value = formatCurrency(lucroCenario)

                        ResultRow(
                            "Se bater a Odd ${idx + 1}:",
                            value,
                            if (lucroCenario >= 0) TipsCores.Verde else TipsCores.Vermelho
                        )
                    }

                    DoubleGreenSection(
                        textColor = textColor,
                        scenarios = metrics.doubleGreen,
                        show = showDoubleGreen,
                        onToggle = onToggleDoubleGreen
                    )

                    // Leva a conta para a Nova Surebet: valor e odd de cada casa já preenchidos
                    val context = LocalContext.current
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(context, CadastroSureActivity::class.java)
                                    .putExtra(CadastroSureActivity.EXTRA_CALC_ODDS, odds.toDoubleArray())
                                    .putExtra(CadastroSureActivity.EXTRA_CALC_STAKES, apostasAtuais.toDoubleArray())
                                    .putExtra(CadastroSureActivity.EXTRA_CALC_FREEBET_1, isFreebet1)
                            )
                        },
                        enabled = apostasAtuais.all { it > 0.0 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = TipsCores.Laranja)
                    ) {
                        Text("Salvar como surebet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun DoubleGreenSection(
    textColor: Color,
    scenarios: List<DoubleGreenScenario>,
    show: Boolean,
    onToggle: (Boolean) -> Unit
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = textColor.copy(alpha = 0.2f))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Mostrar duplo green", fontWeight = FontWeight.Bold, color = textColor)
        Switch(
            checked = show,
            onCheckedChange = onToggle
        )
    }

    AnimatedVisibility(visible = show) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Duplo green (todas as combinações):",
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )

            scenarios.forEach { s ->
                val label = "Odd ${s.i + 1} + Odd ${s.j + 1}:"
                val c = if (s.lucroDuplo >= 0) TipsCores.Verde else TipsCores.Vermelho
                ResultRow(label, formatCurrency(s.lucroDuplo), c)
            }
        }
    }
}

@Composable
fun ResultTitle(text: String, color: Color, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, contentDescription = text, tint = color)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun ResultRow(label: String, value: String, contentColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = contentColor)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}
