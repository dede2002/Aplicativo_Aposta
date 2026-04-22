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
import com.example.apostas.ui.components.RotasNavBar
import com.example.apostas.ui.screens.EstatisticasScreen
import com.example.apostas.ui.screens.SurebetScreen
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.example.apostas.ui.CadastroApostaActivity
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import java.util.Calendar
import android.app.DatePickerDialog
import androidx.compose.material.icons.filled.DateRange
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider


class MainActivity : ComponentActivity() {

    private val scope = MainScope()
    private val apostas = mutableStateListOf<Aposta>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ApostasTheme {

                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as android.app.Activity).window
                        val insetsController = WindowCompat.getInsetsController(window, view)
                        insetsController.isAppearanceLightStatusBars = false
                        insetsController.isAppearanceLightNavigationBars = false
                    }
                }

                var telaAtual by remember { mutableStateOf<RotasNavBar>(RotasNavBar.Apostas) }

                Scaffold(
                    containerColor = Color(0xFF141824), // fundo geral
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF1A1F30),
                            tonalElevation = 0.dp
                        ) {
                            listOf(
                                RotasNavBar.Apostas,
                                RotasNavBar.Estatisticas,
                                RotasNavBar.Surebet
                            ).forEach { tela ->
                                NavigationBarItem(
                                    selected = tela == telaAtual,
                                    onClick = { telaAtual = tela },
                                    icon = { Icon(tela.icon, contentDescription = tela.label) },
                                    label = { Text(tela.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF4F6FFF),
                                        selectedTextColor = Color(0xFF4F6FFF),
                                        unselectedIconColor = Color.White.copy(alpha = 0.4f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.4f),
                                        indicatorColor = Color(0xFF4F6FFF).copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (telaAtual) {
                            is RotasNavBar.Apostas -> TelaPrincipal(
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

                            is RotasNavBar.Estatisticas -> EstatisticasScreen()

                            is RotasNavBar.Surebet -> SurebetScreen()
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

    val backgroundColor = Color(0xFF141824)

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = backgroundColor, darkIcons = false)
        systemUiController.setNavigationBarColor(color = backgroundColor, darkIcons = false)
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
            .background(Color(0xFF141824))
            .statusBarsPadding()
    ) {
        // ── Header ──────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
        ) {
            Text(
                text = "Apostas",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(Date()),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp
            )
        }

        // ── Botões Compartilhar / Nova Aposta ────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { mostrarDialogoCompartilhar = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.07f),
                    contentColor = Color.White.copy(alpha = 0.85f)
                ),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Compartilhar", fontSize = 14.sp)
            }

            Button(
                onClick = onNovaApostaClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F6FFF)),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Nova Aposta", fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Chips de Filtro ──────────────────────────────────────
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(FiltroAposta.entries) { filtro ->
                val selected = filtroSelecionado == filtro
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (filtro == FiltroAposta.DATA_SELECIONDA) showDatePicker = true
                        else selectedDate = null
                        filtroSelecionado = filtro
                    },
                    shape = RoundedCornerShape(50.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF4F6FFF),
                        selectedLabelColor = Color.White,
                        containerColor = Color.White.copy(alpha = 0.06f),
                        labelColor = Color.White.copy(alpha = 0.55f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = Color(0xFF4F6FFF),
                        borderColor = Color.White.copy(alpha = 0.12f),
                        borderWidth = 0.5.dp,
                        selectedBorderWidth = 0.5.dp
                    ),
                    label = {
                        if (filtro == FiltroAposta.DATA_SELECIONDA && selectedDate == null) {
                            Icon(
                                Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Text(
                                text = if (filtro == FiltroAposta.DATA_SELECIONDA)
                                    formato.format(selectedDate!!)
                                else
                                    filtro.label,
                                fontSize = 12.sp
                            )
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Lista de Apostas ─────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            if (apostasFiltradas.isEmpty()) {
                Text(
                    text = "Sem apostas por enquanto",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
    }

    // ── Diálogo Compartilhar ─────────────────────────────────────
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
                                FiltroAposta.GREENS -> aposta.lucro > 0.0
                                FiltroAposta.REDS -> aposta.lucro < 0.0
                                else -> false
                            }
                        }
                    }

                    if (apostasSelecionadas.isNotEmpty()) {
                        compartilharApostas(context, apostasSelecionadas)
                    }
                }) { Text("Compartilhar") }
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
                                    .filter { it != FiltroAposta.TODAS &&
                                            it != FiltroAposta.DATA_SELECIONDA &&
                                            it != FiltroAposta.GREENS &&        // adiciona
                                            it != FiltroAposta.REDS           // adiciona
                                                   }
                                    .forEach { filtrosSelecionados[it] = marcado }
                            }
                        )
                        Text("Todas")
                    }

                    Spacer(Modifier.height(8.dp))

                    FiltroAposta.entries
                        .filter { it != FiltroAposta.TODAS &&
                                it != FiltroAposta.DATA_SELECIONDA &&
                                it != FiltroAposta.GREENS &&        // adiciona
                                it != FiltroAposta.REDS }
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

