package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.apostas.data.AppDatabase
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class EstatisticasActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ApostasTheme {
                TelaEstatisticas()
            }
        }
    }
}

@Composable
fun TelaEstatisticas() {
    val context = LocalContext.current
    var lucroTotal by remember { mutableStateOf(0.0) }
    var definidas by remember { mutableIntStateOf(0) }
    var indefinidas by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val dao = AppDatabase.getDatabase(context).apostaDao()
        val apostas = withContext(Dispatchers.IO) { dao.getAll() }

        lucroTotal = apostas.sumOf { it.lucro }
        definidas = apostas.count { it.lucro != 0.0 }
        indefinidas = apostas.count { it.lucro == 0.0 }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("📊 Relatório de Apostas", style = MaterialTheme.typography.headlineSmall)
        Text("Lucro Total: R$ $lucroTotal")

    }
}