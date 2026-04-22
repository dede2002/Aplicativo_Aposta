package com.example.apostas.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.LucroTotal
import com.example.apostas.data.Saque
import com.example.apostas.ui.DepositoManualActivity
import com.example.apostas.ui.GraficoLucroAvancadoActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun EstatisticasScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var lucroTotalSalvo by remember { mutableDoubleStateOf(0.0) }
    var lucroEditado by remember { mutableStateOf("") }
    var editandoLucroTotal by remember { mutableStateOf(false) }
    var indefinidas by remember { mutableIntStateOf(0) }
    var casasComSaldo by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var totalSaldoCasas by remember { mutableDoubleStateOf(0.0) }
    var totalDinheiroApostado by remember { mutableDoubleStateOf(0.0) }
    var lucroDiarioSalvo by remember { mutableDoubleStateOf(0.0) }
    var totalLucroSurebet by remember { mutableDoubleStateOf(0.0) }
    var totalLucroCassino by remember { mutableDoubleStateOf(0.0) }

    fun atualizarDados() {
        scope.launch {
            val lucroDiarioDao = AppDatabase.getDatabase(context).LucroDiarioDao()
            lucroDiarioSalvo = withContext(Dispatchers.IO) { lucroDiarioDao.get()?.valor ?: 0.0 }
            carregarDados(
                context,
                { lucroTotalSalvo = it },
                { indefinidas = it },
                { casasComSaldo = it },
                { totalSaldoCasas = it },
                { totalDinheiroApostado = it },
                { totalLucroSurebet = it },
                { totalLucroCassino = it }
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) atualizarDados()
        }
        val lifecycle = lifecycleOwner.lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141824))
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ── Lucro Diário ─────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (lucroDiarioSalvo >= 0) Color(0xFF1A2F1A) else Color(0xFF2F1A1A)
                ),
                border = BorderStroke(
                    0.5.dp,
                    if (lucroDiarioSalvo >= 0) Color(0xFF2E5E2E) else Color(0xFF5E2E2E)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Lucro Diário",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                        Text(
                            "R$ " + String.format(Locale("pt", "BR"), "%,.2f", lucroDiarioSalvo),
                            color = if (lucroDiarioSalvo >= 0) Color(0xFF81C784) else Color(0xFFEF9A9A),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val dao = AppDatabase.getDatabase(context).LucroDiarioDao()
                                    withContext(Dispatchers.IO) {
                                        dao.salvar(com.example.apostas.data.LucroDiario(valor = 0.0))
                                    }
                                    lucroDiarioSalvo = 0.0
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Zerar Lucro Diário",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Banca Total ──────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2338)),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                if (!editandoLucroTotal) {
                    var mostrarSaldo by remember { mutableStateOf(true) }

                    LaunchedEffect(Unit) {
                        mostrarSaldo = carregarVisibilidadeSaldo(context)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Banca Total",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                            Text(
                                if (mostrarSaldo)
                                    "R$ " + String.format(Locale("pt", "BR"), "%,.2f", lucroTotalSalvo)
                                else
                                    "R$ ••••••",
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        mostrarSaldo = !mostrarSaldo
                                        salvarVisibilidadeSaldo(context, mostrarSaldo)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (mostrarSaldo) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (mostrarSaldo) "Ocultar saldo" else "Mostrar saldo",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        lucroEditado = "%.2f".format(lucroTotalSalvo).replace(',', '.')
                                        editandoLucroTotal = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Editar Banca",
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = lucroEditado,
                            onValueChange = { lucroEditado = it },
                            label = { Text("Editar valor da banca") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { editandoLucroTotal = false }) {
                                Text("Cancelar")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val novo = lucroEditado.toDoubleOrNull()
                                    if (novo != null) {
                                        scope.launch {
                                            val dao = AppDatabase.getDatabase(context).LucroTotalDao()
                                            withContext(Dispatchers.IO) { dao.salvar(LucroTotal(valor = novo)) }
                                            lucroTotalSalvo = novo
                                            editandoLucroTotal = false
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F6FFF))
                            ) { Text("Salvar") }
                        }
                    }
                }
            }
        }

        // ── Resumo da Atividade ──────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1F30)),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Resumo da Atividade",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(10.dp))
                    EstatInfoRow("Dinheiro em apostas", formatarBR(totalDinheiroApostado))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                    EstatInfoRow("Total nas Casas", formatarBR(totalSaldoCasas))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                    EstatInfoRow("Apostas em aberto", "$indefinidas")
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                    EstatInfoRow(
                        "Lucro com Surebet",
                        formatarBR(totalLucroSurebet),
                        valueColor = if (totalLucroSurebet > 0) Color(0xFF81C784) else Color.White.copy(alpha = 0.85f)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                    EstatInfoRow(
                        "Lucro com Cassino",
                        formatarBR(totalLucroCassino),
                        valueColor = if (totalLucroCassino > 0) Color(0xFF81C784) else Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // ── Botões Histórico / Depositar ─────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(context, GraficoLucroAvancadoActivity::class.java))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha = 0.07f),
                        contentColor = Color.White.copy(alpha = 0.85f)
                    ),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("Histórico", fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        context.startActivity(Intent(context, DepositoManualActivity::class.java))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F6FFF)),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text("+ Depositar", fontSize = 14.sp)
                }
            }
        }

        // ── Título Contas ────────────────────────────────────────
        if (casasComSaldo.isNotEmpty()) {
            item {
                Text(
                    "🏦 Contas",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // ── Cards das casas ──────────────────────────────────
            items(casasComSaldo.toList(), key = { it.first }) { (casa, saldo) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1F30)),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                casa,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Saldo: " + String.format(Locale("pt", "BR"), "R$ %,.2f", saldo),
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppDatabase.getDatabase(context).saqueDao()
                                            .inserir(Saque(casa = casa, valor = saldo))
                                    }
                                    atualizarDados()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF4F6FFF).copy(alpha = 0.12f),
                                contentColor = Color(0xFF7B97FF)
                            ),
                            border = BorderStroke(0.5.dp, Color(0xFF4F6FFF).copy(alpha = 0.35f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text("Sacar", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstatInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.White.copy(alpha = 0.85f)
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

suspend fun carregarDados(
    context: Context,
    setLucroTotalSalvo: (Double) -> Unit,
    setIndefinidas: (Int) -> Unit,
    setCasasComSaldo: (Map<String, Double>) -> Unit,
    setTotalSaldoCasas: (Double) -> Unit,
    setTotalDinheiroApostado: (Double) -> Unit,
    setLucroSurebet: (Double) -> Unit,
    setLucroCassino: (Double) -> Unit
) {
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
    setLucroSurebet(apostas.filter { it.descricao.startsWith("Surebet ✅") }.sumOf { it.lucro })
    setLucroCassino(apostas.filter { it.descricao.startsWith("Cassino ♠️") }.sumOf { it.lucro })
}

fun formatarBR(valor: Double?): String {
    return "R$ " + String.format(Locale("pt", "BR"), "%,.2f", valor ?: 0.0)
}

fun salvarVisibilidadeSaldo(context: Context, visivel: Boolean) {
    val prefs = context.getSharedPreferences("preferencias_apostas", Context.MODE_PRIVATE)
    prefs.edit { putBoolean("mostrar_saldo", visivel) }
}

fun carregarVisibilidadeSaldo(context: Context): Boolean {
    val prefs = context.getSharedPreferences("preferencias_apostas", Context.MODE_PRIVATE)
    return prefs.getBoolean("mostrar_saldo", true)
}