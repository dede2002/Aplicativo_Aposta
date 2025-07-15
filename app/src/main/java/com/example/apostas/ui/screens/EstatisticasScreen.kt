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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.example.apostas.ui.GraficoLucroAvancadoActivity
import androidx.compose.material.icons.filled.Refresh
import java.util.Locale

@Composable
fun EstatisticasScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // A lógica de estado e carregamento de dados permanece a mesma
    var lucroTotalSalvo by remember { mutableDoubleStateOf(0.0) }
    var lucroEditado by remember { mutableStateOf("") }
    var editandoLucroTotal by remember { mutableStateOf(false) }
    var indefinidas by remember { mutableIntStateOf(0) }
    var casasComSaldo by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var totalSaldoCasas by remember { mutableDoubleStateOf(0.0) }
    var totalDinheiroApostado by remember { mutableDoubleStateOf(0.0) }
    var lucroDiarioSalvo by remember { mutableDoubleStateOf(0.0) }

    fun atualizarDados() {
        scope.launch {
            val lucroDiarioDao = AppDatabase.getDatabase(context).LucroDiarioDao()
            lucroDiarioSalvo = withContext(Dispatchers.IO) { lucroDiarioDao.get()?.valor ?: 0.0 }
            carregarDados(
                context, { lucroTotalSalvo = it }, { indefinidas = it },
                { casasComSaldo = it }, { totalSaldoCasas = it }, { totalDinheiroApostado = it }
            )
        }
    }

    // Usando DisposableEffect para observar o ciclo de vida e atualizar os dados
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                atualizarDados()
            }
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }


    // MUDANÇA: A UI foi totalmente redesenhada com Cards e um layout mais limpo
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Lucro Diário", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "R$ " + String.format(Locale("pt", "BR"), "%,.2f", lucroDiarioSalvo),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (lucroDiarioSalvo >= 0) Color(0xFF388E3C) else Color(0xFFD32F2F)
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val dao = AppDatabase.getDatabase(context).LucroDiarioDao()
                            withContext(Dispatchers.IO) {
                                dao.salvar(com.example.apostas.data.LucroDiario(valor = 0.0))
                            }
                            lucroDiarioSalvo = 0.0
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Zerar Lucro Diário")
                    }
                }
            }
        }


        // Card para a Banca Total
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (!editandoLucroTotal) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Banca Total", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "R$ " + String.format(Locale("pt", "BR"), "%,.2f", lucroTotalSalvo)
                                ,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = {
                            lucroEditado = "%.2f".format(lucroTotalSalvo).replace(',', '.')
                            editandoLucroTotal = true
                        }) {
                            Icon(Icons.Default.Edit, "Editar Banca")
                        }
                    }
                } else {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lucroEditado,
                            onValueChange = { lucroEditado = it },
                            label = { Text("Editar valor da banca") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { editandoLucroTotal = false }) { Text("Cancelar") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                val novo = lucroEditado.toDoubleOrNull()
                                if (novo != null) {
                                    scope.launch {
                                        val dao = AppDatabase.getDatabase(context).LucroTotalDao()
                                        withContext(Dispatchers.IO) { dao.salvar(LucroTotal(valor = novo)) }
                                        lucroTotalSalvo = novo
                                        editandoLucroTotal = false
                                    }
                                }
                            }) { Text("Salvar") }
                        }
                    }
                }
            }
        }

        // Card para Resumo da Atividade
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resumo da Atividade", style = MaterialTheme.typography.titleMedium)
                    InfoRow("Dinheiro em apostas:", formatarBR(totalDinheiroApostado))
                    InfoRow("Total nas Casas:", formatarBR(totalSaldoCasas))
                    InfoRow("Apostas em aberto:", "$indefinidas")
                }
            }
        }

        // Botões de Ação
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { context.startActivity(Intent(context, GraficoLucroAvancadoActivity::class.java)) }, modifier = Modifier.weight(1f)) {
                    Text("Histórico")
                }
                Button(onClick = { context.startActivity(Intent(context, DepositoManualActivity::class.java)) }, modifier = Modifier.weight(1f)) {
                    Text("Depositar")
                }
            }
        }

        // Lista de Casas com Saldo
        if (casasComSaldo.isNotEmpty()) {
            item {
                Text("🏦 Contas", style = MaterialTheme.typography.titleLarge)
            }
            items(casasComSaldo.toList(), key = { it.first }) { (casa, saldo) ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(casa, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Saldo: " + String.format(Locale("pt", "BR"), "R$ %,.2f", saldo), style = MaterialTheme.typography.bodyMedium)

                        }
                        Button(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).saqueDao().inserir(Saque(casa = casa, valor = saldo)) }
                                atualizarDados()
                            }
                        }) { Text("Sacar") }
                    }
                }
            }
        }
    }
}

// NOVO: Componente auxiliar para linhas de informação
@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}


// A função carregarDados permanece a mesma
suspend fun carregarDados(
    context: Context, setLucroTotalSalvo: (Double) -> Unit,
    setIndefinidas: (Int) -> Unit, setCasasComSaldo: (Map<String, Double>) -> Unit,
    setTotalSaldoCasas: (Double) -> Unit, setTotalDinheiroApostado: (Double) -> Unit
) {
    // (A lógica interna desta função não precisa mudar)
    val db = AppDatabase.getDatabase(context)
    val daoLucro = db.LucroTotalDao()
    setLucroTotalSalvo(withContext(Dispatchers.IO) { daoLucro.get()?.valor ?: 0.0 })

    val apostas = withContext(Dispatchers.IO) { db.apostaDao().getAll() }
    setIndefinidas(apostas.count { it.lucro == 0.0 })

    val depositos = withContext(Dispatchers.IO) { db.depositoDao().getAll() }
    val saques = withContext(Dispatchers.IO) { db.saqueDao().getAll() }
    val saldos = depositos.groupBy { it.casa }.mapValues { (_, lista) -> lista.sumOf { it.valor } }.toMutableMap()
    apostas.filter { it.lucro != 0.0 }.forEach { aposta ->
        saldos[aposta.casa] = (saldos[aposta.casa] ?: 0.0) + aposta.lucro
    }
    saques.forEach { saque ->
        saldos[saque.casa] = (saldos[saque.casa] ?: 0.0) - saque.valor
    }
    saldos.forEach { (casa, saldo) -> if (saldo < 0.0) saldos[casa] = 0.0 }
    val casasComSaldo = saldos.filterValues { it > 0.0 }
    setCasasComSaldo(casasComSaldo)
    setTotalSaldoCasas(casasComSaldo.values.sum())
    setTotalDinheiroApostado(apostas.filter { it.lucro == 0.0 }.sumOf { it.valor })
}

fun formatarBR(valor: Double?): String {
    return "R$ " + String.format(Locale("pt", "BR"), "%,.2f", valor ?: 0.0)
}
