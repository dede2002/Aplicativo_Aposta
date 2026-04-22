package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.DepositoManual
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.components.casasDeAposta
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DepositoManualActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ApostasTheme {
                TelaDepositoManual(onFinalizar = { finish() })
            }
        }
    }
}

@Composable
fun TelaDepositoManual(onFinalizar: () -> Unit) {
    var casa by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val bgPrimary   = Color(0xFF141824)
    val bgField     = Color(0xFF1E2338)
    val borderColor = Color.White.copy(alpha = 0.12f)
    val borderFocus = Color(0xFF4F6FFF)
    val labelColor  = Color.White.copy(alpha = 0.45f)
    val accentBlue  = Color(0xFF4F6FFF)

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = bgPrimary, darkIcons = false)
        systemUiController.setNavigationBarColor(color = bgPrimary, darkIcons = false)
    }

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
        containerColor = bgPrimary
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
                    "Novo Depósito",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Adicione saldo a uma casa de aposta",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }

            // ── Casa de Aposta ───────────────────────────────────
            CampoCasaDeAposta(
                valor = casa,
                onValorChange = { casa = it },
                sugestoes = casasDeAposta
            )

            // ── Valor ────────────────────────────────────────────
            OutlinedTextField(
                value = valor,
                onValueChange = { valor = it },
                label = { Text("Valor do Depósito (R$)") },
                placeholder = { Text("0,00", color = Color.White.copy(alpha = 0.2f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

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
                    if (casa.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar("Informe a casa de aposta.") }
                        return@Button
                    }
                    if (valorDouble == null || valorDouble <= 0.0) {
                        scope.launch { snackbarHostState.showSnackbar("Informe um valor válido.") }
                        return@Button
                    }
                    scope.launch {
                        val dao = AppDatabase.getDatabase(context).depositoDao()
                        withContext(Dispatchers.IO) {
                            dao.inserir(DepositoManual(casa = casa, valor = valorDouble))
                        }
                        onFinalizar()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Adicionar Depósito", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}