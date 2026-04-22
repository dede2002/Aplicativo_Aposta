package com.example.apostas.ui

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ApostasTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                var lucroDigitado by rememberSaveable { mutableStateOf("") }
                var descricaoExtra by rememberSaveable { mutableStateOf("") }
                var data by rememberSaveable {
                    mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
                }

                // Lista de casas — começa com 2 campos vazios
                val casas = remember { mutableStateListOf("", "") }

                val snackbarHostState = remember { SnackbarHostState() }
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
                        OutlinedTextField(
                            value = lucroDigitado,
                            onValueChange = { lucroDigitado = it },
                            label = { Text("Lucro (R$)") },
                            placeholder = { Text("0,00", color = Color.White.copy(alpha = 0.2f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
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
                                            sugestoes = casasDeAposta
                                        )
                                    }

                                    // Botão remover (só aparece do índice 2 em diante)
                                    if (index >= 2) {
                                        IconButton(
                                            onClick = { casas.removeAt(index) },
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
                            }

                            // Botão adicionar casa (só aparece se tiver menos de 5)
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
                                val valorDouble = lucroDigitado.replace(',', '.').toDoubleOrNull()

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
                                val casasPreenchidas = casas.filter { it.isNotBlank() }
                                val casaFinal = casasPreenchidas.joinToString(" | ")

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
                                    data = data
                                )

                                scope.launch {
                                    val dao = AppDatabase.getDatabase(context).apostaDao()
                                    withContext(Dispatchers.IO) { dao.insert(aposta) }
                                    val intent = Intent(context, MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    (context as? ComponentActivity)?.finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F6FFF)),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Text("Salvar Surebet", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}