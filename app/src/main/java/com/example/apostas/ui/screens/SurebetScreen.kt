package com.example.apostas.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SurebetScreen() {
    var odd1 by remember { mutableStateOf("") }
    var odd2 by remember { mutableStateOf("") }
    var resultado by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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

        Button(onClick = {
            val o1 = odd1.toDoubleOrNull()
            val o2 = odd2.toDoubleOrNull()

            resultado = if (o1 != null && o2 != null && o1 > 0 && o2 > 0) {
                val margem = (1 / o1) + (1 / o2)
                if (margem < 1) {
                    "✅ Há Surebet!".format((1 - margem) * 100)
                } else {
                    "❌ Não há Surebet.".format((1 - margem) * 100)
                }
            } else {
                "Preencha as odds corretamente."
            }
        }) {
            Text("Calcular")
        }

        resultado?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
        }
    }
}
