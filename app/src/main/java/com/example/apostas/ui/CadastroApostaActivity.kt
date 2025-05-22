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
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.components.casasDeAposta


class CadastroApostaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apostaId = intent.getIntExtra("aposta_id", 0)

        setContent {
            ApostasTheme {
                val scope = rememberCoroutineScope()
                var apostaExistente by remember { mutableStateOf<Aposta?>(null) }

                // Carrega aposta do banco se for edição
                LaunchedEffect(Unit) {
                    if (apostaId != 0) {
                        val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
                        apostaExistente = withContext(Dispatchers.IO) {
                            dao.getById(apostaId)
                        }
                    }
                }

                // Exibe o formulário apenas após carregar (ou se for novo)
                if (apostaId == 0 || apostaExistente != null) {
                    FormularioCadastro(apostaExistente) { apostaParaSalvar ->
                        scope.launch {
                            val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
                            withContext(Dispatchers.IO) {
                                if (apostaParaSalvar.id == 0) {
                                    dao.insert(apostaParaSalvar)
                                } else {
                                    dao.delete(apostaParaSalvar.copy())
                                    dao.insert(apostaParaSalvar)
                                }
                            }
                            finish()
                        }
                    }
                } else {
                    // Exibe loading enquanto carrega
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
    var descricao by rememberSaveable { mutableStateOf("") }
    var casa by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    var odds by rememberSaveable { mutableStateOf("") }

    // Preenche os campos com os dados da aposta carregada
    LaunchedEffect(apostaExistente) {
        apostaExistente?.let {
            descricao = it.descricao
            casa = it.casa
            valor = it.valor.toString()
            odds = it.odds.toString()
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = descricao,
            onValueChange = { descricao = it },
            label = { Text("Descrição") },
            modifier = Modifier.fillMaxWidth()
        )
        CampoCasaDeAposta(
            valor = casa,
            onValorChange = { casa = it },
            sugestoes = casasDeAposta
        )
        OutlinedTextField(
            value = valor,
            onValueChange = { valor = it },
            label = { Text("Valor") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = odds,
            onValueChange = { odds = it },
            label = { Text("Odds") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
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
                    lucro = apostaExistente?.lucro ?: 0.0
                )

                onSalvar(aposta)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar")
        }
    }
}