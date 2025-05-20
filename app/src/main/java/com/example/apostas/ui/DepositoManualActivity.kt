
package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.DepositoManual
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun TelaDepositoManual(onFinalizar: () -> Unit) {
    var casa by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = casa,
            onValueChange = { casa = it },
            label = { Text("Casa de Aposta") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = valor,
            onValueChange = { valor = it },
            label = { Text("Valor (R$)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val valorDouble = valor.toDoubleOrNull() ?: 0.0
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Depositar")
        }
    }
}
