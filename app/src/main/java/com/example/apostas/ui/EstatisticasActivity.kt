package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.LucroTotal
import com.example.apostas.data.Saque
import com.example.apostas.ui.components.BottomNavigationBar
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EstatisticasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApostasTheme {
                val context = LocalContext.current

                Scaffold(
                    bottomBar = {
                        BottomNavigationBar(selected = "estatisticas", context = context)
                    }
                ) { innerPadding ->
                    TelaEstatisticas(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun TelaEstatisticas(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lucroTotal by remember { mutableDoubleStateOf(0.0) }
    var definidas by remember { mutableIntStateOf(0) }
    var indefinidas by remember { mutableIntStateOf(0) }
    var casasComSaldo by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var totalSaldoCasas by remember { mutableDoubleStateOf(0.0) }

    var lucroTotalSalvo by remember { mutableDoubleStateOf(0.0) }
    var lucroEditado by remember { mutableStateOf("") }
    var editandoLucroTotal by remember { mutableStateOf(false) }

    fun carregarDados() {
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            val daoLucro = db.LucroTotalDao()
            lucroTotalSalvo = withContext(Dispatchers.IO) { daoLucro.get()?.valor ?: 0.0 }

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

            saques.forEach { saque ->
                saldos[saque.casa] = (saldos[saque.casa] ?: 0.0) - saque.valor
            }

            casasComSaldo = saldos.filterValues { it > 0.0 }
            totalSaldoCasas = casasComSaldo.values.sum()
        }
    }

    LaunchedEffect(Unit) {
        carregarDados()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("📊 Relatório de Apostas", style = MaterialTheme.typography.headlineSmall)
            Text("Lucro Diário: R$ %.2f".format(lucroTotal))
            Text("💰 Total nas Casas: R$ %.2f".format(totalSaldoCasas))
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
                    Text("Lucro Total: R$ %.2f".format(lucroTotalSalvo), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = {
                        lucroEditado = "%.2f".format(lucroTotalSalvo)
                        editandoLucroTotal = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar Lucro Total")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lucroEditado,
                        onValueChange = { lucroEditado = it },
                        label = { Text("Novo Lucro Total") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editandoLucroTotal = false }) {
                            Text("Cancelar")
                        }
                        Button(onClick = {
                            val novo = lucroEditado.toDoubleOrNull()
                            if (novo != null) {
                                scope.launch {
                                    val dao = AppDatabase.getDatabase(context).LucroTotalDao()
                                    withContext(Dispatchers.IO) {
                                        dao.salvar(LucroTotal(valor = novo))
                                    }
                                    lucroTotalSalvo = novo
                                    editandoLucroTotal = false
                                }
                            }
                        }) {
                            Text("Salvar")
                        }
                    }
                }
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
                    Button(
                        onClick = {
                            scope.launch {
                                val saque = Saque(casa = casa, valor = saldo)
                                withContext(Dispatchers.IO) {
                                    AppDatabase.getDatabase(context).saqueDao().inserir(saque)
                                }
                                carregarDados()
                            }
                        }
                    ) {
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
