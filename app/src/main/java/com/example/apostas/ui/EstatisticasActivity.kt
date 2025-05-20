package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.Saque
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class EstatisticasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApostasTheme {
                TelaEstatisticas()
            }
        }
    }
}

@Composable
fun TelaEstatisticas() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lucroTotal by remember { mutableDoubleStateOf(0.0) }
    var definidas by remember { mutableIntStateOf(0) }
    var indefinidas by remember { mutableIntStateOf(0) }
    var casasComSaldo by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var totalSaldoCasas by remember { mutableDoubleStateOf(0.0) }

    fun carregarDados() {
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val apostas = withContext(Dispatchers.IO) { db.apostaDao().getAll() }
            val depositos = withContext(Dispatchers.IO) { db.depositoDao().getAll() }
            val saques = withContext(Dispatchers.IO) { db.saqueDao().getAll() }

            lucroTotal = apostas.sumOf { it.lucro }
            definidas = apostas.count { it.lucro != 0.0 }
            indefinidas = apostas.count { it.lucro == 0.0 }

            val saldos = depositos.groupBy { it.casa }
                .mapValues { (_, lista) -> lista.sumOf { it.valor } }
                .toMutableMap()

            apostas.filter { it.lucro != 0.0 }.forEach { aposta ->
                val casa = aposta.casa
                saldos[casa] = (saldos[casa] ?: 0.0) + aposta.lucro
            }

            // Subtrai os saques
            saques.forEach { saque ->
                saldos[saque.casa] = (saldos[saque.casa] ?: 0.0) - saque.valor
            }

            casasComSaldo = saldos.filterValues { it > 0.0 }
            totalSaldoCasas = casasComSaldo.values.sum()
        }
    }

    // Chama ao iniciar
    LaunchedEffect(Unit) {
        carregarDados()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("📊 Relatório de Apostas", style = MaterialTheme.typography.headlineSmall)
        Text("Lucro Total: R$ %.2f".format(lucroTotal))
        Text("💰 Total nas Casas: R$ %.2f".format(totalSaldoCasas))
        Text("Apostas definidas: $definidas")
        Text("Apostas em aberto: $indefinidas")

        Spacer(modifier = Modifier.height(24.dp))

        if (casasComSaldo.isNotEmpty()) {
            Text("🏦 Casas com Dinheiro", style = MaterialTheme.typography.titleMedium)

            casasComSaldo.forEach { (casa, saldo) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$casa: R$ %.2f".format(saldo))

                    Button(
                        onClick = {
                            scope.launch {
                                val saque = Saque(casa = casa, valor = saldo)
                                withContext(Dispatchers.IO) {
                                    AppDatabase.getDatabase(context).saqueDao().inserir(saque)
                                }
                                carregarDados() // atualiza lista
                            }
                        },

                    ) {
                        Text("Sacar")
                    }
                }
            }
        } else {
            Text("Nenhuma casa com saldo disponível.")
        }
    }
}


