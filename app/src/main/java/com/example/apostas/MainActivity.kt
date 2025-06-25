package com.example.apostas

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import com.example.apostas.data.LucroTotal
import com.example.apostas.ui.FiltroAposta
import androidx.compose.material.icons.Icons.Default
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.apostas.ui.screens.TelaPrincipal
import com.example.apostas.ui.screens.EstatisticasScreen
import com.example.apostas.ui.screens.SurebetScreen
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.example.apostas.ui.CadastroApostaActivity
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.foundation.isSystemInDarkTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import java.util.Calendar
import android.app.DatePickerDialog
import androidx.compose.material.icons.filled.DateRange


class MainActivity : ComponentActivity() {

    private val scope = MainScope()
    private val apostas = mutableStateListOf<Aposta>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        setContent {
            ApostasTheme {
                var telaAtual by remember { mutableStateOf<TelaPrincipal>(TelaPrincipal.Apostas) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                TelaPrincipal.Apostas,
                                TelaPrincipal.Estatisticas,
                                TelaPrincipal.Surebet
                            ).forEach { tela ->
                                NavigationBarItem(
                                    selected = tela == telaAtual,
                                    onClick = { telaAtual = tela },
                                    icon = { Icon(tela.icon, contentDescription = tela.label) },
                                    label = { Text(tela.label) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (telaAtual) {
                            is TelaPrincipal.Apostas -> TelaPrincipal(
                                apostas = apostas,
                                onNovaApostaClick = {
                                    val intent = Intent(this@MainActivity, CadastroApostaActivity::class.java)
                                    startActivity(intent)
                                },
                                onExcluirClick = { aposta ->
                                    scope.launch {
                                        val db = AppDatabase.getDatabase(applicationContext)
                                        val apostaDao = db.apostaDao()
                                        val depositoDao = db.depositoDao()

                                        withContext(Dispatchers.IO) {
                                            apostaDao.delete(aposta)

                                            // Corrige apenas o saldo da casa se a aposta tiver sido resolvida (lucro ≠ 0.0)
                                            if (aposta.lucro != 0.0) {
                                                depositoDao.inserir(
                                                    com.example.apostas.data.DepositoManual(
                                                        casa = aposta.casa,
                                                        valor = aposta.lucro
                                                    )
                                                )
                                            }
                                        }

                                        carregarApostasDoBanco()
                                    }
                                },
                                onEditarClick = { aposta ->
                                    val intent = Intent(this@MainActivity, CadastroApostaActivity::class.java)
                                    intent.putExtra("aposta_id", aposta.id)
                                    startActivity(intent)
                                },
                                onAtualizarLucro = { apostaAtualizada ->
                                    scope.launch {
                                        val db = AppDatabase.getDatabase(applicationContext)
                                        val apostaDao = db.apostaDao()
                                        val lucroTotalDao = db.LucroTotalDao()
                                        val lucroDiarioDao = db.LucroDiarioDao()

                                        withContext(Dispatchers.IO) {
                                            val apostaAntiga = apostaDao.getById(apostaAtualizada.id)
                                            val lucroAnterior = apostaAntiga?.lucro ?: 0.0
                                            val lucroNovo = apostaAtualizada.lucro

                                            // Atualiza lucro total
                                            val lucroTotalAtual = lucroTotalDao.get()?.valor ?: 0.0
                                            val lucroTotalAtualizado = lucroTotalAtual - lucroAnterior + lucroNovo
                                            lucroTotalDao.salvar(LucroTotal(valor = lucroTotalAtualizado))

                                            // Atualiza lucro diário
                                            val lucroDiarioAtual = lucroDiarioDao.get()?.valor ?: 0.0
                                            val lucroDiarioAtualizado = lucroDiarioAtual - lucroAnterior + lucroNovo
                                            lucroDiarioDao.salvar(com.example.apostas.data.LucroDiario(valor = lucroDiarioAtualizado))

                                            // Atualiza aposta
                                            apostaDao.delete(apostaAtualizada.copy())
                                            apostaDao.insert(apostaAtualizada)
                                        }

                                        carregarApostasDoBanco()
                                    }
                                }

                            )

                            is TelaPrincipal.Estatisticas -> EstatisticasScreen()

                            is TelaPrincipal.Surebet -> SurebetScreen()
                        }
                    }
                }

                carregarApostasDoBanco()
            }
        }
    }

    private fun carregarApostasDoBanco() {
        scope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
            val resultado = withContext(Dispatchers.IO) {
                dao.getAll()
            }
            apostas.clear()
            apostas.addAll(resultado)
        }
    }

    override fun onResume() {
        super.onResume()
        carregarApostasDoBanco()
    }
}

// --------------------------
// COMPONENTES COMPOSABLES
// --------------------------

