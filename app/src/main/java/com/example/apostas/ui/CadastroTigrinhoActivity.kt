package com.example.apostas.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

class CadastroTigrinhoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ApostasTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                var lucroDigitado by rememberSaveable { mutableStateOf("") }
                var descricaoExtra by rememberSaveable { mutableStateOf("") }
                var casa by rememberSaveable { mutableStateOf("") }
                var data by rememberSaveable {
                    mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
                }

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

                val bgField     = Color(0xFF1E2338)
                val borderColor = Color.White.copy(alpha = 0.12f)
                val borderFocus = Color(0xFF4F6FFF)
                val labelColor  = Color.White.copy(alpha = 0.45f)
                val accentBlue  = Color(0xFF4F6FFF)

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
                                "Novo Cassino ♠️",
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

                        // ── Lucro / Prejuízo ─────────────────────────────
                        OutlinedTextField(
                            value = lucroDigitado,
                            onValueChange = { lucroDigitado = it },
                            label = { Text("Lucro ou Prejuízo (sem sinal)") },
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
                            placeholder = { Text("Ex: Fortune Tiger", color = Color.White.copy(alpha = 0.2f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            singleLine = true
                        )

                        // ── Casa de Aposta ───────────────────────────────
                        CampoCasaDeAposta(
                            valor = casa,
                            onValorChange = { casa = it },
                            sugestoes = casasDeAposta
                        )

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

                                if (valorDouble == null || valorDouble <= 0.0) {
                                    scope.launch { snackbarHostState.showSnackbar("Informe um valor válido.") }
                                    return@Button
                                }
                                if (casa.isBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar("Informe a casa de aposta.") }
                                    return@Button
                                }

                                val descricaoFinal = "Cassino ♠️" +
                                        if (descricaoExtra.isNotBlank()) " - ${descricaoExtra.trim()}" else ""

                                val aposta = Aposta(
                                    id = 0,
                                    descricao = descricaoFinal,
                                    casa = casa.trim(),
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
                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Text("Salvar Cassino", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}