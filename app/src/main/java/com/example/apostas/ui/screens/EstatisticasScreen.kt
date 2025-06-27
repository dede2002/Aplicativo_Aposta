package com.example.apostas.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.LucroTotal
import com.example.apostas.data.Saque
import com.example.apostas.ui.DepositoManualActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.apostas.ui.GraficoLucroAvancadoActivity
import androidx.compose.material.icons.filled.Refresh


@Composable
fun EstatisticasScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var lucroTotalSalvo by remember { mutableDoubleStateOf(0.0) }
    var lucroEditado by remember { mutableStateOf("") }
    var editandoLucroTotal by remember { mutableStateOf(false) }
    var lucroTotal by remember { mutableDoubleStateOf(0.0) }
    var definidas by remember { mutableIntStateOf(0) }
    var indefinidas by remember { mutableIntStateOf(0) }
    var casasComSaldo by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var totalSaldoCasas by remember { mutableDoubleStateOf(0.0) }
    var totalDinheiroApostado by remember { mutableDoubleStateOf(0.0) }
    var lucroDiarioSalvo by remember { mutableDoubleStateOf(0.0) }


    LaunchedEffect(Unit) {
        val dao = AppDatabase.getDatabase(context).LucroDiarioDao()
        lucroDiarioSalvo = withContext(Dispatchers.IO) {
            dao.get()?.valor ?: 0.0
        }
    }



    fun atualizarDados() {
        scope.launch {
            val lucroDiarioDao = AppDatabase.getDatabase(context).LucroDiarioDao()
            lucroDiarioSalvo = withContext(Dispatchers.IO) { lucroDiarioDao.get()?.valor ?: 0.0 }

            carregarDados(
                context,
                { lucroTotalSalvo = it },
                { lucroTotal = it },
                { definidas = it },
                { indefinidas = it },
                { casasComSaldo = it },
                { totalSaldoCasas = it },
                { totalDinheiroApostado = it }
            )
        }
    }


    LaunchedEffect(Unit) {
        atualizarDados()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                atualizarDados()
            }
        }

        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("📊 Relatório de Apostas", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Lucro Diário: R$ %.2f".format(lucroDiarioSalvo),
                    style = MaterialTheme.typography.titleLarge
                )

                IconButton(onClick = {
                    scope.launch {
                        val dao = AppDatabase.getDatabase(context).LucroDiarioDao()
                        withContext(Dispatchers.IO) {
                            dao.salvar(com.example.apostas.data.LucroDiario(valor = 0.0))
                        }
                        lucroDiarioSalvo = 0.0
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh, // ou Replay, se preferir
                        contentDescription = "Zerar Lucro Diário"
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text("Total nas Casas: R$ %.2f".format(totalSaldoCasas))
            Text("Dinheiro apostado: R$ %.2f".format(totalDinheiroApostado))
            Text("Apostas definidas: $definidas")
            Text("Apostas em aberto: $indefinidas")
        }


        item {
            if (!editandoLucroTotal) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Banca: R$ %.2f".format(lucroTotalSalvo), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = {
                        lucroEditado = "%.2f".format(lucroTotalSalvo)
                        editandoLucroTotal = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar Lucro Total")
                    }
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = lucroEditado,
                        onValueChange = { lucroEditado = it },
                        label = { Text("Editar banca") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editandoLucroTotal = false }) {
                            Text("Cancelar")
                        }
                        Button(onClick = {
                            val novo = lucroEditado.replace(',', '.').toDoubleOrNull()
                            if (novo != null) {
                                scope.launch {
                                    val dao = AppDatabase.getDatabase(context).LucroTotalDao()
                                    withContext(Dispatchers.IO) {
                                        dao.salvar(LucroTotal(valor = novo))
                                    }
                                    lucroTotalSalvo = novo
                                    editandoLucroTotal = false
                                    atualizarDados()
                                }
                            }
                        }) {
                            Text("Salvar")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    context.startActivity(Intent(context, GraficoLucroAvancadoActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Histórico de Lucro")
            }
        }

        item {
            Button(
                onClick = {
                    context.startActivity(Intent(context, DepositoManualActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Depositar")
            }
        }


        if (casasComSaldo.isNotEmpty()) {
            item {
                Text("🏦 Casas com Dinheiro", style = MaterialTheme.typography.titleMedium)
            }

            items(casasComSaldo.toList()) { (casa, saldo) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$casa: R$ %.2f".format(saldo))
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabase.getDatabase(context).saqueDao().inserir(Saque(casa = casa, valor = saldo))
                            }
                            atualizarDados()
                        }
                    }) {
                        Text("Sacar")
                    }
                }
            }
        } else {
            item {
                Text("Nenhuma casa com saldo disponível.")
            }
        }
    }
}

suspend fun carregarDados(
    context: Context,
    setLucroTotalSalvo: (Double) -> Unit,
    setLucroTotal: (Double) -> Unit,
    setDefinidas: (Int) -> Unit,
    setIndefinidas: (Int) -> Unit,
    setCasasComSaldo: (Map<String, Double>) -> Unit,
    setTotalSaldoCasas: (Double) -> Unit,
    setTotalDinheiroApostado: (Double) -> Unit

) {
    val db = AppDatabase.getDatabase(context)
    val daoLucro = db.LucroTotalDao()
    setLucroTotalSalvo(withContext(Dispatchers.IO) { daoLucro.get()?.valor ?: 0.0 })

    val apostas = withContext(Dispatchers.IO) { db.apostaDao().getAll() }
    setLucroTotal(apostas.sumOf { it.lucro })
    setDefinidas(apostas.count { it.lucro != 0.0 })
    setIndefinidas(apostas.count { it.lucro == 0.0 })

    val depositos = withContext(Dispatchers.IO) { db.depositoDao().getAll() }
    val saques = withContext(Dispatchers.IO) { db.saqueDao().getAll() }

    val saldos = depositos.groupBy { it.casa }
        .mapValues { (_, lista) -> lista.sumOf { it.valor } }
        .toMutableMap()

    apostas.filter { it.lucro != 0.0 }.forEach { aposta ->
        saldos[aposta.casa] = (saldos[aposta.casa] ?: 0.0) + aposta.lucro
    }

    saques.forEach { saque ->
        saldos[saque.casa] = (saldos[saque.casa] ?: 0.0) - saque.valor
    }

    // 💡 Zera qualquer saldo negativo
    saldos.forEach { (casa, saldo) ->
        if (saldo < 0.0) saldos[casa] = 0.0
    }

    val casasComSaldo = saldos.filterValues { it > 0.0 }
    setCasasComSaldo(casasComSaldo)
    setTotalSaldoCasas(casasComSaldo.values.sum())

    setTotalDinheiroApostado(
        apostas
            .filter { it.lucro == 0.0 }
            .sumOf { it.valor }
    )

}