@Composable
fun TelaPrincipal(
    apostas: List<Aposta>,
    onNovaApostaClick: () -> Unit,
    onExcluirClick: (Aposta) -> Unit,
    onEditarClick: (Aposta) -> Unit,
    onAtualizarLucro: (Aposta) -> Unit,
    modifier: Modifier = Modifier
) {
    var filtroSelecionado by remember { mutableStateOf(FiltroAposta.HOJE) }
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    var mostrarDialogoCompartilhar by remember { mutableStateOf(false) }
    val filtrosSelecionados = remember { mutableStateMapOf<FiltroAposta, Boolean>() }
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color(0xFF1E2235) else Color(0xFF1E2235)

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(
            color = backgroundColor,
            darkIcons = !isDarkTheme
        )
        systemUiController.setNavigationBarColor(
            color = backgroundColor,
            darkIcons = !isDarkTheme
        )
    }

    LaunchedEffect(Unit) {
        FiltroAposta.entries.forEach {
            if (it != FiltroAposta.TODAS) filtrosSelecionados[it] = false
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                selectedDate = calendar.time
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val apostasFiltradas = when (filtroSelecionado) {
        FiltroAposta.HOJE -> {
            val hoje = formato.format(Date())
            apostas.filter { it.data == hoje }
        }
        FiltroAposta.EM_ABERTO -> apostas.filter { it.lucro == 0.0 }
        FiltroAposta.RESOLVIDAS -> apostas.filter { it.lucro != 0.0 }
        FiltroAposta.TODAS -> apostas
        FiltroAposta.GREENS -> apostas.filter { it.lucro > 0.0 }
        FiltroAposta.REDS -> apostas.filter { it.lucro < 0.0 }
        FiltroAposta.DATA_SELECIONDA -> {
            val selecionadaStr = selectedDate?.let { formato.format(it) }
            if (selecionadaStr != null) apostas.filter { it.data == selecionadaStr }
            else emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = onNovaApostaClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Nova Aposta")
        }

        Button(
            onClick = { mostrarDialogoCompartilhar = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Compartilhar Apostas")
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(FiltroAposta.entries) { filtro ->
                FilterChip(
                    selected = filtroSelecionado == filtro,
                    onClick = {
                        if (filtro == FiltroAposta.DATA_SELECIONDA) {
                            showDatePicker = true
                        } else {
                            selectedDate = null
                        }
                        filtroSelecionado = filtro
                    },
                    label = {
                        if (filtro == FiltroAposta.DATA_SELECIONDA && selectedDate == null) {
                            Icon(Default.DateRange, contentDescription = "Selecionar Data")
                        } else {
                            Text(
                                if (filtro == FiltroAposta.DATA_SELECIONDA)
                                    formato.format(selectedDate!!)
                                else
                                    filtro.label
                            )
                        }
                    }

                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            if (apostasFiltradas.isEmpty()) {
                Text(
                    text = "Sem apostas por enquanto",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apostasFiltradas) { aposta ->
                        CardAposta(
                            context = context,
                            aposta = aposta,
                            onExcluirClick = onExcluirClick,
                            onEditarClick = onEditarClick,
                            onAtualizarLucro = onAtualizarLucro
                        )
                    }
                }
            }
        }

        if (mostrarDialogoCompartilhar) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoCompartilhar = false },
                confirmButton = {
                    TextButton(onClick = {
                        mostrarDialogoCompartilhar = false
                        val filtrosMarcados = filtrosSelecionados.filterValues { it }.keys
                        val hojeFormatado = formato.format(Date())

                        val apostasSelecionadas = apostas.filter { aposta ->
                            filtrosMarcados.any { filtro ->
                                when (filtro) {
                                    FiltroAposta.HOJE -> aposta.data == hojeFormatado
                                    FiltroAposta.EM_ABERTO -> aposta.lucro == 0.0
                                    FiltroAposta.RESOLVIDAS -> aposta.lucro != 0.0
                                    FiltroAposta.GREENS -> aposta.lucro > 0.0
                                    FiltroAposta.REDS -> aposta.lucro < 0.0
                                    else -> false
                                }
                            }
                        }

                        if (apostasSelecionadas.isNotEmpty()) {
                            compartilharApostas(context, apostasSelecionadas)
                        }
                    }) {
                        Text("Compartilhar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogoCompartilhar = false }) {
                        Text("Cancelar")
                    }
                },
                title = { Text("Selecionar tipos de apostas") },
                text = {
                    Column {
                        var todasMarcadas by remember {
                            mutableStateOf(filtrosSelecionados.values.all { it })
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = todasMarcadas,
                                onCheckedChange = { marcado ->
                                    todasMarcadas = marcado
                                    FiltroAposta.entries
                                        .filter { it != FiltroAposta.TODAS && it != FiltroAposta.DATA_SELECIONDA }
                                        .forEach {
                                            filtrosSelecionados[it] = marcado
                                        }
                                }
                            )
                            Text("Todas")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FiltroAposta.entries
                            .filter { it != FiltroAposta.TODAS && it != FiltroAposta.DATA_SELECIONDA }
                            .forEach { filtro ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = filtrosSelecionados[filtro] == true,
                                        onCheckedChange = { isChecked ->
                                            filtrosSelecionados[filtro] = isChecked
                                            todasMarcadas = filtrosSelecionados
                                                .filterKeys { it != FiltroAposta.DATA_SELECIONDA }
                                                .values.all { it }
                                        }
                                    )
                                    Text(filtro.label)
                                }
                            }
                    }
                }
            )
        }

    }
}





