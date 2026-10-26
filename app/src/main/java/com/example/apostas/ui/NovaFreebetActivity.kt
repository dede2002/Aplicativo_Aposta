package com.example.apostas.ui

import android.app.DatePickerDialog
import com.example.apostas.ui.theme.TipsCores
import com.example.apostas.ui.theme.fundoTips
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
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
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.Freebet
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.components.casasDeAposta
import com.example.apostas.ui.theme.ApostasTheme
import com.example.apostas.util.parseValorBR
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Cadastro de uma freebet ganha numa missão (aberto pelo "+ Adicionar freebet" da tela Freebets).
class NovaFreebetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { ApostasTheme { TelaNovaFreebet(onSalva = { finish() }) } }
    }
}

@Composable
private fun TelaNovaFreebet(onSalva: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fundo = TipsCores.Fundo
    val fundoCampo = TipsCores.CampoOpaco
    val azul = TipsCores.Laranja

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = fundo, darkIcons = false)
        systemUiController.setNavigationBarColor(color = fundo, darkIcons = false)
    }

    // rememberSaveable: o que foi digitado sobrevive a girar a tela
    var casa by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    var custo by rememberSaveable { mutableStateOf("") }
    var validade by rememberSaveable { mutableStateOf("") }
    var mostrarCalendario by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var salvando by remember { mutableStateOf(false) }

    // Contas de outros CPFs ("Bet365 (Irmã)") aparecem junto das casas nas sugestões
    var nomesDasContas by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        nomesDasContas = withContext(Dispatchers.IO) {
            AppDatabase.getDatabase(context).contaCasaDao().getAll().map { it.nome }
        }
    }

    LaunchedEffect(mostrarCalendario) {
        if (mostrarCalendario) {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                context,
                { _, ano, mes, dia -> validade = "%02d/%02d/%04d".format(dia, mes + 1, ano) },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).apply { setOnDismissListener { mostrarCalendario = false } }.show()
        }
    }

    val campo = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = fundoCampo,
        focusedContainerColor = fundoCampo,
        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
        focusedBorderColor = azul,
        unfocusedLabelColor = Color.White.copy(alpha = 0.45f),
        focusedLabelColor = azul,
        unfocusedTextColor = Color.White.copy(alpha = 0.85f),
        focusedTextColor = Color.White,
        cursorColor = azul,
        unfocusedTrailingIconColor = Color.White.copy(alpha = 0.35f),
        focusedTrailingIconColor = azul
    )

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
            Text("Nova freebet", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "A freebet que você ganhou e quanto perdeu na missão para ganhar.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }

        CampoCasaDeAposta(label = "Casa ou conta", valor = casa, onValorChange = { casa = it; erro = null }, sugestoes = casasDeAposta + nomesDasContas)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = valor,
                onValueChange = { valor = it; erro = null },
                label = { Text("Valor da freebet") },
                placeholder = { Text("0,00", color = Color.White.copy(alpha = 0.2f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = campo,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = custo,
                onValueChange = { custo = it; erro = null },
                label = { Text("Quanto perdeu") },
                placeholder = { Text("0,00", color = Color.White.copy(alpha = 0.2f)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = campo,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        val toqueValidade = remember { MutableInteractionSource() }
        OutlinedTextField(
            value = validade,
            onValueChange = {},
            label = { Text("Validade (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = campo,
            readOnly = true,
            trailingIcon = {
                if (validade.isNotBlank()) {
                    IconButton(onClick = { validade = "" }) { Icon(Icons.Default.Close, contentDescription = "Tirar validade") }
                } else {
                    Icon(Icons.Filled.CalendarToday, contentDescription = "Escolher validade")
                }
            },
            interactionSource = toqueValidade,
            singleLine = true
        )
        LaunchedEffect(toqueValidade) {
            toqueValidade.interactions.collect { if (it is PressInteraction.Release) mostrarCalendario = true }
        }
        erro?.let { Text(it, color = TipsCores.Vermelho, fontSize = 12.sp) }

        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

        Button(
            onClick = {
                val v = parseValorBR(valor)
                val c = if (custo.isBlank()) 0.0 else parseValorBR(custo)
                // Usa o nome que o app já conhece (lista de casas ou conta cadastrada), se for o mesmo
                val nomeCasa = (casasDeAposta + nomesDasContas).firstOrNull { it.equals(casa.trim(), ignoreCase = true) } ?: casa.trim()
                erro = when {
                    nomeCasa.isBlank() -> "Escolha a casa."
                    v == null || v <= 0.0 -> "Informe o valor da freebet."
                    c == null || c < 0.0 -> "Informe quanto perdeu (0 se não perdeu nada)."
                    else -> null
                }
                if (erro != null) return@Button
                salvando = true
                val nova = Freebet(
                    casa = nomeCasa,
                    valor = v!!,
                    custo = c!!,
                    data = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                    validade = validade.ifBlank { null }
                )
                scope.launch {
                    withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).freebetDao().inserir(nova) }
                    onSalva()
                }
            },
            enabled = !salvando,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = azul),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) { Text("Salvar freebet", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
    }
}
