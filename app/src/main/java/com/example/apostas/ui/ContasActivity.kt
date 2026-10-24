package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.ContaCasa
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.components.casasDeAposta
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// "Minhas contas": contas extras de cada casa (outros CPFs). Aparecem para escolher nas casas da surebet
// ("Bet365 (Irmã)") e cada uma tem o próprio saldo em Estatísticas. A conta principal é a própria casa.
class ContasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { ApostasTheme { TelaContas() } }
    }
}

@Composable
private fun TelaContas() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fundo = Color(0xFF141824)
    val campoFundo = Color(0xFF1E2338)
    val azul = Color(0xFF4F6FFF)

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = fundo, darkIcons = false)
        systemUiController.setNavigationBarColor(color = fundo, darkIcons = false)
    }

    var contas by remember { mutableStateOf<List<ContaCasa>>(emptyList()) }
    var casa by remember { mutableStateOf("") }
    var apelido by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var removendo by remember { mutableStateOf<ContaCasa?>(null) }

    fun recarregar() {
        scope.launch { contas = withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).contaCasaDao().getAll() } }
    }
    LaunchedEffect(Unit) { recarregar() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fundo)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.padding(bottom = 4.dp)) {
            Text("Minhas contas", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(
                "Contas de outros CPFs em cada casa. A principal é a própria casa.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }

        // ── Nova conta ───────────────────────────────────────────
        CampoCasaDeAposta(valor = casa, onValorChange = { casa = it; erro = null }, sugestoes = casasDeAposta)
        OutlinedTextField(
            value = apelido,
            onValueChange = { apelido = it.replace("(", "").replace(")", ""); erro = null },
            label = { Text("De quem é a conta (ex.: Irmã)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            isError = erro != null,
            supportingText = erro?.let { { Text(it, color = Color(0xFFEF9A9A)) } },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = campoFundo,
                focusedContainerColor = campoFundo,
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedBorderColor = azul,
                unfocusedLabelColor = Color.White.copy(alpha = 0.45f),
                focusedLabelColor = azul,
                unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                focusedTextColor = Color.White,
                cursorColor = azul
            )
        )
        Button(
            onClick = {
                // Usa o nome da lista de casas do app, se for a mesma casa
                val nomeCasa = casasDeAposta.firstOrNull { it.equals(casa.trim(), ignoreCase = true) } ?: casa.trim()
                val nova = ContaCasa(casa = nomeCasa, apelido = apelido.trim())
                erro = when {
                    nomeCasa.isBlank() -> "Escolha a casa."
                    nova.apelido.isBlank() -> "Diga de quem é a conta."
                    contas.any { it.nome.equals(nova.nome, ignoreCase = true) } -> "Essa conta já existe."
                    else -> null
                }
                if (erro == null) {
                    scope.launch {
                        withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).contaCasaDao().inserir(nova) }
                        apelido = ""
                        recarregar()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = azul),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) { Text("Adicionar conta", fontSize = 15.sp, fontWeight = FontWeight.Medium) }

        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

        // ── Contas cadastradas, por casa ─────────────────────────
        if (contas.isEmpty()) {
            Text("Nenhuma conta extra cadastrada.", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
        }
        contas.groupBy { it.casa }.forEach { (nomeCasa, daCasa) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1F30)),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(nomeCasa, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("Principal", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                    daCasa.forEach { conta ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(conta.apelido, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { removendo = conta }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remover conta", tint = Color(0xFFEF9A9A), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    removendo?.let { conta ->
        AlertDialog(
            onDismissRequest = { removendo = null },
            containerColor = Color(0xFF1A1F30),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f),
            title = { Text("Remover ${conta.nome}?") },
            text = { Text("Ela sai da lista de contas. Apostas e saldo já anotados nela continuam.") },
            confirmButton = {
                TextButton(onClick = {
                    removendo = null
                    scope.launch {
                        withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).contaCasaDao().apagar(conta) }
                        recarregar()
                    }
                }) { Text("Remover", color = Color(0xFFEF9A9A)) }
            },
            dismissButton = { TextButton(onClick = { removendo = null }) { Text("Cancelar", color = Color.White.copy(alpha = 0.6f)) } }
        )
    }
}
