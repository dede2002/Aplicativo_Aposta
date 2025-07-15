package com.example.apostas.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.apostas.MainActivity
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.ui.components.CampoCasaDeAposta
import com.example.apostas.ui.components.casasDeAposta
import com.example.apostas.ui.theme.ApostasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday

class CadastroSureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ApostasTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                var lucroDigitado by rememberSaveable { mutableStateOf("") }
                var descricaoExtra by rememberSaveable { mutableStateOf("") }
                var casa by rememberSaveable { mutableStateOf("") }
                var data by rememberSaveable {
                    mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
                }

                val calendar = remember { Calendar.getInstance() }
                val datePickerDialog = remember {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            data = "%02d/%02d/%04d".format(dayOfMonth, month + 1, year)
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                }

                val interactionSource = remember { MutableInteractionSource() }

                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(32.dp)
                    ) {
                        Text("Nova Aposta - Surebet ✅", style = MaterialTheme.typography.titleLarge)

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = lucroDigitado,
                            onValueChange = { lucroDigitado = it },
                            label = { Text("Lucro ou Prejuízo (sem sinal)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // NOVO CAMPO: Descrição extra
                        OutlinedTextField(
                            value = descricaoExtra,
                            onValueChange = { descricaoExtra = it },
                            label = { Text("Descrição adicional (opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        CampoCasaDeAposta(
                            valor = casa,
                            onValorChange = { casa = it },
                            sugestoes = casasDeAposta
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = data,
                            onValueChange = {},
                            label = { Text("Data (dd/MM/yyyy)") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Icon(Icons.Filled.CalendarToday, contentDescription = "Selecionar data")
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

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                val valorDouble = lucroDigitado.replace(',', '.').toDoubleOrNull()
                                if (valorDouble == null || valorDouble <= 0.0 || casa.isBlank()) return@Button

                                val descricaoFinal = "Surebet ✅" +
                                        if (descricaoExtra.isNotBlank()) " - ${descricaoExtra.trim()}" else ""

                                val aposta = Aposta(
                                    id = 0,
                                    descricao = descricaoFinal,
                                    casa = casa.trim(),
                                    valor = valorDouble,
                                    odds = 1.0,
                                    retornoPotencial = valorDouble,
                                    lucro = 0.0,
                                    data = data
                                )

                                scope.launch {
                                    val dao = AppDatabase.getDatabase(context).apostaDao()
                                    withContext(Dispatchers.IO) {
                                        dao.insert(aposta)
                                    }
                                    val intent = Intent(context, MainActivity::class.java)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    (context as? ComponentActivity)?.finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Salvar")
                        }
                    }
                }
            }
        }

    }
}

