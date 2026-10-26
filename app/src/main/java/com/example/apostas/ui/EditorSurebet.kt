package com.example.apostas.ui

import android.app.DatePickerDialog
import com.example.apostas.ui.theme.TipsCores
import com.example.apostas.ui.theme.fundoTips
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.ContaCasa
import com.example.apostas.data.Perna
import com.example.apostas.data.casaDaConta
import com.example.apostas.data.contasDaCasa
import com.example.apostas.data.lucroAposEdicao
import com.example.apostas.data.pernas
import com.example.apostas.data.pernasCompletas
import com.example.apostas.data.pernasParaJson
import com.example.apostas.util.parseValorBR
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

// Edição de uma surebet em aberto que tem as pernas (o que foi apostado em cada casa):
// conta de cada casa (outros CPFs), Back/Lay, valor e odd. "+ Adicionar conta" divide o mesmo palpite
// em outra conta (mesmo grupo: ganham juntas). O lucro é recalculado a partir das pernas.

private val Fundo = TipsCores.Fundo
private val FundoCard = TipsCores.Superficie
private val FundoCampo = TipsCores.CampoOpaco
private val Azul = TipsCores.Laranja
private val AzulClaro = TipsCores.LaranjaClaro
private val Vermelho = TipsCores.Vermelho

// Uma perna em edição (texto como o usuário digita)
private data class LinhaPerna(
    val conta: String,
    val aposta: String?,
    val tipo: String,
    val stake: String,
    val odd: String,
    val grupo: Int?,
    val print: String?,
    // Freebet usada nesta casa (mantida ao editar)
    val freebetId: Int? = null,
    val custoFreebet: Double? = null
)

private fun formatarCampo(valor: Double?): String =
    valor?.let { String.format(Locale("pt", "BR"), "%.2f", it) } ?: ""

private fun LinhaPerna.paraPerna() = Perna(
    casa = conta,
    aposta = aposta,
    tipo = tipo,
    odd = parseValorBR(odd),
    stake = parseValorBR(stake),
    print = print,
    grupo = grupo,
    freebetId = freebetId,
    custoFreebet = custoFreebet
)

// Ao girar a tela, guarda as linhas como lista de textos
private val SaverLinhas = listSaver<SnapshotStateList<LinhaPerna>, String>(
    save = { lista ->
        lista.flatMap {
            listOf(
                it.conta, it.aposta ?: "", it.tipo, it.stake, it.odd, it.grupo?.toString() ?: "", it.print ?: "",
                it.freebetId?.toString() ?: "", it.custoFreebet?.toString() ?: ""
            )
        }
    },
    restore = { campos ->
        campos.chunked(9).map { c ->
            LinhaPerna(
                c[0], c[1].ifBlank { null }, c[2], c[3], c[4], c[5].toIntOrNull(), c[6].ifBlank { null },
                c[7].toIntOrNull(), c[8].toDoubleOrNull()
            )
        }.toMutableStateList()
    }
)

