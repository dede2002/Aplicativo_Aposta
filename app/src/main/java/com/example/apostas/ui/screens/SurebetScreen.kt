package com.example.apostas.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
fun SurebetScreen() {
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color(0xFF1E2235) else Color(0xFF1E2235)
    val cardBackground = backgroundColor
    val buttonColor = if (isDarkTheme) Color(0xFF5B21B6) else Color(0xFF4F46E5)
    val successBorder = Color(0xFF22C55E)
    val errorBorder = Color(0xFFEF4444)

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = backgroundColor, darkIcons = !isDarkTheme)
        systemUiController.setNavigationBarColor(color = backgroundColor, darkIcons = !isDarkTheme)
    }

    var odd1 by remember { mutableStateOf("") }
    var odd2 by remember { mutableStateOf("") }
    var odd3 by remember { mutableStateOf("") }
    var aposta1 by remember { mutableStateOf("") }
    var resultadoCard by remember { mutableStateOf<@Composable (() -> Unit)?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Calculadora de Surebet",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = cardBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Gray,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = if (isDarkTheme) Color.White else Color.White,
                        focusedLabelColor = if (isDarkTheme) Color.White else Color.White,
                        unfocusedLabelColor = Color.LightGray,
                        focusedTextColor = if (isDarkTheme) Color.White else Color.White,
                        unfocusedTextColor = if (isDarkTheme) Color.White else Color.White
                    )


                    OutlinedTextField(
                        value = odd1,
                        onValueChange = { odd1 = it },
                        label = { Text("Odd 1") },
                        textStyle = TextStyle(color = if (isDarkTheme) Color.White else Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors
                    )

                    OutlinedTextField(
                        value = odd2,
                        onValueChange = { odd2 = it },
                        label = { Text("Odd 2") },
                        textStyle = TextStyle(color = if (isDarkTheme) Color.White else Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors
                    )

                    OutlinedTextField(
                        value = odd3,
                        onValueChange = { odd3 = it },
                        label = { Text("Odd 3 (opcional)") },
                        textStyle = TextStyle(color = if (isDarkTheme) Color.White else Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors
                    )

                    OutlinedTextField(
                        value = aposta1,
                        onValueChange = { aposta1 = it },
                        label = { Text("Valor Apostado na Odd 1 (R$)") },
                        textStyle = TextStyle(color = if (isDarkTheme) Color.White else Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors
                    )

                    Button(
                        onClick = {
                            val o1 = odd1.replace(',', '.').toDoubleOrNull()
                            val o2 = odd2.replace(',', '.').toDoubleOrNull()
                            val o3 = odd3.replace(',', '.').toDoubleOrNull()
                            val a1 = aposta1.replace(',', '.').toDoubleOrNull()

                            resultadoCard = if (o1 != null && o2 != null && a1 != null && o1 > 1 && o2 > 1 && a1 > 0) {
                                val margem = if (o3 != null && o3 > 1) {
                                    (1 / o1) + (1 / o2) + (1 / o3)
                                } else {
                                    (1 / o1) + (1 / o2)
                                }

                                val retorno = a1 * o1
                                val a2 = (retorno / o2)
                                val a3 = if (o3 != null && o3 > 1) (retorno / o3) else 0.0
                                val total = a1 + a2 + if (a3 > 0) a3 else 0.0

                                if (margem < 1) {
                                    val lucro = retorno - total
                                    val perc = (lucro / total) * 100

                                    {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF134E4A)),
                                            border = BorderStroke(1.dp, successBorder),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("✅ Há Surebet!", color = successBorder, fontWeight = FontWeight.Bold)
                                                Text("• Apostar na Odd (%.2f): R$ %.2f".format(o2, a2), color = Color.White)
                                                if (o3 != null && o3 > 1) {
                                                    Text("• Apostar na Odd (%.2f): R$ %.2f".format(o3, a3), color = Color.White)
                                                }
                                                Text("• Total Investido: R$ %.2f".format(total), color = Color.White)
                                                Text("• Retorno Garantido: R$ %.2f".format(retorno), color = Color.White)
                                                Text("• Lucro: R$ %.2f (%.2f%%)".format(lucro, perc), color = successBorder, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    val menorRetorno = listOfNotNull(
                                        a1 * o1,
                                        a2.takeIf { it > 0 }?.let { it * o2 },
                                        if (a3 > 0) a3 * o3!! else null
                                    ).minOrNull() ?: 0.0
                                    val prejuizo = total - menorRetorno
                                    val perc = (prejuizo / total) * 100

                                    {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF581C1C)),
                                            border = BorderStroke(1.dp, errorBorder),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("❌ Não há Surebet", color = errorBorder, fontWeight = FontWeight.Bold)
                                                Text("• Total Investido: R$ %.2f".format(total), color = Color.White)
                                                Text("• Retorno Máximo Estimado: R$ %.2f".format(menorRetorno), color = Color.White)
                                                Text("• Prejuízo: R$ %.2f (%.2f%%)".format(prejuizo, perc), color = errorBorder, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF581C1C)),
                                        border = BorderStroke(1.dp, errorBorder),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text("⚠️ Preencha os campos corretamente.", color = errorBorder, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        ,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = "Calcular", tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Calcular", color = Color.White)
                    }
                }

                resultadoCard?.invoke()
            }
        }
    }
}
