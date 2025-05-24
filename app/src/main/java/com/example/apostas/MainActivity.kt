package com.example.apostas


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
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
import com.example.apostas.ui.MainScreen



class MainActivity : ComponentActivity() {

    private val apostas = mutableStateListOf<Aposta>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ApostasTheme {
                MainScreen(
                    apostas = apostas,
                    onNovaApostaClick = {
                        startActivity(android.content.Intent(this, com.example.apostas.ui.CadastroApostaActivity::class.java))
                    },
                    onExcluirClick = { aposta ->
                        MainScope().launch {
                            val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
                            withContext(Dispatchers.IO) {
                                dao.delete(aposta)
                            }
                            carregarApostasDoBanco()
                        }
                    },
                    onEditarClick = { aposta ->
                        val intent = android.content.Intent(this, com.example.apostas.ui.CadastroApostaActivity::class.java)
                        intent.putExtra("aposta_id", aposta.id)
                        startActivity(intent)
                    },
                    onAtualizarLucro = { apostaAtualizada ->
                        MainScope().launch {
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
            }
        }

        carregarApostasDoBanco()
    }

    override fun onResume() {
        super.onResume()
        carregarApostasDoBanco()
    }

    private fun carregarApostasDoBanco() {
        MainScope().launch {
            val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
            val resultado = withContext(Dispatchers.IO) {
                dao.getAll()
            }
            apostas.clear()
            apostas.addAll(resultado)
        }
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
                CardAposta(
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
            // Dados principais da aposta
            Text("📌 ${aposta.descricao}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🏠 Casa: ${aposta.casa}")
            Text("💸 Valor: R$ %.2f".format(aposta.valor))
            Text("📈 Odds: ${aposta.odds}")
            Text("💰 Retorno Potencial: R$ %.2f".format(aposta.retornoPotencial))
            Text("📊 Lucro: R$ %.2f".format(aposta.lucro))

            Spacer(modifier = Modifier.height(12.dp))

            // Botões de resultado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        val lucro = aposta.retornoPotencial - aposta.valor
                        onAtualizarLucro(aposta.copy(lucro = lucro))
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Green")
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        val prejuizo = -aposta.valor
                        onAtualizarLucro(aposta.copy(lucro = prejuizo))
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Red")
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        onAtualizarLucro(aposta.copy(lucro = 0.0))
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Anulada")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ações (Editar / Excluir)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { onEditarClick(aposta) }) {
                    Icon(imageVector = Default.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = { showDialog = true }) {
                    Icon(imageVector = Default.Delete, contentDescription = "Excluir")
                }
            }
        }
    }

    // Diálogo de confirmação
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja excluir esta aposta?") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onExcluirClick(aposta)
                }) {
                    Text("Sim")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
