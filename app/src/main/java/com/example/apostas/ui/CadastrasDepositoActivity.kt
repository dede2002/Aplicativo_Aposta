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

class CadastrarDepositoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ApostasTheme {
                val scope = rememberCoroutineScope()
                var casa by remember { mutableStateOf("") }
                var valor by remember { mutableStateOf("") }

                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = casa,
                        onValueChange = { casa = it },
                        label = { Text("Casa de Aposta") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { valor = it },
                        label = { Text("Valor") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val valorDouble = valor.toDoubleOrNull() ?: 0.0
                            if (casa.isNotBlank() && valorDouble > 0) {
                                scope.launch {
                                    val dao = AppDatabase.getDatabase(applicationContext).depositoDao()
                                    withContext(Dispatchers.IO) {
                                        dao.inserir(DepositoManual(casa = casa, valor = valorDouble))
                                    }
                                    finish()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Adicionar Depósito")
                    }
                }
            }
        }
    }
}
