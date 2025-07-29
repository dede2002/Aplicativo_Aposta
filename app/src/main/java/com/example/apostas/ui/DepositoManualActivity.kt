package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.DepositoManual
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.apostas.ui.components.casasDeAposta

class DepositoManualActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApostasTheme {
                TelaDepositoManual(onFinalizar = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDepositoManual(onFinalizar: () -> Unit) {
    var casa by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // MUDANÇA: Cores agora são dinâmicas baseadas no tema
    val corFundo = (Color(0xFF1E2235))
    val corCard = MaterialTheme.colorScheme.surfaceContainerHigh
    val corPrimaria = MaterialTheme.colorScheme.primary
    val corTextoHeader = MaterialTheme.colorScheme.onPrimary
    val corTextoLabel = MaterialTheme.colorScheme.onSurfaceVariant // Cor mais sutil para labels

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(corFundo)
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(top = 58.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(corCard) // MUDANÇA: Cor do card baseada no tema
                .widthIn(max = 400.dp)
        ) {
            // Cabeçalho
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(corPrimaria)
                    .padding(24.dp)
            ) {
                Text(
                    text = "Cadastrar Depósito",
                    color = corTextoHeader,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Adicione um novo depósito à sua conta",
                    color = corTextoHeader.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }

            // Corpo com os campos
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Campo "Casa de Aposta" com dropdown
                Column {
                    Spacer(Modifier.height(0.dp))
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        CampoCasaDeAposta(
                            valor = casa,
                            onValorChange = { casa = it },
                            sugestoes = casasDeAposta
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            casasDeAposta.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion) },
                                    onClick = {
                                        casa = suggestion
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Campo "Valor do Depósito"
                Column {
                    Text("Valor do Depósito", color = corTextoLabel, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { valor = it },
                        placeholder = { Text("R$ 0,00") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(Modifier.height(2.dp))

                // Botão "Adicionar Depósito"
                Button(
                    onClick = {
                        val valorDouble = valor.replace(',', '.').toDoubleOrNull() ?: 0.0
                        if (casa.isNotBlank() && valorDouble > 0) {
                            scope.launch {
                                val dao = AppDatabase.getDatabase(context).depositoDao()
                                withContext(Dispatchers.IO) {
                                    dao.inserir(DepositoManual(casa = casa, valor = valorDouble))
                                }
                                onFinalizar()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = corPrimaria)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Adicionar Depósito", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
