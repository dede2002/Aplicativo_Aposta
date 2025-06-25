package com.example.apostas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.DepositoManual
import com.example.apostas.data.LucroTotal
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.components.casasDeAposta
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.interaction.MutableInteractionSource

class CadastroApostaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apostaId = intent.getIntExtra("aposta_id", 0)

        setContent {
            ApostasTheme {
                val scope = rememberCoroutineScope()
                var apostaExistente by remember { mutableStateOf<Aposta?>(null) }

                LaunchedEffect(Unit) {
                    if (apostaId != 0) {
                            val dao = AppDatabase.getDatabase(applicationContext).apostaDao()
                        apostaExistente = withContext(Dispatchers.IO) {
                            dao.getById(apostaId)
                        }
                    }
                }

                if (apostaId == 0 || apostaExistente != null) {
                    FormularioCadastro(apostaExistente) { apostaParaSalvar ->
                        scope.launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            val apostaDao = db.apostaDao()
                            val depositoDao = db.depositoDao()
                            val saqueDao = db.saqueDao()
                            val lucroDao = db.LucroTotalDao()

                            withContext(Dispatchers.IO) {
                                if (apostaParaSalvar.id == 0) {
                                    val total = apostaDao.getTotalApostas()
                                    if (total >= 5000) {
                                        apostaDao.getApostaMaisAntiga()?.let { apostaDao.delete(it) }
                                    }

                                    // calcula saldo atual na casa
                                    val depositos = depositoDao.getAll().filter { it.casa == apostaParaSalvar.casa }.sumOf { it.valor }
                                    val saques = saqueDao.getAll().filter { it.casa == apostaParaSalvar.casa }.sumOf { it.valor }
                                    val lucros = apostaDao.getAll().filter { it.casa == apostaParaSalvar.casa && it.lucro != 0.0 }.sumOf { it.lucro }
                                    val valoresApostados = apostaDao.getAll().filter { it.casa == apostaParaSalvar.casa && it.lucro == 0.0 }.sumOf { it.valor }

                                    val saldoAtual = depositos + lucros - saques - valoresApostados

                                    apostaDao.insert(apostaParaSalvar)

                                    if (saldoAtual < apostaParaSalvar.valor) {
                                        val valorFaltante = apostaParaSalvar.valor - saldoAtual
                                        depositoDao.inserir(DepositoManual(casa = apostaParaSalvar.casa, valor = valorFaltante))
                                    }
                                } else {
                                    apostaDao.update(apostaParaSalvar)
                                    val antiga = apostaExistente!!
                                    val diferenca = apostaParaSalvar.valor - antiga.valor
                                    if (diferenca != 0.0) {
                                        depositoDao.inserir(DepositoManual(casa = apostaParaSalvar.casa, valor = diferenca))
                                    }
                                    val atual = lucroDao.get()?.valor ?: 0.0
                                    val atualizado = atual - antiga.lucro + apostaParaSalvar.lucro
                                    lucroDao.salvar(LucroTotal(valor = atualizado))
                                }
                            }

                            finish()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
fun FormularioCadastro(
    apostaExistente: Aposta?,
    onSalvar: (Aposta) -> Unit
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color(0xFF1E2235) else Color(0xFF1E2235)

    var descricao by rememberSaveable { mutableStateOf("") }
    var casa by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    var odds by rememberSaveable { mutableStateOf("") }
    var data by rememberSaveable {
        mutableStateOf(
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        )
    }

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

    LaunchedEffect(apostaExistente) {
        apostaExistente?.let {
            descricao = it.descricao
            casa = it.casa
            valor = "%.2f".format(it.valor).replace('.', ',')
            odds = "%.2f".format(it.odds).replace('.', ',')
            data = it.data
        }
    }

    val calendar = remember { Calendar.getInstance() }
    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedDate = "%02d/%02d/%04d".format(dayOfMonth, month + 1, year)
                data = selectedDate
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = descricao,
            onValueChange = { descricao = it },
            label = { Text("Descrição") },
            modifier = Modifier.fillMaxWidth()
        )

        CampoCasaDeAposta(
            valor = casa,
            onValorChange = { casa = it },
            sugestoes = casasDeAposta
        )

        OutlinedTextField(
            value = valor,
            onValueChange = { valor = it },
            label = { Text("Valor") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = odds,
            onValueChange = { odds = it },
            label = { Text("Odds") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        val interactionSource = remember { MutableInteractionSource() }

        OutlinedTextField(
            value = data,
            onValueChange = {},
            label = { Text("Data (dd/MM/yyyy)") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = "Selecionar data"
                )
            },
            interactionSource = interactionSource
        )

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                if (interaction is androidx.compose.foundation.interaction.PressInteraction.Release) {
                    datePickerDialog.show()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val valorDouble = valor.replace(',', '.').toDoubleOrNull() ?: 0.0
                val oddsDouble = odds.replace(',', '.').toDoubleOrNull() ?: 0.0
                val retorno = valorDouble * oddsDouble

                val aposta = Aposta(
                    id = apostaExistente?.id ?: 0,
                    descricao = descricao,
                    casa = casa,
                    valor = valorDouble,
                    odds = oddsDouble,
                    retornoPotencial = retorno,
                    lucro = apostaExistente?.lucro ?: 0.0,
                    data = data
                )

                onSalvar(aposta)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar")
        }
    }
}
