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
                                        val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
                                        withContext(Dispatchers.IO) {
                                            dao.delete(aposta)
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
                                        val dao = db.apostaDao()
                                        val lucroTotalDao = db.LucroTotalDao()

                                        withContext(Dispatchers.IO) {
                                            val apostaAntiga = dao.getById(apostaAtualizada.id)
                                            val lucroAnterior = apostaAntiga?.lucro ?: 0.0
                                            val lucroNovo = apostaAtualizada.lucro
                                            val atual = lucroTotalDao.get()?.valor ?: 0.0
                                            val atualizado = atual - lucroAnterior + lucroNovo
                                            lucroTotalDao.salvar(LucroTotal(valor = atualizado))
                                            dao.delete(apostaAtualizada.copy())
                                            dao.insert(apostaAtualizada)
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
    var filtroSelecionado by remember { mutableStateOf(FiltroAposta.TODAS) }

    val context = LocalContext.current

    val apostasFiltradas = when (filtroSelecionado) {
        FiltroAposta.TODAS -> apostas
        FiltroAposta.RESOLVIDAS -> apostas.filter { it.lucro != 0.0 }
        FiltroAposta.EM_ABERTO -> apostas.filter { it.lucro == 0.0 }
        FiltroAposta.GREENS -> apostas.filter { it.lucro > 0.0 }
        FiltroAposta.REDS -> apostas.filter { it.lucro < 0.0 }
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
            onClick = { compartilharApostas(context = context, apostas) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Compartilhar Apostas")
        }


        // ✅ Filtro com LazyRow para rolagem horizontal
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(FiltroAposta.entries) { filtro ->
                FilterChip(
                    selected = filtroSelecionado == filtro,
                    onClick = { filtroSelecionado = filtro },
                    label = { Text(filtro.label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(apostasFiltradas) { aposta ->
                val context = LocalContext.current
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

@Composable
fun CardAposta(
    context: Context,
    aposta: Aposta,
    onExcluirClick: (Aposta) -> Unit,
    onEditarClick: (Aposta) -> Unit,
    onAtualizarLucro: (Aposta) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("📌 ${aposta.descricao}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🏠 Casa: ${aposta.casa}")
            Text("💸 Valor: R$ %.2f".format(aposta.valor))
            Text("📈 Odds: ${aposta.odds}")
            Text("💰 Retorno Potencial: R$ %.2f".format(aposta.retornoPotencial))
            Text("📊 Lucro: R$ %.2f".format(aposta.lucro))

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

            // Ícones de ações
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
                    """.trimIndent().format(aposta.valor, aposta.retornoPotencial, aposta.lucro)

                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, mensagem)
                        type = "text/plain"
                        setPackage("com.whatsapp") // tenta abrir o WhatsApp
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
                    Icon(Default.Share, contentDescription = "Compartilhar")
                }

                IconButton(onClick = { onEditarClick(aposta) }) {
                    Icon(imageVector = Default.Edit, contentDescription = "Editar")
                }

                IconButton(onClick = { showDialog = true }) {
                    Icon(imageVector = Default.Delete, contentDescription = "Excluir")
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
            append("📊 Lucro: R$ %.2f\n".format(aposta.lucro))
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

