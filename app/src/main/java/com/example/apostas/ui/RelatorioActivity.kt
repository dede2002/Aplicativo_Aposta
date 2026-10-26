package com.example.apostas.ui

import android.os.Bundle
import com.example.apostas.ui.theme.TipsCores
import com.example.apostas.ui.theme.fundoTips
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.lucroPorMes
import com.example.apostas.data.lucroPorSemana
import com.example.apostas.data.resolvidasNoPeriodo
import com.example.apostas.data.resultadoPorCasa
import com.example.apostas.data.resumoDoPeriodo
import com.example.apostas.ui.screens.formatarBR
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale

// "Relatório" (Estatísticas → 📊 Relatório): lucro por período, resultado de cada casa/conta e lucro por semana ou mês.
class RelatorioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { ApostasTheme { TelaRelatorio() } }
    }
}

private val Fundo = TipsCores.Fundo
private val FundoCard = TipsCores.Superficie
private val FundoCampo = TipsCores.CampoOpaco
private val Azul = TipsCores.Laranja
private val Verde = TipsCores.Verde
private val Vermelho = TipsCores.Vermelho

private enum class Periodo(val rotulo: String) {
    SETE_DIAS("7 dias"), TRINTA_DIAS("30 dias"), ESTE_MES("Este mês"), MES_PASSADO("Mês passado"), TUDO("Tudo");

    // (início, fim) do período; null = sem limite
    fun intervalo(agora: Date = Date()): Pair<Date?, Date?> {
        val c = Calendar.getInstance().apply { time = agora }
        return when (this) {
            SETE_DIAS -> c.apply { add(Calendar.DAY_OF_YEAR, -6) }.time to agora
            TRINTA_DIAS -> c.apply { add(Calendar.DAY_OF_YEAR, -29) }.time to agora
            ESTE_MES -> c.apply { set(Calendar.DAY_OF_MONTH, 1) }.time to agora
            MES_PASSADO -> {
                c.add(Calendar.MONTH, -1)
                c.set(Calendar.DAY_OF_MONTH, 1)
                val inicio = c.time
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
                inicio to c.time
            }
            TUDO -> null to null
        }
    }
}