@Composable
fun CardAposta(
    context: Context,
    aposta: Aposta,
    onExcluirClick: (Aposta) -> Unit,
    onEditarClick: (Aposta) -> Unit,
    onAtualizarLucro: (Aposta) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val statusTexto = when {
        aposta.lucro > 0.0 -> "Green"
        aposta.lucro < 0.0 -> "Red"
        else -> "Em Aberto"
    }

    val cardColor = when {
        aposta.lucro > 0.0 -> Color(0xFF1A2F1A)
        aposta.lucro < 0.0 -> Color(0xFF2F1A1A)
        else -> Color(0xFF1E2338)
    }
    val cardBorderColor = when {
        aposta.lucro > 0.0 -> Color(0xFF2E5E2E)
        aposta.lucro < 0.0 -> Color(0xFF5E2E2E)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val badgeBackground = when {
        aposta.lucro > 0.0 -> Color(0xFF2E7D32).copy(alpha = 0.35f)
        aposta.lucro < 0.0 -> Color(0xFFB71C1C).copy(alpha = 0.30f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val badgeTextColor = when {
        aposta.lucro > 0.0 -> Color(0xFF81C784)
        aposta.lucro < 0.0 -> Color(0xFFEF9A9A)
        else -> Color.White.copy(alpha = 0.45f)
    }
    val lucroColor = when {
        aposta.lucro > 0.0 -> Color(0xFF81C784)
        aposta.lucro < 0.0 -> Color(0xFFEF9A9A)
        else -> Color.White.copy(alpha = 0.8f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(0.5.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {

            // ── Badge de status ──────────────────────────────────
            Box(
                modifier = Modifier
                    .background(badgeBackground, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = statusTexto,
                    color = badgeTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Título ───────────────────────────────────────────
            Text(
                text = aposta.descricao,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(10.dp))

            // ── Linhas de dados ──────────────────────────────────
            @Composable
            fun InfoRow(label: String, value: String, valueColor: Color = Color.White.copy(alpha = 0.8f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                    Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            InfoRow("Casa         ", aposta.casa)

            when {
                aposta.descricao.startsWith("Cassino ♠️") || aposta.descricao.startsWith("Surebet ✅") -> {
                    InfoRow("Lucro", "R$ %.2f".format(aposta.lucro), lucroColor)
                }
                else -> {
                    InfoRow("Valor", "R$ %.2f".format(aposta.valor))
                    InfoRow("Odds", aposta.odds.toString())
                    InfoRow("Retorno Potencial", "R$ %.2f".format(aposta.retornoPotencial))
                    InfoRow("Lucro", "R$ %.2f".format(aposta.retornoPotencial - aposta.valor), lucroColor)
                }
            }

            InfoRow("Data", aposta.data)

            // ── Divisor ──────────────────────────────────────────
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.07f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // ── Botões Green / Red / Em aberto ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Green
                OutlinedButton(
                    onClick = {
                        val lucro = when {
                            aposta.descricao.startsWith("Surebet ✅", ignoreCase = true) -> aposta.valor
                            aposta.descricao.startsWith("Cassino ♠\uFE0F", ignoreCase = true) -> aposta.valor
                            else -> aposta.retornoPotencial - aposta.valor
                        }
                        onAtualizarLucro(aposta.copy(lucro = lucro))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f),
                        contentColor = Color(0xFF81C784)
                    ),
                    border = BorderStroke(0.5.dp, Color(0xFF2E5E2E)),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                ) { Text("Green", fontSize = 11.sp, fontWeight = FontWeight.Medium) }

                // Red
                OutlinedButton(
                    onClick = {
                        onAtualizarLucro(aposta.copy(lucro = -aposta.valor))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFB71C1C).copy(alpha = 0.12f),
                        contentColor = Color(0xFFEF9A9A)
                    ),
                    border = BorderStroke(0.5.dp, Color(0xFF5E2E2E)),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                ) { Text("Red", fontSize = 11.sp, fontWeight = FontWeight.Medium) }

                // Em aberto
                OutlinedButton(
                    onClick = { onAtualizarLucro(aposta.copy(lucro = 0.0)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)
                ) { Text("Em aberto", fontSize = 11.sp, fontWeight = FontWeight.Medium) }
            }

            // ── Ícones de ação ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Compartilhar
                IconButton(
                    onClick = {
                        val statusFull = when {
                            aposta.lucro > 0.0 -> "🟢 Green"
                            aposta.lucro < 0.0 -> "🔴 Red"
                            else -> "⚪ Em Aberto"
                        }
                        val mensagem = if (aposta.descricao.startsWith("Surebet ✅") || aposta.descricao.startsWith("Cassino ♠️")) {
                            """
📌 Aposta: *${aposta.descricao.trim()}*
🏠 Casa: ${aposta.casa}
💸 Valor: R$ %.2f
🗓️ Data: ${aposta.data}
Status: $statusFull
                            """.trimIndent().format(aposta.lucro)
                        } else {
                            """
📌 Aposta: *${aposta.descricao.trim()}*
🏠 Casa: ${aposta.casa}
💸 Valor: R$ %.2f
📈 Odds: ${aposta.odds}
💰 Potencial: R$ %.2f
📊 Lucro: R$ %.2f
🗓️ Data: ${aposta.data}
Status: $statusFull
                            """.trimIndent().format(aposta.valor, aposta.retornoPotencial, aposta.retornoPotencial - aposta.valor)
                        }

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
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        Default.Share,
                        contentDescription = "Compartilhar",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(Modifier.width(24.dp))

                // Editar
                IconButton(
                    onClick = { onEditarClick(aposta) },
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        Default.Edit,
                        contentDescription = "Editar",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(15.dp)
                    )
                }

                Spacer(Modifier.width(24.dp))

                // Excluir
                IconButton(
                    onClick = { showDialog = true },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        Default.Delete,
                        contentDescription = "Excluir",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }

    // ── Diálogo de confirmação de exclusão ───────────────────────
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
            val status = when {
                aposta.lucro > 0.0 -> "🟢 Green"
                aposta.lucro < 0.0 -> "🔴 Red"
                else -> "⚪ Em Aberto"
            }

            if (aposta.descricao.startsWith ("Surebet ✅") || aposta.descricao.startsWith ("Cassino ♠️")) {
                append("🏷️ *${aposta.descricao.trim()}*\n")
                append("🏠 ${aposta.casa}\n")
                append("💰 Valor: R$ %.2f\n".format(aposta.valor))
                append("\uD83D\uDDD3\uFE0F Data: ${aposta.data}\n")
                append("Status: $status\n")
            } else {
                append("🏷️ *${aposta.descricao.trim()}*\n")
                append("🏠 ${aposta.casa}\n")
                append("💰 Valor: R$ %.2f\n".format(aposta.valor))
                append("📈 Odds: %.2f\n".format(aposta.odds))
                append("💵 Retorno: R$ %.2f\n".format(aposta.retornoPotencial))
                append("📊 Lucro: R$ %.2f\n".format(aposta.retornoPotencial - aposta.valor))
                append("\uD83D\uDDD3\uFE0F Data: ${aposta.data}\n")
                append("Status: $status\n")
            }

            append("──────────────\n")
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