@Composable
fun EditorSurebet(aposta: Aposta, onSalvar: (Aposta) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = Fundo, darkIcons = false)
        systemUiController.setNavigationBarColor(color = Fundo, darkIcons = false)
    }

    val pernasOriginais = remember(aposta) { aposta.pernas }
    var descricao by rememberSaveable { mutableStateOf(aposta.descricao) }
    var data by rememberSaveable { mutableStateOf(aposta.data) }
    val linhas = rememberSaveable(saver = SaverLinhas) {
        pernasOriginais.map {
            LinhaPerna(
                it.casa, it.aposta, it.tipo ?: "Back", formatarCampo(it.stake), formatarCampo(it.odd), it.grupo, it.print,
                it.freebetId, it.custoFreebet
            )
        }.toMutableStateList()
    }
    // Null = lucro calculado pelas pernas; texto = o usuário digitou por cima
    var lucroDigitado by rememberSaveable { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }
    var mostrarData by remember { mutableStateOf(false) }

    var contas by remember { mutableStateOf<List<ContaCasa>>(emptyList()) }
    fun recarregarContas() {
        scope.launch {
            contas = withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).contaCasaDao().getAll() }
        }
    }
    LaunchedEffect(Unit) { recarregarContas() }
    // Nova conta pedida na escolha da conta da linha i
    var novaContaDaLinha by remember { mutableStateOf<Int?>(null) }

    val pernasAtuais = linhas.map { it.paraPerna() }
    val lucroCalculado = lucroAposEdicao(aposta.valor, pernasOriginais, pernasAtuais)
    val textoLucro = lucroDigitado ?: formatarCampo(lucroCalculado)

    val campo = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = FundoCampo,
        focusedContainerColor = FundoCampo,
        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
        focusedBorderColor = Azul,
        unfocusedLabelColor = Color.White.copy(alpha = 0.45f),
        focusedLabelColor = Azul,
        unfocusedTextColor = Color.White.copy(alpha = 0.85f),
        focusedTextColor = Color.White,
        cursorColor = Azul,
        unfocusedTrailingIconColor = Color.White.copy(alpha = 0.35f),
        focusedTrailingIconColor = Azul
    )

    // Abre o calendário uma vez por pedido (mesmo padrão das outras telas)
    LaunchedEffect(mostrarData) {
        if (mostrarData) {
            val cal = Calendar.getInstance()
            runCatching {
                val (d, m, a) = data.split("/").map { it.toInt() }
                cal.set(a, m - 1, d)
            }
            DatePickerDialog(
                context,
                { _, ano, mes, dia -> data = "%02d/%02d/%04d".format(dia, mes + 1, ano) },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).apply { setOnDismissListener { mostrarData = false } }.show()
        }
    }

    Scaffold(Modifier.fundoTips(), snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.padding(bottom = 4.dp)) {
                Text("Editar Surebet", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Valor, odd e conta de cada casa",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }

            OutlinedTextField(
                value = descricao,
                onValueChange = { descricao = it },
                label = { Text("Descrição") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = campo,
                singleLine = true
            )

            Text("CASAS E CONTAS", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, letterSpacing = 0.6.sp)

            linhas.forEachIndexed { i, linha ->
                CardPerna(
                    linha = linha,
                    contasDisponiveis = contasDaCasa(linha.conta, contas),
                    podeRemover = linhas.size > 2,
                    campo = campo,
                    onMudar = { linhas[i] = it },
                    onRemover = { linhas.removeAt(i) },
                    onNovaConta = { novaContaDaLinha = i },
                    onAdicionarConta = {
                        // Mesmo palpite em outra conta: as duas linhas passam a ter o mesmo grupo
                        val grupo = linha.grupo ?: ((linhas.mapNotNull { it.grupo }.maxOrNull() ?: 0) + 1)
                        linhas[i] = linha.copy(grupo = grupo)
                        val usadas = linhas.map { it.conta }.toSet()
                        val proxima = contasDaCasa(linha.conta, contas).firstOrNull { it !in usadas } ?: casaDaConta(linha.conta)
                        // A conta nova usa dinheiro normal (a freebet fica só na linha original)
                        linhas.add(i + 1, linha.copy(conta = proxima, stake = "", grupo = grupo, freebetId = null, custoFreebet = null))
                    }
                )
            }

            // ── Lucro ────────────────────────────────────────────
            OutlinedTextField(
                value = textoLucro,
                onValueChange = { lucroDigitado = it },
                label = { Text("Lucro garantido (R$)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = campo,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                supportingText = {
                    Text(
                        if (lucroDigitado == null) "Recalculado pelas casas acima. Pode digitar outro valor."
                        else "Valor digitado por você.",
                        color = Color.White.copy(alpha = 0.4f)
                    )
                },
                trailingIcon = if (lucroDigitado != null) {
                    {
                        TextButton(onClick = { lucroDigitado = null }) {
                            Text("Recalcular", color = AzulClaro, fontSize = 12.sp)
                        }
                    }
                } else null
            )

            // ── Data ─────────────────────────────────────────────
            val toqueData = remember { MutableInteractionSource() }
            OutlinedTextField(
                value = data,
                onValueChange = {},
                label = { Text("Data") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = campo,
                readOnly = true,
                trailingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Selecionar data") },
                interactionSource = toqueData,
                singleLine = true
            )
            LaunchedEffect(toqueData) {
                toqueData.interactions.collect { if (it is PressInteraction.Release) mostrarData = true }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            Button(
                onClick = {
                    val pernas = linhas.map { it.paraPerna() }
                    val lucro = parseValorBR(textoLucro)
                    val erro = when {
                        descricao.isBlank() -> "Preencha a descrição."
                        linhas.any { it.conta.isBlank() } -> "Escolha a conta de todas as casas."
                        linhas.map { it.conta }.toSet().size < linhas.size -> "A mesma conta aparece duas vezes."
                        pernas.any { p ->
                            val algum = p.stake != null || p.odd != null
                            algum && ((p.stake ?: 0.0) <= 0.0 || (p.odd ?: 0.0) <= 1.0)
                        } -> "Preencha valor e odd (maior que 1) de cada casa."
                        lucro == null -> "Informe o lucro."
                        else -> null
                    }
                    if (erro != null) {
                        scope.launch { snackbar.showSnackbar(erro) }
                        return@Button
                    }
                    salvando = true
                    onSalvar(
                        aposta.copy(
                            descricao = descricao.trim(),
                            casa = pernas.map { it.casa }.distinct().joinToString(" | "),
                            // Em surebets, o valor é o lucro esperado
                            valor = lucro!!,
                            retornoPotencial = lucro,
                            odds = 1.0,
                            data = data,
                            pernasJson = pernasParaJson(pernas),
                            stakesNasCasas = pernasCompletas(pernas)
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Azul),
                contentPadding = PaddingValues(vertical = 14.dp),
                enabled = !salvando
            ) {
                Text("Salvar Surebet", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    novaContaDaLinha?.let { i ->
        DialogoNovaConta(
            casa = casaDaConta(linhas[i].conta),
            onCancelar = { novaContaDaLinha = null },
            onCriar = { apelido ->
                val conta = ContaCasa(casa = casaDaConta(linhas[i].conta), apelido = apelido.trim())
                linhas[i] = linhas[i].copy(conta = conta.nome)
                novaContaDaLinha = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val dao = AppDatabase.getDatabase(context).contaCasaDao()
                        if (dao.getAll().none { it.nome.equals(conta.nome, ignoreCase = true) }) dao.inserir(conta)
                    }
                    recarregarContas()
                }
            }
        )
    }
}

@Composable
private fun CardPerna(
    linha: LinhaPerna,
    contasDisponiveis: List<String>,
    podeRemover: Boolean,
    campo: TextFieldColors,
    onMudar: (LinhaPerna) -> Unit,
    onRemover: () -> Unit,
    onNovaConta: () -> Unit,
    onAdicionarConta: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FundoCard),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Conta (principal ou outro CPF)
                var aberto by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { aberto = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = FundoCampo, contentColor = Color.White),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(linha.conta, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Escolher conta", tint = Color.White.copy(alpha = 0.5f))
                    }
                    DropdownMenu(
                        expanded = aberto,
                        onDismissRequest = { aberto = false },
                        modifier = Modifier.background(FundoCampo)
                    ) {
                        contasDisponiveis.forEach { conta ->
                            DropdownMenuItem(
                                text = { Text(conta, color = Color.White) },
                                onClick = { aberto = false; onMudar(linha.copy(conta = conta)) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("+ Nova conta…", color = AzulClaro) },
                            onClick = { aberto = false; onNovaConta() }
                        )
                    }
                }

                // Back / Lay
                val lay = linha.tipo.equals("Lay", ignoreCase = true)
                val corTipo = if (lay) Vermelho else AzulClaro
                OutlinedButton(
                    onClick = { onMudar(linha.copy(tipo = if (lay) "Back" else "Lay")) },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = corTipo.copy(alpha = 0.08f), contentColor = corTipo),
                    border = BorderStroke(0.5.dp, corTipo.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(if (lay) "Lay" else "Back", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                if (podeRemover) {
                    IconButton(
                        onClick = onRemover,
                        modifier = Modifier
                            .size(40.dp)
                            .background(TipsCores.Vermelho.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = Vermelho, modifier = Modifier.size(14.dp))
                    }
                }
            }

            linha.aposta?.let {
                Text(it, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            }
            if (linha.freebetId != null) {
                Text(
                    "Freebet · perdeu " + String.format(Locale("pt", "BR"), "R$ %,.2f", linha.custoFreebet ?: 0.0) + " na missão",
                    color = TipsCores.LaranjaClaro,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = linha.stake,
                    onValueChange = { onMudar(linha.copy(stake = it)) },
                    label = { Text("Valor (R$)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = campo,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = linha.odd,
                    onValueChange = { onMudar(linha.copy(odd = it)) },
                    label = { Text("Odd") },
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(16.dp),
                    colors = campo,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            TextButton(onClick = onAdicionarConta, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("+ Adicionar conta (mesmo palpite)", color = AzulClaro, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DialogoNovaConta(casa: String, onCancelar: () -> Unit, onCriar: (String) -> Unit) {
    var apelido by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FundoCard),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nova conta na $casa", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("De quem é a conta? Ex.: Irmã, Namorada", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                OutlinedTextField(
                    value = apelido,
                    onValueChange = { apelido = it.replace("(", "").replace(")", "") },
                    label = { Text("Nome da conta") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = FundoCampo,
                        focusedContainerColor = FundoCampo,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedBorderColor = Azul,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.45f),
                        focusedLabelColor = Azul,
                        unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                        focusedTextColor = Color.White,
                        cursorColor = Azul
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onCancelar,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Color.White.copy(alpha = 0.6f)),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) { Text("Cancelar", fontSize = 14.sp) }
                    Button(
                        onClick = { if (apelido.isNotBlank()) onCriar(apelido) },
                        enabled = apelido.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        // Desativado (sem nome) continua visível no fundo escuro
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Azul,
                            disabledContainerColor = Azul.copy(alpha = 0.25f),
                            disabledContentColor = Color.White.copy(alpha = 0.45f)
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) { Text("Criar", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}
