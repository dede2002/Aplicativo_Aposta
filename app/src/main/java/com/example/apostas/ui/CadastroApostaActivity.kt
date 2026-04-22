package com.example.apostas.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.DepositoManual
import com.example.apostas.data.LucroTotal
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.components.casasDeAposta
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CadastroApostaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val apostaId = intent.getIntExtra("aposta_id", 0)

        setContent {
            ApostasTheme {
                val scope = rememberCoroutineScope()
                var apostaExistente by remember { mutableStateOf<Aposta?>(null) }

                LaunchedEffect(Unit) {
                    if (apostaId != 0) {
                        val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
                        apostaExistente = withContext(Dispatchers.IO) { dao.getById(apostaId) }
                    }
                }

                if (apostaId == 0 || apostaExistente != null) {
                    FormularioCadastro(apostaExistente) { apostaParaSalvarOriginal ->
                        scope.launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            val apostaDao = db.apostaDao()
                            val depositoDao = db.depositoDao()
                            val saqueDao = db.saqueDao()
                            val lucroDao = db.LucroTotalDao()
                            val lucroDiarioDao = db.LucroDiarioDao()

                            withContext(Dispatchers.IO) {
                                val todasApostas = apostaDao.getAll()
                                val todosDepositos = depositoDao.getAll()
                                val todosSaques = saqueDao.getAll()

                                val novoRetorno = apostaParaSalvarOriginal.valor * apostaParaSalvarOriginal.odds
                                val lucroAntigo = apostaExistente?.lucro ?: 0.0

                                val lucroCorrigido = if (apostaParaSalvarOriginal.id == 0 || lucroAntigo == 0.0) {
                                    0.0
                                } else {
                                    val novoLucroCalculado = novoRetorno - apostaParaSalvarOriginal.valor
                                    if (lucroAntigo < 0) -kotlin.math.abs(novoLucroCalculado) else kotlin.math.abs(novoLucroCalculado)
                                }

                                val apostaParaSalvar = apostaParaSalvarOriginal.copy(
                                    retornoPotencial = novoRetorno,
                                    lucro = lucroCorrigido
                                )

                                if (apostaParaSalvar.id == 0) {
                                    if (todasApostas.size >= 5000) {
                                        apostaDao.getApostaMaisAntiga()?.let { apostaDao.delete(it) }
                                    }

                                    val depositos = todosDepositos.filter { it.casa == apostaParaSalvar.casa }.sumOf { it.valor }
                                    val saques = todosSaques.filter { it.casa == apostaParaSalvar.casa }.sumOf { it.valor }
                                    val lucros = todasApostas.filter { it.casa == apostaParaSalvar.casa && it.lucro != 0.0 }.sumOf { it.lucro }
                                    val valoresApostados = todasApostas.filter { it.casa == apostaParaSalvar.casa && it.lucro == 0.0 }.sumOf { it.valor }
                                    val saldoAtual = depositos + lucros - saques - valoresApostados

                                    apostaDao.insert(apostaParaSalvar)

                                    if (saldoAtual < apostaParaSalvar.valor) {
                                        val valorFaltante = apostaParaSalvar.valor - saldoAtual
                                        depositoDao.inserir(DepositoManual(casa = apostaParaSalvar.casa, valor = valorFaltante))
                                    }
                                } else {
                                    val antiga = apostaExistente!!
                                    val diferencaValor = apostaParaSalvar.valor - antiga.valor

                                    apostaDao.update(apostaParaSalvar)

                                    if (diferencaValor != 0.0) {
                                        depositoDao.inserir(DepositoManual(casa = apostaParaSalvar.casa, valor = diferencaValor))
                                    }

                                    val lucroTotalAtual = lucroDao.get()?.valor ?: 0.0
                                    lucroDao.salvar(LucroTotal(valor = lucroTotalAtual - lucroAntigo + lucroCorrigido))

                                    val lucroDiarioAtual = lucroDiarioDao.get()?.valor ?: 0.0
                                    lucroDiarioDao.salvar(com.example.apostas.data.LucroDiario(valor = lucroDiarioAtual - lucroAntigo + lucroCorrigido))
                                }
                            }

                            finish()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF141824)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4F6FFF))
                    }
                }
            }
        }
    }
}

// ── Cores ─────────────────────────────────────────────────────────
private val BgPrimary   = Color(0xFF141824)
private val BgField     = Color(0xFF1E2338)
private val BorderColor = Color.White.copy(alpha = 0.12f)
private val BorderFocus = Color(0xFF4F6FFF)
private val LabelColor  = Color.White.copy(alpha = 0.45f)
private val AccentBlue  = Color(0xFF4F6FFF)

