package com.example.apostas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.ui.CadastroApostaActivity
import com.example.apostas.ui.EstatisticasActivity
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import com.example.apostas.data.LucroTotal
import com.example.apostas.ui.DepositoManualActivity


class MainActivity : ComponentActivity() {
    private val apostas = mutableStateListOf<Aposta>()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ApostasTheme {
                var expanded by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Minhas Apostas") },
                            actions = {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Ver Estatísticas") },
                                        onClick = {
                                            expanded = false
                                            val intent = Intent(this@MainActivity, EstatisticasActivity::class.java)
                                            startActivity(intent)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Fazer Depósito Manual") },
                                    onClick = {
                                        expanded = false
                                        val intent = Intent(this@MainActivity, DepositoManualActivity::class.java)
                                        startActivity(intent)
                                    }
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    TelaPrincipal(
                        apostas = apostas,
                        onNovaApostaClick = {
                            val intent = Intent(this, CadastroApostaActivity::class.java)
                            startActivity(intent)
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
                            val intent = Intent(this, CadastroApostaActivity::class.java)
                            intent.putExtra("aposta_id", aposta.id)
                            startActivity(intent)
                        },
                        onAtualizarLucro = { apostaAtualizada ->
                            MainScope().launch {
                                val db = AppDatabase.getDatabase(applicationContext)
                                val dao = db.apostaDao()
                                val lucroTotalDao = db.LucroTotalDao()

                                withContext(Dispatchers.IO) {
                                    // 1. Pega a aposta original do banco (antes de excluir)
                                    val apostaAntiga = dao.getById(apostaAtualizada.id)

                                    // 2. Calcula o lucro total corrigido
                                    val lucroAnterior = apostaAntiga?.lucro ?: 0.0
                                    val lucroNovo = apostaAtualizada.lucro
                                    val atual = lucroTotalDao.get()?.valor ?: 0.0
                                    val atualizado = atual - lucroAnterior + lucroNovo

                                    // 3. Salva o novo total de lucro
                                    lucroTotalDao.salvar(LucroTotal(valor = atualizado))

                                    // 4. Atualiza a aposta
                                    dao.delete(apostaAtualizada.copy())
                                    dao.insert(apostaAtualizada)
                                }

                                carregarApostasDoBanco()
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
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

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(apostas) { aposta ->
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
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart) // <-- garante alinhamento correto dentro do Box
                    .padding(8.dp)
            ) {
                Text("Aposta: ${aposta.descricao}")
                Text("Casa: ${aposta.casa}")
                Text("Valor: R$ ${aposta.valor}")
                Text("Odds: ${aposta.odds}")
                Text("Retorno Potencial: R$ ${aposta.retornoPotencial}")
                Text("Lucro: R$ ${aposta.lucro}")

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val lucro = aposta.retornoPotencial - aposta.valor
                            onAtualizarLucro(aposta.copy(lucro = lucro))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Green")
                    }

                    Button(
                        onClick = {
                            val prejuizo = -aposta.valor
                            onAtualizarLucro(aposta.copy(lucro = prejuizo))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Red")
                    }
                }
            }

            // Ícone de edição (canto superior direito)
            IconButton(
                onClick = { onEditarClick(aposta) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar"
                )
            }

            // Ícone de lixeira (canto inferior direito)
            IconButton(
                onClick = { showDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir",

                    )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Confirmar exclusão") },
            text = { Text("Tem certeza que deseja excluir esta aposta?") },
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