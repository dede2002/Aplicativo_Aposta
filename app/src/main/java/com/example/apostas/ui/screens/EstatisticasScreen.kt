package com.example.apostas.ui.screens

import com.example.apostas.util.parseValorBR
import com.example.apostas.ui.theme.TipsCores
import com.example.apostas.ui.theme.fundoTips
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.example.apostas.ui.theme.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.DepositoManual
import com.example.apostas.data.LucroTotal
import com.example.apostas.data.Saque
import com.example.apostas.data.ajusteDeSaldo
import com.example.apostas.data.saldosDasCasas
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.components.casasDeAposta
import com.example.apostas.ui.ContasActivity
import com.example.apostas.ui.DepositoManualActivity
import com.example.apostas.ui.GraficoLucroAvancadoActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun EstatisticasScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var lucroTotalSalvo by remember { mutableDoubleStateOf(0.0) }
    var lucroEditado by remember { mutableStateOf("") }
    var editandoLucroTotal by remember { mutableStateOf(false) }
    var indefinidas by remember { mutableIntStateOf(0) }
    var casasComSaldo by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var totalSaldoCasas by remember { mutableDoubleStateOf(0.0) }
    var totalDinheiroApostado by remember { mutableDoubleStateOf(0.0) }
    var lucroDiarioSalvo by remember { mutableDoubleStateOf(0.0) }
    var totalLucroSurebet by remember { mutableDoubleStateOf(0.0) }
    var totalLucroCassino by remember { mutableDoubleStateOf(0.0) }
    // Saque em confirmação: casa e saldo disponível
    var saqueEmAndamento by remember { mutableStateOf<Pair<String, Double>?>(null) }
    var valorSaque by remember { mutableStateOf("") }
    var erroSaque by remember { mutableStateOf<String?>(null) }
    // Saldo de todas as casas (inclusive zeradas/negativas), para o "Ajustar saldo"
    var saldosDasCasas by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    // Ajuste em andamento: casa ("" = escolher a casa no diálogo)
    var casaAjuste by remember { mutableStateOf<String?>(null) }
    var casaAjusteDigitada by remember { mutableStateOf("") }
    var saldoReal by remember { mutableStateOf("") }
    var erroAjuste by remember { mutableStateOf<String?>(null) }
    // Contas extras (outros CPFs), para sugerir no "Adicionar saldo"
    var nomesDasContas by remember { mutableStateOf<List<String>>(emptyList()) }

    fun abrirAjuste(casa: String) {
        casaAjuste = casa
        casaAjusteDigitada = ""
        saldoReal = ""
        erroAjuste = null
    }

    fun atualizarDados() {
        scope.launch {
            val lucroDiarioDao = AppDatabase.getDatabase(context).LucroDiarioDao()
            lucroDiarioSalvo = withContext(Dispatchers.IO) { lucroDiarioDao.get()?.valor ?: 0.0 }
            nomesDasContas = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context).contaCasaDao().getAll().map { it.nome }
            }
            carregarDados(
                context,
                { lucroTotalSalvo = it },
                { indefinidas = it },
                { casasComSaldo = it },
                { saldosDasCasas = it },
                { totalSaldoCasas = it },
                { totalDinheiroApostado = it },
                { totalLucroSurebet = it },
                { totalLucroCassino = it }
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) atualizarDados()
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        // Embaixo, espaço para a barra de navegação flutuante
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── Topo ─────────────────────────────────────────────────
        item {
            Column {
                Text("Estatísticas", color = TipsCores.Texto, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("Sua banca e suas casas", color = TipsCores.TextoSecundario, fontSize = 14.sp)
            }
        }

        // ── Banca total e lucro do dia ───────────────────────────
        item {
            CartaoVidro(Modifier.fillMaxWidth(), destaque = TipsCores.Laranja) {
                if (!editandoLucroTotal) {
                    var mostrarSaldo by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) { mostrarSaldo = carregarVisibilidadeSaldo(context) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Banca total", color = TipsCores.TextoSecundario, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            if (mostrarSaldo) {
                                ValorGrande(lucroTotalSalvo, tamanho = 38.sp)
                            } else {
                                Text("R$ ••••••", color = TipsCores.Texto, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        IconeCirculo(
                            if (mostrarSaldo) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            cor = TipsCores.Texto,
                            tamanho = 38.dp,
                            descricao = if (mostrarSaldo) "Ocultar saldo" else "Mostrar saldo",
                            onClick = {
                                mostrarSaldo = !mostrarSaldo
                                salvarVisibilidadeSaldo(context, mostrarSaldo)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        IconeCirculo(
                            Icons.Rounded.Edit,
                            cor = TipsCores.Texto,
                            tamanho = 38.dp,
                            descricao = "Editar banca",
                            onClick = {
                                lucroEditado = "%.2f".format(lucroTotalSalvo).replace(',', '.')
                                editandoLucroTotal = true
                            }
                        )
                    }
                } else {
                    Text("Banca total", color = TipsCores.TextoSecundario, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lucroEditado,
                        onValueChange = { lucroEditado = it },
                        label = { Text("Novo valor da banca") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BotaoTips("Cancelar", { editandoLucroTotal = false }, Modifier.weight(1f), secundario = true)
                        BotaoTips("Salvar", {
                            val novo = parseValorBR(lucroEditado)
                            if (novo != null) {
                                scope.launch {
                                    val dao = AppDatabase.getDatabase(context).LucroTotalDao()
                                    withContext(Dispatchers.IO) { dao.salvar(LucroTotal(valor = novo)) }
                                    lucroTotalSalvo = novo
                                    editandoLucroTotal = false
                                }
                            }
                        }, Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Lucro do dia (contador que o usuário zera quando quiser)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, TipsCores.VidroBorda, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Lucro do dia", color = TipsCores.TextoSecundario, fontSize = 12.sp)
                        ValorGrande(
                            lucroDiarioSalvo,
                            cor = when {
                                lucroDiarioSalvo > 0 -> TipsCores.Verde
                                lucroDiarioSalvo < 0 -> TipsCores.Vermelho
                                else -> TipsCores.Texto
                            },
                            tamanho = 22.sp,
                            mostrarSinal = true
                        )
                    }
                    IconeCirculo(
                        Icons.Rounded.RestartAlt,
                        tamanho = 34.dp,
                        descricao = "Zerar lucro do dia",
                        onClick = {
                            scope.launch {
                                val dao = AppDatabase.getDatabase(context).LucroDiarioDao()
                                withContext(Dispatchers.IO) { dao.salvar(com.example.apostas.data.LucroDiario(valor = 0.0)) }
                                lucroDiarioSalvo = 0.0
                            }
                        }
                    )
                }
            }
        }

        // ── Resumo em quadrinhos ─────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuadroValor("Nas casas", totalSaldoCasas, Icons.Rounded.AccountBalanceWallet, Modifier.weight(1f).fillMaxHeight())
                    QuadroValor(
                        "Em apostas", totalDinheiroApostado, Icons.Rounded.HourglassTop, Modifier.weight(1f).fillMaxHeight(),
                        detalhe = if (indefinidas == 1) "1 em aberto" else "$indefinidas em aberto"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuadroValor("Surebet", totalLucroSurebet, Icons.Rounded.Balance, Modifier.weight(1f), lucro = true)
                    QuadroValor("Cassino", totalLucroCassino, Icons.Rounded.Casino, Modifier.weight(1f), lucro = true)
                }
            }
        }

        // ── Histórico / Depositar ────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotaoTips(
                    "Histórico",
                    { context.startActivity(Intent(context, GraficoLucroAvancadoActivity::class.java)) },
                    Modifier.weight(1f),
                    icone = Icons.Rounded.Insights,
                    secundario = true
                )
                BotaoTips(
                    "Depositar",
                    { context.startActivity(Intent(context, DepositoManualActivity::class.java)) },
                    Modifier.weight(1f),
                    icone = Icons.Rounded.Add
                )
            }
        }

        // ── Contas ───────────────────────────────────────────────
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Contas", color = TipsCores.Texto, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // Contas de outros CPFs em cada casa
                TextButton(onClick = { context.startActivity(Intent(context, ContasActivity::class.java)) }) {
                    Text("Minhas contas", color = TipsCores.LaranjaClaro, fontSize = 13.sp)
                }
                // Para uma casa que não aparece na lista (saldo zerado no app, mas com dinheiro de verdade)
                TextButton(onClick = { abrirAjuste("") }) {
                    Text("Adicionar saldo", color = TipsCores.LaranjaClaro, fontSize = 13.sp)
                }
            }
        }

        if (casasComSaldo.isEmpty()) {
            item {
                Text(
                    "Nenhuma casa com saldo no momento.",
                    color = TipsCores.TextoApagado,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(casasComSaldo.toList(), key = { it.first }) { (casa, saldo) ->
            CartaoVidro(Modifier.fillMaxWidth(), cantos = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconeCirculo(Icons.Rounded.AccountBalanceWallet, tamanho = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(casa, color = TipsCores.Texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        ValorGrande(saldo, tamanho = 20.sp)
                    }
                    BotaoTips("Ajustar", { abrirAjuste(casa) }, secundario = true, compacto = true)
                    Spacer(Modifier.width(8.dp))
                    BotaoTips("Sacar", {
                        valorSaque = "%.2f".format(Locale("pt", "BR"), saldo)
                        erroSaque = null
                        saqueEmAndamento = casa to saldo
                    }, compacto = true)
                }
            }
        }
    }

    // ── Diálogo de saque ─────────────────────────────────────────
    saqueEmAndamento?.let { (casa, saldo) ->
        AlertDialog(
            onDismissRequest = { saqueEmAndamento = null },
            title = { Text("Sacar de $casa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saldo disponível: ${formatarBR(saldo)}")
                    OutlinedTextField(
                        value = valorSaque,
                        onValueChange = { valorSaque = it; erroSaque = null },
                        label = { Text("Valor do saque (R$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = erroSaque != null,
                        supportingText = erroSaque?.let { { Text(it) } }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val valor = parseValorBR(valorSaque)
                        when {
                            valor == null || valor <= 0.0 -> erroSaque = "Informe um valor válido."
                            // Tolerância de 1 centavo para o arredondamento do valor exibido
                            valor > saldo + 0.005 -> erroSaque = "O valor passa do saldo da casa."
                            else -> {
                                saqueEmAndamento = null
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppDatabase.getDatabase(context).saqueDao()
                                            .inserir(Saque(casa = casa, valor = valor.coerceAtMost(saldo)))
                                    }
                                    atualizarDados()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TipsCores.Laranja)
                ) { Text("Confirmar saque") }
            },
            dismissButton = {
                TextButton(onClick = { saqueEmAndamento = null }) { Text("Cancelar") }
            }
        )
    }

    // ── Diálogo de ajustar saldo ─────────────────────────────────
    casaAjuste?.let { casaFixa ->
        val escolhendoCasa = casaFixa.isEmpty()
        // Casa digitada: usa o nome que o app já conhece, se for a mesma casa
        val casa = if (escolhendoCasa) {
            val digitada = casaAjusteDigitada.trim()
            saldosDasCasas.keys.firstOrNull { it.equals(digitada, ignoreCase = true) } ?: digitada
        } else casaFixa
        val saldoNoApp = saldosDasCasas[casa] ?: 0.0

        fun salvarAjuste() {
            val real = parseValorBR(saldoReal)
            when {
                casa.isBlank() -> erroAjuste = "Escolha a casa."
                real == null || real < 0.0 -> erroAjuste = "Informe o saldo (0 se a casa está vazia)."
                else -> {
                    casaAjuste = null
                    val ajuste = ajusteDeSaldo(saldoNoApp, real)
                    if (ajuste != 0.0) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabase.getDatabase(context).depositoDao()
                                    .inserir(DepositoManual(casa = casa, valor = ajuste))
                            }
                            atualizarDados()
                        }
                    }
                }
            }
        }

        // Mesmo visual do resto do app (como o "Em qual casa caiu o lucro?")
        Dialog(
            onDismissRequest = { casaAjuste = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TipsCores.Superficie),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (escolhendoCasa) "Adicionar saldo" else "Ajustar saldo de $casa",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Coloque o saldo atual da casa",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp
                    )

                    if (escolhendoCasa) {
                        CampoCasaDeAposta(
                            valor = casaAjusteDigitada,
                            onValorChange = { casaAjusteDigitada = it; erroAjuste = null },
                            sugestoes = casasDeAposta + nomesDasContas
                        )
                    }

                    if (casa.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TipsCores.CampoOpaco, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Saldo no app", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
                            Text(
                                formatarBR(saldoNoApp.coerceAtLeast(0.0)),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    OutlinedTextField(
                        value = saldoReal,
                        onValueChange = { saldoReal = it; erroAjuste = null },
                        label = { Text("Saldo real (R$)") },
                        placeholder = { Text("0,00", color = Color.White.copy(alpha = 0.2f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = erroAjuste != null,
                        supportingText = erroAjuste?.let { { Text(it, color = TipsCores.Vermelho) } },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = TipsCores.CampoOpaco,
                            focusedContainerColor = TipsCores.CampoOpaco,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedBorderColor = TipsCores.Laranja,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.45f),
                            focusedLabelColor = TipsCores.Laranja,
                            unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                            focusedTextColor = Color.White,
                            cursorColor = TipsCores.Laranja
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { casaAjuste = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Cancelar", fontSize = 14.sp)
                        }
                        Button(
                            onClick = { salvarAjuste() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(containerColor = TipsCores.Laranja),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Salvar", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstatInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.White.copy(alpha = 0.85f)
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

suspend fun carregarDados(
    context: Context,
    setLucroTotalSalvo: (Double) -> Unit,
    setIndefinidas: (Int) -> Unit,
    setCasasComSaldo: (Map<String, Double>) -> Unit,
    setSaldosDasCasas: (Map<String, Double>) -> Unit,
    setTotalSaldoCasas: (Double) -> Unit,
    setTotalDinheiroApostado: (Double) -> Unit,
    setLucroSurebet: (Double) -> Unit,
    setLucroCassino: (Double) -> Unit
) {
    val db = AppDatabase.getDatabase(context)
    val daoLucro = db.LucroTotalDao()
    setLucroTotalSalvo(withContext(Dispatchers.IO) { daoLucro.get()?.valor ?: 0.0 })

    val apostas = withContext(Dispatchers.IO) { db.apostaDao().getAll() }
    setIndefinidas(apostas.count { it.lucro == 0.0 })

    val depositos = withContext(Dispatchers.IO) { db.depositoDao().getAll() }
    val saques = withContext(Dispatchers.IO) { db.saqueDao().getAll() }
    val saldos = saldosDasCasas(apostas, depositos, saques)
    setSaldosDasCasas(saldos)
    // Saldo zerado ou negativo não aparece
    val casasComSaldo = saldos.filterValues { it > 0.0 }
    setCasasComSaldo(casasComSaldo)
    setTotalSaldoCasas(casasComSaldo.values.sum())
    setTotalDinheiroApostado(apostas.filter { it.lucro == 0.0 }.sumOf { it.valor })
    setLucroSurebet(apostas.filter { it.descricao.startsWith("Surebet ✅") }.sumOf { it.lucro })
    setLucroCassino(apostas.filter { it.descricao.startsWith("Cassino ♠️") }.sumOf { it.lucro })
}

fun formatarBR(valor: Double?): String {
    return "R$ " + String.format(Locale("pt", "BR"), "%,.2f", valor ?: 0.0)
}

fun salvarVisibilidadeSaldo(context: Context, visivel: Boolean) {
    val prefs = context.getSharedPreferences("preferencias_apostas", Context.MODE_PRIVATE)
    prefs.edit { putBoolean("mostrar_saldo", visivel) }
}

fun carregarVisibilidadeSaldo(context: Context): Boolean {
    val prefs = context.getSharedPreferences("preferencias_apostas", Context.MODE_PRIVATE)
    return prefs.getBoolean("mostrar_saldo", true)
}

// Quadrinho de vidro com rótulo, ícone e valor grande
@Composable
private fun QuadroValor(
    rotulo: String,
    valor: Double,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    lucro: Boolean = false,
    detalhe: String? = null
) {
    CartaoVidro(modifier, cantos = 20.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Text(rotulo, color = TipsCores.TextoSecundario, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
            IconeCirculo(icone, tamanho = 32.dp)
        }
        Spacer(Modifier.height(8.dp))
        ValorGrande(
            valor,
            cor = when {
                lucro && valor > 0 -> TipsCores.Verde
                lucro && valor < 0 -> TipsCores.Vermelho
                else -> TipsCores.Texto
            },
            tamanho = 22.sp
        )
        detalhe?.let { Text(it, color = TipsCores.TextoApagado, fontSize = 12.sp) }
    }
}