@Composable
private fun TelaRelatorio() {
    val context = LocalContext.current
    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = Fundo, darkIcons = false)
        systemUiController.setNavigationBarColor(color = Fundo, darkIcons = false)
    }

    var apostas by remember { mutableStateOf<List<Aposta>?>(null) }
    LaunchedEffect(Unit) {
        apostas = withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).apostaDao().getAll() }
    }
    var periodo by remember { mutableStateOf(Periodo.TRINTA_DIAS) }
    var porSemana by remember { mutableStateOf(true) }
    var verTodasAsCasas by remember { mutableStateOf(false) }
    var verTodoOTempo by remember { mutableStateOf(false) }
    var buscaCasa by remember { mutableStateOf("") }

    val (inicio, fim) = periodo.intervalo()
    val resolvidas = remember(apostas, periodo) { resolvidasNoPeriodo(apostas.orEmpty(), inicio, fim) }
    val resumo = remember(resolvidas) { resumoDoPeriodo(resolvidas) }
    val porCasa = remember(resolvidas) { resultadoPorCasa(resolvidas) }
    val porTempo = remember(resolvidas, porSemana) { if (porSemana) lucroPorSemana(resolvidas) else lucroPorMes(resolvidas) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fundoTips()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.padding(bottom = 4.dp)) {
            Text("Relatório", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Apostas resolvidas, pela data da aposta.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }

        // ── Período ──────────────────────────────────────────────
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Periodo.entries.forEach { p ->
                Chip(p.rotulo, selecionado = p == periodo) { periodo = p }
            }
        }

        if (apostas == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Azul)
            }
            return@Column
        }

        // ── Resumo ───────────────────────────────────────────────
        Cartao {
            Text("Lucro no período", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            Text(
                formatarBR(resumo.lucro),
                color = if (resumo.lucro >= 0) Verde else Vermelho,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Linha("Operações", "${resumo.operacoes} · ${resumo.greens} green · ${resumo.reds} red")
            Linha("Surebets", formatarBR(resumo.lucroSurebet), cor(resumo.lucroSurebet))
            Linha("Cassino", formatarBR(resumo.lucroCassino), cor(resumo.lucroCassino))
            Linha("Freebets (extrações)", formatarBR(resumo.lucroFreebets), cor(resumo.lucroFreebets))
            resumo.retornoPercentual?.let { r ->
                Linha(
                    "Retorno sobre o apostado",
                    String.format(Locale("pt", "BR"), "%+.2f%%", r),
                    cor(r)
                )
            }
        }

        // ── Por casa/conta ───────────────────────────────────────
        Titulo("POR CASA E CONTA")
        OutlinedTextField(
            value = buscaCasa,
            onValueChange = { buscaCasa = it },
            placeholder = { Text("Buscar casa ou conta", color = Color.White.copy(alpha = 0.35f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (buscaCasa.isNotEmpty()) {
                { IconButton(onClick = { buscaCasa = "" }) { Icon(Icons.Default.Close, contentDescription = "Limpar busca") } }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = FundoCampo,
                focusedContainerColor = FundoCampo,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedBorderColor = Azul,
                unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                focusedTextColor = Color.White,
                cursorColor = Azul,
                unfocusedLeadingIconColor = Color.White.copy(alpha = 0.35f),
                focusedLeadingIconColor = Azul,
                unfocusedTrailingIconColor = Color.White.copy(alpha = 0.35f),
                focusedTrailingIconColor = Azul
            )
        )
        Cartao {
            val buscando = buscaCasa.isNotBlank()
            val casas = if (buscando) porCasa.filter { it.first.contains(buscaCasa.trim(), ignoreCase = true) } else porCasa
            when {
                porCasa.isEmpty() ->
                    Text("Nenhuma aposta resolvida no período.", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
                casas.isEmpty() ->
                    Text("Nenhuma casa com \"${buscaCasa.trim()}\" no período.", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
            }
            // Buscando: mostra todas as encontradas; sem busca, as 10 maiores e o resto com "Ver todas"
            val mostrar = if (buscando || verTodasAsCasas) casas else casas.take(10)
            mostrar.forEach { (casa, valor) -> Linha(casa, formatarBR(valor), cor(valor)) }
            if (!buscando && casas.size > 10) {
                BotaoVerTodas(verTodasAsCasas, "Ver todas (${casas.size})", "Mostrar só as 10 maiores") { verTodasAsCasas = !verTodasAsCasas }
            }
        }

        // ── Por semana / mês ─────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Titulo(if (porSemana) "POR SEMANA" else "POR MÊS", Modifier.weight(1f))
            Chip("Semana", selecionado = porSemana) { porSemana = true }
            Chip("Mês", selecionado = !porSemana) { porSemana = false }
        }
        Cartao {
            if (porTempo.isEmpty()) {
                Text("Nenhuma aposta resolvida no período.", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
            }
            val maior = porTempo.maxOfOrNull { kotlin.math.abs(it.second) } ?: 0.0
            // Os 10 mais recentes; o resto com "Ver todos"
            (if (verTodoOTempo) porTempo else porTempo.take(10)).forEach { (rotulo, valor) ->
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Linha(rotulo, formatarBR(valor), cor(valor))
                    // Barra proporcional ao maior valor do período
                    val fracao = if (maior > 0) (kotlin.math.abs(valor) / maior).toFloat().coerceIn(0.02f, 1f) else 0f
                    Box(
                        Modifier
                            .fillMaxWidth(fracao)
                            .height(4.dp)
                            .background(cor(valor).copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    )
                }
            }
            if (porTempo.size > 10) {
                BotaoVerTodas(verTodoOTempo, "Ver todos (${porTempo.size})", "Mostrar só os 10 mais recentes") { verTodoOTempo = !verTodoOTempo }
            }
        }
    }
}

private fun cor(valor: Double) = when {
    valor > 0 -> Verde
    valor < 0 -> Vermelho
    else -> Color.White.copy(alpha = 0.85f)
}

@Composable
private fun Chip(texto: String, selecionado: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selecionado,
        onClick = onClick,
        shape = RoundedCornerShape(50.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Azul,
            selectedLabelColor = Color.Black,
            containerColor = Color.White.copy(alpha = 0.06f),
            labelColor = Color.White.copy(alpha = 0.55f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selecionado,
            selectedBorderColor = Azul,
            borderColor = Color.White.copy(alpha = 0.12f),
            borderWidth = 0.5.dp,
            selectedBorderWidth = 0.5.dp
        ),
        label = { Text(texto, fontSize = 12.sp) }
    )
}

@Composable
private fun BotaoVerTodas(abertas: Boolean, textoAbrir: String, textoFechar: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text(if (abertas) textoFechar else textoAbrir, color = Azul, fontSize = 12.sp)
    }
}

@Composable
private fun Titulo(texto: String, modifier: Modifier = Modifier) {
    Text(texto, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, letterSpacing = 0.6.sp, modifier = modifier.padding(top = 4.dp))
}

@Composable
private fun Cartao(conteudo: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = FundoCard),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = conteudo)
    }
}

@Composable
private fun Linha(rotulo: String, valor: String, corValor: Color = Color.White.copy(alpha = 0.85f)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(12.dp))
        Text(valor, color = corValor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
