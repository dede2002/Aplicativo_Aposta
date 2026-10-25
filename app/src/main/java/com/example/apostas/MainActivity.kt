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
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.apostas.data.casas
import com.example.apostas.data.casasDoResultado
import com.example.apostas.data.isDuploGreen
import com.example.apostas.data.isDuploRed
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.apostas.data.Perna
import com.example.apostas.data.pernaDaCasa
import com.example.apostas.data.pernas
import com.example.apostas.telegram.PrintsApostas
import com.example.apostas.telegram.LembreteApostasAbertas
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import com.example.apostas.data.excluirAposta
import com.example.apostas.util.parseValorBR
import kotlinx.coroutines.Dispatchers
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
import com.example.apostas.ui.FreebetsActivity
import androidx.compose.ui.Alignment
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.apostas.telegram.ImportacaoAutomatica
import com.example.apostas.telegram.ImportadorTelegram
import com.example.apostas.telegram.PushDoAparelho
import com.example.apostas.telegram.aparelhoConectado
import com.example.apostas.telegram.apagarConfiguracaoAntiga


class MainActivity : ComponentActivity() {

    // lifecycleScope é cancelado junto com a Activity (o MainScope antigo nunca era).
    private val scope get() = lifecycleScope
    private val apostas = mutableStateListOf<Aposta>()

    // Android 13+: pede para mostrar as notificações (surebets importadas e lembrete de apostas em aberto)
    private val pedirPermissaoNotificacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Filtro pedido por uma notificação (ex.: lembrete abre em "Em Aberto"); a tela aplica e zera
    private val filtroPedido = mutableStateOf<FiltroAposta?>(null)

    private fun lerFiltroDoIntent(intent: Intent?) {
        intent?.getStringExtra(LembreteApostasAbertas.EXTRA_FILTRO)
            ?.let { nome -> FiltroAposta.entries.firstOrNull { it.name == nome } }
            ?.let { filtroPedido.value = it }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lerFiltroDoIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        lerFiltroDoIntent(intent)

        apagarConfiguracaoAntiga(applicationContext)
        ImportacaoAutomatica.agendar(applicationContext)
        // Garante que o servidor tem o token de push atual deste celular
        if (aparelhoConectado(this)) {
            scope.launch { PushDoAparelho.registrar(applicationContext) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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
                // Notificação pediu um filtro: volta para a aba de apostas
                LaunchedEffect(filtroPedido.value) {
                    if (filtroPedido.value != null) telaAtual = RotasNavBar.Apostas
                }

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
                                filtroPedido = filtroPedido.value,
                                onFiltroPedidoAplicado = { filtroPedido.value = null },
                                onNovaApostaClick = {
                                    val intent = Intent(this@MainActivity, CadastroApostaActivity::class.java)
                                    startActivity(intent)
                                },
                                onExcluirClick = { aposta ->
                                    scope.launch {
                                        val db = AppDatabase.getDatabase(applicationContext)
                                        // Resolvida: compensa o saldo das casas; surebet nova em aberto: desfaz os depósitos das stakes
                                        withContext(Dispatchers.IO) { db.excluirAposta(aposta) }

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

                                        withContext(Dispatchers.IO) { db.withTransaction {
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

                                            // Atualiza aposta (tudo numa transação: ou grava tudo, ou nada)
                                            apostaDao.update(apostaAtualizada)
                                        } }

                                        carregarApostasDoBanco()
                                    }
                                }

                            )

                            is RotasNavBar.Estatisticas -> EstatisticasScreen()

                            is RotasNavBar.Surebet -> SurebetScreen()
                        }
                    }
                }
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
        importarDoTelegram()
    }

    // Ao abrir o app, busca na hora o que foi encaminhado ao bot (sem esperar os ~15 min).
    private fun importarDoTelegram() {
        if (!aparelhoConectado(this)) return
        scope.launch {
            val resultado = ImportadorTelegram.importar(applicationContext)
            val n = resultado.importadas.size
            if (n > 0) {
                carregarApostasDoBanco()
                val aviso = if (n == 1) "1 surebet importada do Telegram" else "$n surebets importadas do Telegram"
                val semLucro = if (resultado.semLucro > 0) " (confira o lucro)" else ""
                Toast.makeText(this@MainActivity, aviso + semLucro, Toast.LENGTH_LONG).show()
            }
        }
    }
}

// --------------------------
// COMPONENTES COMPOSABLES
// --------------------------

/** Quantas apostas o filtro "Mais recentes" mostra (as últimas que chegaram). */
private const val LIMITE_MAIS_RECENTES = 30