@Composable
fun CardAposta(
    context: Context,
    aposta: Aposta,
    onExcluirClick: (Aposta) -> Unit,
    onEditarClick: (Aposta) -> Unit,
    onAtualizarLucro: (Aposta) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val cardColor = corDoCardPorLucro(aposta.lucro)
    val textColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("📌 ${aposta.descricao}", color = textColor, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🏠 Casa: ${aposta.casa}", color = textColor)
            Text("💸 Valor: R$ %.2f".format(aposta.valor), color = textColor)
            Text("📈 Odds: ${aposta.odds}", color = textColor)
            Text("💰 Retorno Potencial: R$ %.2f".format(aposta.retornoPotencial), color = textColor)
            Text("📊 Lucro: R$ %.2f".format(aposta.retornoPotencial - aposta.valor), color = textColor)
            Text("\uD83D\uDDD3\uFE0F Data: ${aposta.data}", color = textColor)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val lucro = aposta.retornoPotencial - aposta.valor
                        onAtualizarLucro(aposta.copy(lucro = lucro))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Green")
                }

                OutlinedButton(
                    onClick = {
                        val prejuizo = -aposta.valor
                        onAtualizarLucro(aposta.copy(lucro = prejuizo))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Red")
                }

                OutlinedButton(
                    onClick = {
                        onAtualizarLucro(aposta.copy(lucro = 0.0))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Anulada")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = {
                    val mensagem = """
                        📌 Aposta: *${aposta.descricao}*
                        🏠 Casa: ${aposta.casa}
                        💸 Valor: R$ %.2f
                        📈 Odds: ${aposta.odds}
                        💰 Potencial: R$ %.2f
                        📊 Lucro: R$ %.2f
                        🗓️ Data: ${aposta.data}
                    """.trimIndent().format(aposta.valor, aposta.retornoPotencial, aposta.retornoPotencial - aposta.valor)

                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, mensagem)
                        type = "text/plain"
                        setPackage("com.whatsapp")
                    }

                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val fallback = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, mensagem)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(fallback, "Compartilhar via"))
                    }
                }) {
                    Icon(Default.Share, contentDescription = "Compartilhar", tint = textColor)
                }

                IconButton(onClick = { onEditarClick(aposta) }) {
                    Icon(Default.Edit, contentDescription = "Editar", tint = textColor)
                }

                IconButton(onClick = { showDialog = true }) {
                    Icon(Default.Delete, contentDescription = "Excluir", tint = textColor)
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja excluir esta aposta?") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onExcluirClick(aposta)
                }) { Text("Sim") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}


fun compartilharApostas(context: Context, apostas: List<Aposta>) {
    if (apostas.isEmpty()) return

    val texto = buildString {
        append("Minhas Apostas:\n\n")
        apostas.forEach { aposta ->
            append("🏷️ *${aposta.descricao}*\n")
            append("🏠 ${aposta.casa}\n")
            append("💰 Valor: R$ %.2f\n".format(aposta.valor))
            append("📈 Odds: %.2f\n".format(aposta.odds))
            append("💵 Retorno: R$ %.2f\n".format(aposta.retornoPotencial))
            append("📊 Lucro: R$ %.2f\n".format(aposta.retornoPotencial - aposta.valor))
            append("\uD83D\uDDD3\uFE0F Data: ${aposta.data}")
            append("\n──────────────\n")
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Compartilhar apostas via"))
    } catch (_: Exception) {}
}


@Composable
fun corDoCardPorLucro(lucro: Double): Color {
    val isDarkTheme = isSystemInDarkTheme()

    return when {
        lucro > 0.0 -> if (isDarkTheme) Color(0xFF1B5E20) else Color(0xFFD0F0C0) // Verde escuro no dark, verde claro no light
        lucro < 0.0 -> if (isDarkTheme) Color(0xFF8E2424) else Color(0xFFFFD6D6) // Vermelho escuro no dark, vermelho claro no light
        else -> if (isDarkTheme) Color(0xFF424242) else MaterialTheme.colorScheme.surfaceVariant // Neutro
    }
}