package com.example.apostas

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.ui.CadastroApostaActivity
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.apostas.ui.EstatisticasActivity



class MainActivity : ComponentActivity() {
    // Lista reativa de apostas (estado compartilhado com Compose)
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
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        // Carrega as apostas no início
        carregarApostasDoBanco()
    }

    override fun onResume() {
        super.onResume()
        // Recarrega quando volta da tela de cadastro
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

@Composable
fun TelaPrincipal(
    apostas: List<Aposta>,
    onNovaApostaClick: () -> Unit,
    onExcluirClick: (Aposta) -> Unit,
    onEditarClick: (Aposta) -> Unit,
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
                CardAposta(aposta = aposta, onExcluirClick = onExcluirClick, onEditarClick = onEditarClick)
            }
        }
    }
}


@Composable
fun CardAposta(
    aposta: Aposta,
    onExcluirClick: (Aposta) -> Unit,
    onEditarClick: (Aposta) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            // Conteúdo do card
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)) {

                Text(text = "Aposta: ${aposta.descricao}")
                Text(text = "Casa: ${aposta.casa}")
                Text(text = "Valor: R$ ${aposta.valor}")
                Text(text = "Odds: ${aposta.odds}")
                Text(text = "Status: ${aposta.status}")
                Text(text = "Retorno Potencial: R$ ${aposta.retornoPotencial}")
                Text(text = "Lucro: R$ ${aposta.lucro}")

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            }

            // Ícone de edição sobreposto no topo direito
            IconButton(
                onClick = { onEditarClick(aposta) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar"
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





