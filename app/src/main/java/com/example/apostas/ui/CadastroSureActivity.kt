package com.example.apostas.ui

import com.example.apostas.util.parseValorBR
import android.app.DatePickerDialog
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.apostas.MainActivity
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.Freebet
import com.example.apostas.data.Perna
import com.example.apostas.data.custoDasFreebets
import com.example.apostas.data.lucroLiquidoPelasPernas
import com.example.apostas.data.pernasParaJson
import com.example.apostas.data.salvarSurebet
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.components.casasDeAposta
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CadastroSureActivity : ComponentActivity() {
    companion object {
        // Vindo do "Usar" da tela Freebets: a 1ª casa já começa com essa freebet
        const val EXTRA_FREEBET_ID = "freebet_id"
        // Vindo do "Salvar como surebet" da calculadora: odd e valor de cada casa, e se a 1ª é freebet
        const val EXTRA_CALC_ODDS = "calc_odds"
        const val EXTRA_CALC_STAKES = "calc_stakes"
        const val EXTRA_CALC_FREEBET_1 = "calc_freebet_1"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val freebetInicial = intent.getIntExtra(EXTRA_FREEBET_ID, 0)
        val oddsDaCalculadora = intent.getDoubleArrayExtra(EXTRA_CALC_ODDS)
        val stakesDaCalculadora = intent.getDoubleArrayExtra(EXTRA_CALC_STAKES)
        val freebetNaCalculadora = intent.getBooleanExtra(EXTRA_CALC_FREEBET_1, false)

        setContent {
            ApostasTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                var lucroDigitado by rememberSaveable { mutableStateOf("") }
                var descricaoExtra by rememberSaveable { mutableStateOf("") }
                var salvando by remember { mutableStateOf(false) }
                var data by rememberSaveable {
                    mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
                }

                // Lista de casas — começa com 2 campos vazios
                // rememberSaveable: as casas digitadas sobrevivem a girar a tela
                val casas = rememberSaveable(
                    saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
                ) { mutableStateListOf("", "") }
                // Stake, odd e tipo (Back/Lay) de cada casa, na mesma ordem de `casas`.
                // Servem para o saldo de cada casa receber o que aconteceu nela quando a surebet bater.
                val stakes = rememberSaveable(
                    saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
                ) { mutableStateListOf("", "") }
                val odds = rememberSaveable(
                    saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
                ) { mutableStateListOf("", "") }
                val tipos = rememberSaveable(
                    saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
                ) { mutableStateListOf("Back", "Back") }
                val snackbarHostState = remember { SnackbarHostState() }
                // Freebet usada em cada casa (id como texto; "" = dinheiro normal), na mesma ordem de `casas`
                val freebetsDasCasas = rememberSaveable(
                    saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
                ) { mutableStateListOf("", "") }
                var freebetsDisponiveis by remember { mutableStateOf<List<Freebet>>(emptyList()) }
                // Casa cuja freebet está sendo escolhida
                var escolhendoFreebetDa by remember { mutableStateOf<Int?>(null) }
                var freebetInicialAplicada by rememberSaveable { mutableStateOf(false) }

                fun aplicarFreebet(i: Int, fb: Freebet) {
                    casas[i] = fb.casa
                    stakes[i] = String.format(Locale("pt", "BR"), "%.2f", fb.valor)
                    tipos[i] = "Back"
                    freebetsDasCasas[i] = fb.id.toString()
                }

                LaunchedEffect(Unit) {
                    freebetsDisponiveis = withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(context).freebetDao().disponiveis()
                    }
                    if (freebetInicialAplicada) return@LaunchedEffect
                    freebetInicialAplicada = true
                    if (freebetInicial != 0) {
                        freebetsDisponiveis.firstOrNull { it.id == freebetInicial }?.let { aplicarFreebet(0, it) }
                    }
                    // Da calculadora: uma casa por odd, com o valor e a odd calculados (só falta dizer as casas)
                    if (oddsDaCalculadora != null && stakesDaCalculadora != null) {
                        val n = minOf(oddsDaCalculadora.size, stakesDaCalculadora.size, 5)
                        while (casas.size < n) {
                            casas.add(""); stakes.add(""); odds.add(""); tipos.add("Back"); freebetsDasCasas.add("")
                        }
                        for (i in 0 until n) {
                            stakes[i] = String.format(Locale("pt", "BR"), "%.2f", stakesDaCalculadora[i])
                            odds[i] = String.format(Locale("pt", "BR"), "%.2f", oddsDaCalculadora[i])
                        }
                        // Aposta 1 era freebet: escolher qual das freebets disponíveis
                        if (freebetNaCalculadora) {
                            if (freebetsDisponiveis.isNotEmpty()) escolhendoFreebetDa = 0
                            else snackbarHostState.showSnackbar("Nenhuma freebet disponível. Cadastre em \"Usar freebet\".")
                        }
                    }
                }
                fun freebetDa(i: Int): Freebet? =
                    freebetsDasCasas.getOrNull(i)?.toIntOrNull()?.let { id -> freebetsDisponiveis.firstOrNull { it.id == id } }

                // Contas de outros CPFs ("Bet365 (Irmã)") aparecem junto das casas nas sugestões
                var nomesDasContas by remember { mutableStateOf<List<String>>(emptyList()) }
                LaunchedEffect(Unit) {
                    nomesDasContas = withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(context).contaCasaDao().getAll().map { it.nome }
                    }
                }

                val calendar = remember { Calendar.getInstance() }
                val datePickerDialog = remember {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            data = "%02d/%02d/%04d".format(dayOfMonth, month + 1, year)
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                }

                val systemUiController = rememberSystemUiController()
                SideEffect {
                    systemUiController.setSystemBarsColor(color = Color(0xFF141824), darkIcons = false)
                    systemUiController.setNavigationBarColor(color = Color(0xFF141824), darkIcons = false)
                }

                // Cores
                val bgField     = Color(0xFF1E2338)
                val borderColor = Color.White.copy(alpha = 0.12f)
                val borderFocus = Color(0xFF4F6FFF)
                val labelColor  = Color.White.copy(alpha = 0.45f)


                val fieldColors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = bgField,
                    focusedContainerColor = bgField,
                    unfocusedBorderColor = borderColor,
                    focusedBorderColor = borderFocus,
                    unfocusedLabelColor = labelColor,
                    focusedLabelColor = borderFocus,
                    unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                    focusedTextColor = Color.White,
                    cursorColor = borderFocus,
                    unfocusedTrailingIconColor = Color.White.copy(alpha = 0.35f),
                    focusedTrailingIconColor = borderFocus
                )

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = Color(0xFF141824)
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

                        // ── Header ───────────────────────────────────────
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                "Nova Surebet ✅",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Preencha os detalhes abaixo",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp
                            )
                        }

                        // ── Lucro ────────────────────────────────────────
                        // Lucro calculado pelas casas (com freebet: extração − quanto perdeu na missão)
                        val pernasDigitadas = casas.indices.map { i ->
                            Perna(
                                casa = casas[i],
                                tipo = tipos[i],
                                odd = parseValorBR(odds[i]),
                                stake = parseValorBR(stakes[i]),
                                freebetId = freebetDa(i)?.id,
                                custoFreebet = freebetDa(i)?.custo
                            )
                        }.filter { it.casa.isNotBlank() }
                        val lucroPelasCasas = lucroLiquidoPelasPernas(pernasDigitadas)
                        val custoFreebets = custoDasFreebets(pernasDigitadas)
                        OutlinedTextField(
                            value = lucroDigitado,
                            onValueChange = { lucroDigitado = it },
                            label = { Text("Lucro (R$)") },
                            placeholder = { Text("0,00", color = Color.White.copy(alpha = 0.2f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            supportingText = lucroPelasCasas?.let { calc ->
                                {
                                    Text(
                                        "Pelas casas: " + String.format(Locale("pt", "BR"), "R$ %,.2f", calc) +
                                                if (custoFreebets > 0) " (já desconta " +
                                                        String.format(Locale("pt", "BR"), "R$ %,.2f", custoFreebets) + " da missão)" else "",
                                        color = Color.White.copy(alpha = 0.45f)
                                    )
                                }
                            },
                            trailingIcon = lucroPelasCasas?.let { calc ->
                                {
                                    TextButton(onClick = { lucroDigitado = String.format(Locale("pt", "BR"), "%.2f", calc) }) {
                                        Text("Usar", color = Color(0xFF7B97FF), fontSize = 12.sp)
                                    }
                                }
                            }
                        )

                        // ── Descrição extra ──────────────────────────────
                        OutlinedTextField(
                            value = descricaoExtra,
                            onValueChange = { descricaoExtra = it },
                            label = { Text("Descrição (opcional)") },
                            placeholder = { Text("Ex: United vencer primeiro tempo", color = Color.White.copy(alpha = 0.2f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            singleLine = true
                        )

                        // ── Casas de Aposta ──────────────────────────────
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Header da seção
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

                            // Campos de casa
                            casas.forEachIndexed { index, casa ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Número da casa
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(
                                                Color(0xFF4F6FFF).copy(alpha = 0.2f),
                                                CircleShape
                                            )
                                            .border(
                                                0.5.dp,
                                                Color(0xFF4F6FFF).copy(alpha = 0.4f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            color = Color(0xFF7B97FF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Campo da casa
                                    Box(modifier = Modifier.weight(1f)) {
                                        CampoCasaDeAposta(
                                            label = "Casa ${index + 1}",
                                            valor = casa,
                                            onValorChange = { casas[index] = it },
                                            sugestoes = casasDeAposta + nomesDasContas
                                        )
                                    }

                                    // Botão remover (só aparece do índice 2 em diante)
                                    if (index >= 2) {
                                        IconButton(
                                            onClick = {
                                                casas.removeAt(index)
                                                stakes.removeAt(index)
                                                odds.removeAt(index)
                                                tipos.removeAt(index)
                                                freebetsDasCasas.removeAt(index)
                                            },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(
                                                    Color(0xFFB71C1C).copy(alpha = 0.15f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    0.5.dp,
                                                    Color(0xFFB71C1C).copy(alpha = 0.3f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remover casa",
                                                tint = Color(0xFFEF9A9A),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(28.dp))
                                    }
                                }

                                // Stake, odd e Back/Lay da casa
                                Row(
                                    modifier = Modifier.padding(start = 32.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val comFreebet = freebetDa(index) != null
                                    OutlinedTextField(
                                        value = stakes[index],
                                        onValueChange = { stakes[index] = it },
                                        // Com freebet, o valor é o da freebet
                                        readOnly = comFreebet,
                                        label = { Text(if (comFreebet) "Freebet (R$)" else "Stake (R$)") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = fieldColors,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = odds[index],
                                        onValueChange = { odds[index] = it },
                                        label = { Text("Odd") },
                                        modifier = Modifier.weight(0.8f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = fieldColors,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    val lay = tipos[index] == "Lay"
                                    val corTipo = if (lay) Color(0xFFEF9A9A) else Color(0xFF7B97FF)
                                    OutlinedButton(
                                        onClick = { if (!comFreebet) tipos[index] = if (lay) "Back" else "Lay" },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = corTipo.copy(alpha = 0.08f),
                                            contentColor = corTipo
                                        ),
                                        border = BorderStroke(0.5.dp, corTipo.copy(alpha = 0.4f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.padding(top = 6.dp).padding(end = 36.dp)
                                    ) {
                                        Text(tipos[index], fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                // Freebet desta casa
                                val fb = freebetDa(index)
                                if (fb != null) {
                                    Row(
                                        modifier = Modifier.padding(start = 32.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "🎁 Freebet ${String.format(Locale("pt", "BR"), "R$ %,.2f", fb.valor)} · perdeu " +
                                                    String.format(Locale("pt", "BR"), "R$ %,.2f", fb.custo),
                                            color = Color(0xFFFFB74D),
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { freebetsDasCasas[index] = ""; stakes[index] = "" }) {
                                            Text("Tirar", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                        }
                                    }
                                } else if (freebetsDisponiveis.any { d -> freebetsDasCasas.none { it == d.id.toString() } }) {
                                    TextButton(
                                        onClick = { escolhendoFreebetDa = index },
                                        modifier = Modifier.padding(start = 28.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text("🎁 Usar freebet nesta casa", color = Color(0xFF7B97FF), fontSize = 12.sp)
                                    }
                                }
                            }

                            Text(
                                "Stake e odd de cada casa deixam o saldo das casas certo quando a surebet bater " +
                                        "(Lay: a stake do lay; a responsabilidade é calculada).",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 11.sp
                            )

                            // Botão adicionar casa (só aparece se tiver menos de 5)
                            if (casas.size < 5) {
                                OutlinedButton(
                                    onClick = {
                                        casas.add("")
                                        stakes.add("")
                                        odds.add("")
                                        tipos.add("Back")
                                        freebetsDasCasas.add("")
                                    },
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
                            } else { Spacer(modifier = Modifier.height(4.dp)) }
                        }



                        // ── Data ─────────────────────────────────────────
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

                        // ── Divisor ──────────────────────────────────────
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.07f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // ── Botão Salvar ─────────────────────────────────
                        Button(
                            onClick = {
                                val valorDouble = parseValorBR(lucroDigitado)

                                // Valida lucro
                                if (valorDouble == null || valorDouble <= 0.0) {
                                    scope.launch { snackbarHostState.showSnackbar("Informe um lucro válido.") }
                                    return@Button
                                }

                                // Valida que as 2 primeiras casas estão preenchidas
                                if (casas[0].isBlank() || casas[1].isBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar("Preencha pelo menos 2 casas de aposta.") }
                                    return@Button
                                }

                                // Monta string de casas (ignora campos vazios extras)
                                val indicesPreenchidos = casas.indices.filter { casas[it].isNotBlank() }
                                val casasPreenchidas = indicesPreenchidos.map { casas[it].trim() }
                                val casaFinal = casasPreenchidas.joinToString(" | ")

                                // Pernas: só se todas as casas tiverem stake e odd; sem nenhuma, fica como antes
                                val algumaPreenchida = indicesPreenchidos.any { stakes[it].isNotBlank() || odds[it].isNotBlank() }
                                val pernas = indicesPreenchidos.map { i ->
                                    Perna(
                                        casa = casas[i].trim(),
                                        tipo = tipos[i],
                                        odd = parseValorBR(odds[i]),
                                        stake = parseValorBR(stakes[i]),
                                        freebetId = freebetDa(i)?.id,
                                        custoFreebet = freebetDa(i)?.custo
                                    )
                                }
                                val pernasValidas = pernas.all { (it.stake ?: 0.0) > 0.0 && (it.odd ?: 0.0) > 1.0 }
                                if (algumaPreenchida && !pernasValidas) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Preencha stake e odd (maior que 1) de todas as casas, ou deixe todas em branco."
                                        )
                                    }
                                    return@Button
                                }

                                val descricaoFinal = "Surebet ✅" +
                                        if (descricaoExtra.isNotBlank()) " - ${descricaoExtra.trim()}" else ""

                                val aposta = Aposta(
                                    id = 0,
                                    descricao = descricaoFinal,
                                    casa = casaFinal,
                                    valor = valorDouble,
                                    odds = 1.0,
                                    retornoPotencial = valorDouble,
                                    lucro = 0.0,
                                    data = data,
                                    pernasJson = if (algumaPreenchida) pernasParaJson(pernas) else null,
                                    stakesNasCasas = algumaPreenchida
                                )

                                salvando = true // evita salvar duas vezes com toques rápidos
                                scope.launch {
                                    // Com stake e odd, cobre a stake de cada casa (depósito automático do que falta)
                                    withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).salvarSurebet(aposta) }
                                    val intent = Intent(context, MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    (context as? ComponentActivity)?.finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F6FFF)),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            enabled = !salvando
                        ) {
                            Text("Salvar Surebet", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(context, ImportacaoTelegramActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.07f),
                                contentColor = Color.White.copy(alpha = 0.85f)
                            ),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("📲 Importar do Telegram", fontSize = 14.sp)
                        }
                    }
                }

                escolhendoFreebetDa?.let { i ->
                    // Freebets ainda não usadas nesta surebet; as da casa digitada primeiro
                    val opcoes = freebetsDisponiveis
                        .filter { d -> freebetsDasCasas.none { it == d.id.toString() } }
                        .sortedByDescending { it.casa.equals(casas[i].trim(), ignoreCase = true) }
                    AlertDialog(
                        onDismissRequest = { escolhendoFreebetDa = null },
                        containerColor = Color(0xFF1A1F30),
                        titleContentColor = Color.White,
                        title = { Text("Qual freebet?") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                opcoes.forEach { fb ->
                                    OutlinedButton(
                                        onClick = { aplicarFreebet(i, fb); escolhendoFreebetDa = null },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(0xFF1E2338),
                                            contentColor = Color.White
                                        ),
                                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(
                                                fb.casa + " · " + String.format(Locale("pt", "BR"), "R$ %,.2f", fb.valor),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "Perdeu " + String.format(Locale("pt", "BR"), "R$ %,.2f", fb.custo) +
                                                        (fb.validade?.let { " · vence $it" } ?: ""),
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.45f)
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { escolhendoFreebetDa = null }) {
                                Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    )
                }
            }
        }
    }
}