@Composable
fun TelaPrincipal(
    apostas: List<Aposta>,
    onNovaApostaClick: () -> Unit,
    onExcluirClick: (Aposta) -> Unit,
    onEditarClick: (Aposta) -> Unit,
    onAtualizarLucro: (Aposta) -> Unit,
    modifier: Modifier = Modifier,
    // Filtro vindo de uma notificação (ex.: lembrete de apostas em aberto)
    filtroPedido: FiltroAposta? = null,
    onFiltroPedidoAplicado: () -> Unit = {}
) {
    var filtroSelecionado by remember { mutableStateOf(FiltroAposta.HOJE) }
    LaunchedEffect(filtroPedido) {
        filtroPedido?.let {
            filtroSelecionado = it
            onFiltroPedidoAplicado()
        }
    }
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

    // Abre o diálogo uma vez por pedido; fechar (escolhendo ou cancelando) libera o próximo.
    LaunchedEffect(showDatePicker) {
        if (showDatePicker) {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = calendar.time
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                setOnDismissListener { showDatePicker = false }
            }.show()
        }
    }

    val apostasFiltradas = when (filtroSelecionado) {
        FiltroAposta.HOJE -> {
            val hoje = formato.format(Date())
            apostas.filter { it.data == hoje }
        }
        // Ordem de chegada: o id é sequencial, então o maior id é o último cadastrado/importado,
        // independente da data da aposta (a importada do Telegram leva a data original da entrada).
        FiltroAposta.MAIS_RECENTES -> apostas.sortedByDescending { it.id }.take(LIMITE_MAIS_RECENTES)
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

        // ── Freebets (cadastrar, ver as disponíveis e usar numa surebet) ──
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, FreebetsActivity::class.java)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFFFFB74D).copy(alpha = 0.08f),
                contentColor = Color(0xFFFFB74D)
            ),
            border = BorderStroke(0.5.dp, Color(0xFFFFB74D).copy(alpha = 0.35f)),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Text("🎁 Usar freebet", fontSize = 14.sp)
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
                    // Só estes filtros aparecem no diálogo; "Todas" reflete apenas eles.
                    val filtrosDoDialogo = listOf(FiltroAposta.HOJE, FiltroAposta.EM_ABERTO)
                    val todasMarcadas = filtrosDoDialogo.all { filtrosSelecionados[it] == true }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = todasMarcadas,
                            onCheckedChange = { marcado ->
                                filtrosDoDialogo.forEach { filtrosSelecionados[it] = marcado }
                            }
                        )
                        Text("Todas")
                    }

                    Spacer(Modifier.height(8.dp))

                    filtrosDoDialogo.forEach { filtro ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = filtrosSelecionados[filtro] == true,
                                    onCheckedChange = { isChecked ->
                                        filtrosSelecionados[filtro] = isChecked
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
    // Green/Red de operação com várias casas aguardando a escolha da casa do resultado
    var resultadoPendente by remember { mutableStateOf<Double?>(null) }
    // "Ver apostas de cada casa" aberto e print em tela cheia
    var mostrarPernas by remember { mutableStateOf(false) }
    var printAberto by remember { mutableStateOf<String?>(null) }
    printAberto?.let { VisualizadorPrint(it) { printAberto = null } }

    fun marcarResultado(lucro: Double) {
        if (aposta.casas.size > 1) resultadoPendente = lucro
        else onAtualizarLucro(aposta.copy(lucro = lucro, casaLucro = null))
    }

    // Resolvida: lucro real (Red = -valor). Em aberto: lucro potencial.
    val lucroExibido = if (aposta.lucro != 0.0) aposta.lucro else aposta.retornoPotencial - aposta.valor

    val statusTexto = when {
        aposta.isDuploGreen -> "Duplo Green"
        aposta.isDuploRed -> "Duplo Red"
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
                    // Aqui o valor cadastrado já é o lucro esperado da operação
                    if (aposta.lucro == 0.0) InfoRow("Lucro potencial", "R$ %.2f".format(aposta.valor))
                    else InfoRow("Lucro", "R$ %.2f".format(aposta.lucro), lucroColor)
                }
                else -> {
                    InfoRow("Valor", "R$ %.2f".format(aposta.valor))
                    InfoRow("Odds", aposta.odds.toString())
                    InfoRow("Retorno Potencial", "R$ %.2f".format(aposta.retornoPotencial))
                    InfoRow(
                        if (aposta.lucro == 0.0) "Lucro potencial" else "Lucro",
                        "R$ %.2f".format(lucroExibido),
                        lucroColor
                    )
                }
            }

            if (aposta.casaLucro != null) {
                InfoRow("Resultado em", aposta.casasDoResultado.joinToString(" + "))
            }

            InfoRow("Data", aposta.data)

            // ── O que foi apostado em cada casa (surebets importadas) ──
            val pernas = aposta.pernas
            if (pernas.isNotEmpty()) {
                Text(
                    if (mostrarPernas) "Ocultar apostas de cada casa ▴" else "Ver apostas de cada casa ▾",
                    color = Color(0xFF7B97FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { mostrarPernas = !mostrarPernas }
                        .padding(vertical = 4.dp)
                )
                if (mostrarPernas) {
                    pernas.forEach { perna ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(perna.casa, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                DetalhePerna(perna)
                            }
                            if (PrintsApostas.existe(context, perna.print)) {
                                IconButton(onClick = { printAberto = perna.print }, modifier = Modifier.size(36.dp)) {
                                    Icon(Default.Image, contentDescription = "Ver print de ${perna.casa}", tint = Color(0xFF7B97FF), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

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
                        marcarResultado(lucro)
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
                        marcarResultado(-aposta.valor)
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
                    onClick = { onAtualizarLucro(aposta.copy(lucro = 0.0, casaLucro = null)) },
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
                            """.trimIndent().format(aposta.valor, aposta.retornoPotencial, lucroExibido)
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

    // ── Diálogo: em qual casa caiu o resultado ───────────────────
    resultadoPendente?.let { lucro ->
        DialogoCasaDoResultado(
            aposta = aposta,
            lucro = lucro,
            onDismiss = { resultadoPendente = null },
            onConfirmar = { lucroFinal, casaLucro ->
                resultadoPendente = null
                onAtualizarLucro(aposta.copy(lucro = lucroFinal, casaLucro = casaLucro))
            }
        )
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


// "Ambas equipes Marcam: Sim" + "Back · odd 2,23 · R$ 1.000,00" (o que tiver)
@Composable
private fun DetalhePerna(perna: Perna) {
    perna.aposta?.let {
        Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 16.sp)
    }
    val numeros = listOfNotNull(
        perna.tipo?.let { if (it == "Lay") "Lay (contra)" else it },
        perna.odd?.let { "odd " + String.format(Locale("pt", "BR"), "%.2f", it) },
        perna.stake?.let {
            // Freebet: o valor é crédito da casa, não sai do saldo
            (if (perna.freebetId != null) "🎁 freebet " else "") + "R$ " + String.format(Locale("pt", "BR"), "%,.2f", it)
        }
    )
    if (numeros.isNotEmpty()) {
        Text(numeros.joinToString(" · "), color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
    }
}

// Print de uma casa em tela cheia; toque em qualquer lugar ou no X para fechar.
@Composable
private fun VisualizadorPrint(nome: String, onFechar: () -> Unit) {
    val context = LocalContext.current
    var imagem by remember(nome) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(nome) {
        imagem = withContext(Dispatchers.IO) {
            android.graphics.BitmapFactory.decodeFile(PrintsApostas.arquivo(context, nome).path)?.asImageBitmap()
        }
    }
    Dialog(onDismissRequest = onFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable { onFechar() }
        ) {
            imagem?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Print da aposta",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .verticalScroll(rememberScrollState())
                )
            } ?: Text(
                "Print não encontrado",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = onFechar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(Default.Close, contentDescription = "Fechar", tint = Color.White)
            }
        }
    }
}

// Escolha da casa onde caiu o resultado de uma operação com várias casas.
// Uma casa: toque direto nela. Duplo Green / Duplo Red: marca as casas e digita o valor total,
// que é dividido igualmente entre elas.
@Composable
private fun DialogoCasaDoResultado(
    aposta: Aposta,
    lucro: Double,
    onDismiss: () -> Unit,
    onConfirmar: (lucro: Double, casaLucro: String) -> Unit
) {
    val isGreen = lucro >= 0
    val corTexto = if (isGreen) Color(0xFF81C784) else Color(0xFFEF9A9A)
    val corFundo = if (isGreen) Color(0xFF2E7D32) else Color(0xFFB71C1C)
    val corBorda = if (isGreen) Color(0xFF2E5E2E) else Color(0xFF5E2E2E)
    val nomeDuplo = if (isGreen) "Duplo Green" else "Duplo Red"

    var modoDuplo by remember { mutableStateOf(false) }
    val casasMarcadas = remember { mutableStateListOf<String>() }
    var valorTotal by remember { mutableStateOf("%.2f".format(Locale("pt", "BR"), abs(lucro))) }
    var erro by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // Print aberto em tela cheia (nome do arquivo em PrintsApostas)
    var printAberto by remember { mutableStateOf<String?>(null) }

    printAberto?.let { VisualizadorPrint(it) { printAberto = null } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1F30)),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.10f)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier
                    // Com o detalhe de cada casa a janela pode passar da tela
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Cabeçalho ────────────────────────────────────
                Box(
                    modifier = Modifier
                        .background(corFundo.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (modoDuplo) nomeDuplo else if (isGreen) "Green" else "Red",
                        color = corTexto,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    when {
                        modoDuplo && isGreen -> "Quais casas bateram?"
                        modoDuplo -> "Quais casas perderam?"
                        isGreen -> "Em qual casa caiu o lucro?"
                        else -> "Em qual casa ficou o prejuízo?"
                    },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    aposta.descricao,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    maxLines = 1
                )

                Spacer(Modifier.height(2.dp))

                // ── Casas ────────────────────────────────────────
                aposta.casas.forEach { casa ->
                    val marcada = modoDuplo && casa in casasMarcadas
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (marcada) corFundo.copy(alpha = 0.18f) else Color(0xFF1E2338),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                0.5.dp,
                                if (marcada) corBorda else Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                if (modoDuplo) {
                                    if (marcada) casasMarcadas.remove(casa) else casasMarcadas.add(casa)
                                    erro = null
                                } else {
                                    onConfirmar(lucro, casa)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = if (modoDuplo) 4.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (modoDuplo) {
                            Checkbox(
                                checked = marcada,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = corTexto,
                                    uncheckedColor = Color.White.copy(alpha = 0.35f),
                                    checkmarkColor = Color(0xFF141824)
                                )
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        // Casa + o que foi apostado nela (para saber qual bateu)
                        val perna = aposta.pernaDaCasa(casa)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                casa,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            perna?.let { DetalhePerna(it) }
                        }
                        if (perna != null && PrintsApostas.existe(context, perna.print)) {
                            IconButton(onClick = { printAberto = perna.print }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Default.Image,
                                    contentDescription = "Ver print de $casa",
                                    tint = Color(0xFF7B97FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (!modoDuplo) {
                            Icon(
                                Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (!modoDuplo) {
                    // ── Botão Duplo Green / Duplo Red ────────────
                    OutlinedButton(
                        onClick = { modoDuplo = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = corFundo.copy(alpha = 0.12f),
                            contentColor = corTexto
                        ),
                        border = BorderStroke(0.5.dp, corBorda),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(nomeDuplo, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    // ── Valor total ──────────────────────────────
                    OutlinedTextField(
                        value = valorTotal,
                        onValueChange = { valorTotal = it; erro = null },
                        label = { Text(if (isGreen) "Lucro total (R$)" else "Prejuízo total (R$)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = erro != null,
                        supportingText = {
                            Text(
                                erro ?: "Dividido igualmente entre as casas marcadas.",
                                color = if (erro != null) Color(0xFFEF9A9A) else Color.White.copy(alpha = 0.4f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFF1E2338),
                            focusedContainerColor = Color(0xFF1E2338),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                            focusedBorderColor = Color(0xFF4F6FFF),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.45f),
                            focusedLabelColor = Color(0xFF4F6FFF),
                            unfocusedTextColor = Color.White.copy(alpha = 0.85f),
                            focusedTextColor = Color.White,
                            cursorColor = Color(0xFF4F6FFF)
                        )
                    )
                }

                // ── Rodapé ───────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (modoDuplo) modoDuplo = false else onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(if (modoDuplo) "Voltar" else "Cancelar", fontSize = 14.sp)
                    }

                    if (modoDuplo) {
                        Button(
                            onClick = {
                                val total = parseValorBR(valorTotal)
                                when {
                                    casasMarcadas.size < 2 -> erro = "Marque pelo menos 2 casas."
                                    total == null || total <= 0.0 -> erro = "Informe um valor válido."
                                    else -> {
                                        // Mantém a ordem original das casas
                                        val marcadas = aposta.casas.filter { it in casasMarcadas }
                                        val valorFinal = if (isGreen) total else -total
                                        onConfirmar(valorFinal, marcadas.joinToString(" | "))
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F6FFF)),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Confirmar", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
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
                val lucro = if (aposta.lucro != 0.0) aposta.lucro else aposta.retornoPotencial - aposta.valor
                append("📊 Lucro: R$ %.2f\n".format(lucro))
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
