package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.apostas.ui.components.BottomNavigationBar
import com.example.apostas.ui.theme.ApostasTheme

class SurebetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApostasTheme {
                val context = LocalContext.current

                Scaffold(
                    bottomBar = {
                        BottomNavigationBar(selected = "surebet", context = context)
                    }
                ) { innerPadding ->
                    SurebetScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SurebetScreen(modifier: Modifier = Modifier) {
    var odd1 by remember { mutableStateOf("") }
    var odd2 by remember { mutableStateOf("") }
    var aposta1 by remember { mutableStateOf("") }
    var resultadoCard by remember { mutableStateOf<@Composable (() -> Unit)?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Calculadora de Surebet", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = odd1,
            onValueChange = { odd1 = it },
            label = { Text("Odd 1") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = odd2,
            onValueChange = { odd2 = it },
            label = { Text("Odd 2") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = aposta1,
            onValueChange = { aposta1 = it },
            label = { Text("Valor Apostado na Odd 1 (R$)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            val o1 = odd1.toDoubleOrNull()
            val o2 = odd2.toDoubleOrNull()
            val a1 = aposta1.toDoubleOrNull()

            resultadoCard = if (o1 != null && o2 != null && a1 != null && o1 > 1.0 && o2 > 1.0 && a1 > 0) {
                val margem = (1 / o1) + (1 / o2)

                if (margem < 1) {
                    val aposta2 = (a1 * o1) / o2
                    val totalInvestido = a1 + aposta2
                    val retornoGarantido = a1 * o1
                    val lucro = retornoGarantido - totalInvestido
                    val percentual = (lucro / totalInvestido) * 100

                    {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("✅ Há Surebet!", style = MaterialTheme.typography.titleMedium)
                                Text("• Apostar na Odd (%.2f): R$%.2f".format(o2,aposta2))
                                Text("• Total Investido: R$%.2f".format(totalInvestido))
                                Text("• Retorno Garantido: R$%.2f".format(retornoGarantido))
                                Text("• Lucro: R$%.2f".format(lucro, percentual))
                            }
                        }
                    }
                } else {
                    {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("❌ Não há Surebet.", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            } else {
                {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("⚠️ Preencha todos os campos corretamente.")
                        }
                    }
                }
            }
        }) {
            Text("Calcular")
        }

        resultadoCard?.let { it() }
    }
}


