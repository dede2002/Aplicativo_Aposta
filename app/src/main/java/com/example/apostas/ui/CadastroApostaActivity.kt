package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CadastroApostaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apostaId = intent.getIntExtra("aposta_id", 0)

        setContent {
            ApostasTheme {
                var apostaExistente by remember { mutableStateOf<Aposta?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    if (apostaId != 0) {
                        val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
                        apostaExistente = withContext(Dispatchers.IO) {
                            dao.getAll().find { it.id == apostaId }
                        }
                    }
                }

                FormularioCadastro(apostaExistente) { apostaParaSalvar ->
                    scope.launch {
                        val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
                        withContext(Dispatchers.IO) {
                            if (apostaParaSalvar.id == 0) {
                                dao.insert(apostaParaSalvar)
                            } else {
                                dao.delete(apostaParaSalvar.copy()) // remove antigo
                                dao.insert(apostaParaSalvar)        // insere novo com mesmo ID
                            }
                        }
                        finish()
                    }
                }
            }
        }
    }
}
@Composable
fun FormularioCadastro(
    apostaExistente: Aposta?,
    onSalvar: (Aposta) -> Unit
) {
    var descricao by remember { mutableStateOf(apostaExistente?.descricao ?: "") }
    var casa by remember { mutableStateOf(apostaExistente?.casa ?: "") }
    var valor by remember { mutableStateOf(apostaExistente?.valor?.toString() ?: "") }
    var odds by remember { mutableStateOf(apostaExistente?.odds?.toString() ?: "") }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = descricao,
            onValueChange = { descricao = it },
            label = { Text("Descrição") }
        )
        OutlinedTextField(
            value = casa,
            onValueChange = { casa = it },
            label = { Text("Casa") }
        )
        OutlinedTextField(
            value = valor,
            onValueChange = { valor = it },
            label = { Text("Valor") }
        )
        OutlinedTextField(
            value = odds,
            onValueChange = { odds = it },
            label = { Text("Odds") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val valorDouble = valor.toDoubleOrNull() ?: 0.0
            val oddsDouble = odds.toDoubleOrNull() ?: 0.0
            val retorno = valorDouble * oddsDouble

            val aposta = Aposta(
                id = apostaExistente?.id ?: 0,
                descricao = descricao,
                casa = casa,
                valor = valorDouble,
                odds = oddsDouble,
                retornoPotencial = retorno,
                lucro = apostaExistente?.lucro ?: 0.0 // mantém lucro anterior se houver
            )

            onSalvar(aposta)
        }) {
            Text("Salvar")
        }
    }
}