@Composable
fun FormularioCadastro(
    apostaExistente: Aposta?,
    onSalvar: (Aposta) -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var descricao by rememberSaveable { mutableStateOf("") }
    val casas = remember { mutableStateListOf("", "") }
    var valor by rememberSaveable { mutableStateOf("") }
    var odds by rememberSaveable { mutableStateOf("") }
    var data by rememberSaveable {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
    }

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = BgPrimary, darkIcons = false)
        systemUiController.setNavigationBarColor(color = BgPrimary, darkIcons = false)
    }

    // Preenche campos ao editar
    LaunchedEffect(apostaExistente) {
        apostaExistente?.let {
            descricao = it.descricao
            valor = "%.2f".format(it.valor).replace('.', ',')
            odds = "%.2f".format(it.odds).replace('.', ',')
            data = it.data

            // Separa casas salvas como "Bet365 | Betano | ..."
            val casasSalvas = it.casa.split(" | ").map { c -> c.trim() }.filter { c -> c.isNotBlank() }
            casas.clear()
            casas.addAll(casasSalvas)
            // Garante mínimo de 2 campos
            repeat((2 - casas.size).coerceAtLeast(0)) { casas.add("") }
        }
    }

    val calendar = remember { Calendar.getInstance() }
    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                data = "%02d/%02d/%04d".format(dayOfMonth, month + 1, year)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val isSurebet   = descricao.startsWith("Surebet ✅")
    val isCassino   = descricao.startsWith("Cassino ♠️")
    val ocultarOdds = isSurebet || isCassino

    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = BgField,
        focusedContainerColor = BgField,
        unfocusedBorderColor = BorderColor,
        focusedBorderColor = BorderFocus,
        unfocusedLabelColor = LabelColor,
        focusedLabelColor = AccentBlue,
        unfocusedTextColor = Color.White.copy(alpha = 0.85f),
        focusedTextColor = Color.White,
        cursorColor = AccentBlue,
        unfocusedTrailingIconColor = Color.White.copy(alpha = 0.35f),
        focusedTrailingIconColor = AccentBlue
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Header ───────────────────────────────────────────
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = if (apostaExistente != null) "Editar Aposta" else "Nova Aposta",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Preencha os detalhes abaixo",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }

            // ── Descrição ────────────────────────────────────────
            OutlinedTextField(
                value = descricao,
                onValueChange = { descricao = it },
                label = { Text("Descrição") },
                placeholder = { Text("Ex: Flamengo vencer", color = Color.White.copy(alpha = 0.2f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                singleLine = true
            )

            // ── Casas de Aposta ──────────────────────────────────────────────
            if (isSurebet) {
                // Múltiplas casas para Surebet
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CASAS DE APOSTA",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 11.sp,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            "${casas.size} / 5",
                            color = Color.White.copy(alpha = 0.25f),
                            fontSize = 11.sp
                        )
                    }

                    casas.forEachIndexed { index, casa ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFF4F6FFF).copy(alpha = 0.2f), CircleShape)
                                    .border(0.5.dp, Color(0xFF4F6FFF).copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    color = Color(0xFF7B97FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                CampoCasaDeAposta(
                                    label = "Casa ${index + 1}",
                                    valor = casa,
                                    onValorChange = { casas[index] = it },
                                    sugestoes = casasDeAposta
                                )
                            }
                            if (index >= 2) {
                                IconButton(
                                    onClick = { casas.removeAt(index) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFFB71C1C).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .border(0.5.dp, Color(0xFFB71C1C).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remover casa",
                                        tint = Color(0xFFEF9A9A),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    if (casas.size < 5) {
                        OutlinedButton(
                            onClick = { casas.add("") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF4F6FFF).copy(alpha = 0.08f),
                                contentColor = Color(0xFF7B97FF)
                            ),
                            border = BorderStroke(0.5.dp, Color(0xFF4F6FFF).copy(alpha = 0.35f)),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("+ Adicionar casa", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            } else {
                // Campo único para apostas normais e cassino
                CampoCasaDeAposta(
                    label = "Casa de Aposta",
                    valor = casas[0],
                    onValorChange = { casas[0] = it },
                    sugestoes = casasDeAposta
                )
            }

            // ── Valor e Odds lado a lado ─────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = valor,
                    onValueChange = { valor = it },
                    label = { Text("Valor (R$)") },
                    placeholder = { Text("0,00", color = Color.White.copy(alpha = 0.2f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                if (!ocultarOdds) {
                    OutlinedTextField(
                        value = odds,
                        onValueChange = { odds = it },
                        label = { Text("Odds") },
                        placeholder = { Text("1,00", color = Color.White.copy(alpha = 0.2f)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            // ── Data ─────────────────────────────────────────────
            val interactionSource = remember { MutableInteractionSource() }

            OutlinedTextField(
                value = data,
                onValueChange = {},
                label = { Text("Data") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                readOnly = true,
                trailingIcon = {
                    Icon(Icons.Filled.CalendarToday, contentDescription = "Selecionar data")
                },
                interactionSource = interactionSource,
                singleLine = true
            )

            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interaction ->
                    if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                        datePickerDialog.show()
                    }
                }
            }

            // ── Divisor ──────────────────────────────────────────
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.07f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // ── Botão Salvar ─────────────────────────────────────
            Button(
                onClick = {
                    val valorDouble = valor.replace(',', '.').toDoubleOrNull()
                    val oddsDouble  = odds.replace(',', '.').toDoubleOrNull()
                    val oddsOk = isCassino || isSurebet || (oddsDouble != null && oddsDouble > 0.99)

                    val casasPreenchidas = casas.filter { it.isNotBlank() }
                    if (descricao.isBlank() || casasPreenchidas.isEmpty() || valorDouble == null || valorDouble <= 0.0 || !oddsOk) {
                        scope.launch { snackbarHostState.showSnackbar("Preencha todos os campos corretamente.") }
                        return@Button
                    }

                    val retorno = valorDouble * (oddsDouble ?: 1.0)
                    val aposta = Aposta(
                        id = apostaExistente?.id ?: 0,
                        descricao = descricao.trim(),
                        casa = casasPreenchidas.joinToString(" | "),
                        valor = valorDouble,
                        odds = oddsDouble ?: 1.0,
                        retornoPotencial = retorno,
                        lucro = apostaExistente?.lucro ?: 0.0,
                        data = data
                    )
                    onSalvar(aposta)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text("Salvar Aposta", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            // ── Botões Cassino e Surebet ─────────────────────────
            if (apostaExistente == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(context, CadastroTigrinhoActivity::class.java))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.07f),
                            contentColor = Color.White.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Cassino ♠️", fontSize = 14.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(context, CadastroSureActivity::class.java))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White.copy(alpha = 0.07f),
                            contentColor = Color.White.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Surebet ✅", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}