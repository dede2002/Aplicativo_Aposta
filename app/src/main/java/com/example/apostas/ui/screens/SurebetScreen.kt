package com.example.apostas.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import androidx.compose.foundation.isSystemInDarkTheme


// MUDANÇA: A classe Failure agora carrega os dados do prejuízo
sealed class SurebetResult {
    data class Success(
        val aposta1: Double,
        val aposta2: Double,
        val aposta3: Double?,
        val totalInvestido: Double,
        val retorno: Double,
        val lucro: Double,
        val porcentagem: Double
    ) : SurebetResult()

    data class Failure(
        val totalInvestido: Double,
        val prejuizo: Double,
        val porcentagem: Double,
        val aposta1: Double,
        val aposta2: Double,
        val aposta3: Double? = null
    ) : SurebetResult()

    data object InvalidInput : SurebetResult()
}


fun formatCurrency(value: Double): String {
    return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
}


@Composable
fun SurebetScreen() {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF2A2058), Color(0xFF681A2B))
    )

    var odd1 by remember { mutableStateOf("") }
    var odd2 by remember { mutableStateOf("") }
    var odd3 by remember { mutableStateOf("") }
    var valorApostado1 by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<SurebetResult?>(null) }

    val cardColor = Color(0xFFEBE6F0)
    val primaryTextColor = Color(0xFF392D69)
    val buttonColor = Color(0xFF5B4BD8)
    val backgroundColor = Color.Transparent

    val isDarkTheme = isSystemInDarkTheme()
    val colornavbar= if (isDarkTheme) Color.Black else Color.Black


    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = colornavbar, darkIcons = !isDarkTheme)
        systemUiController.setNavigationBarColor(color = colornavbar, darkIcons = !isDarkTheme)
    }


    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .systemBarsPadding(),
        containerColor = backgroundColor
    )  { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 400.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Calculadora de Surebet",
                            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            InputColumn(modifier = Modifier.weight(1f), label = "Odd 1", value = odd1, onValueChange = { odd1 = it }, placeholder = "Odd 1")
                            InputColumn(modifier = Modifier.weight(1f), label = "Odd 2", value = odd2, onValueChange = { odd2 = it }, placeholder = "Odd 2")
                            InputColumn(modifier = Modifier.weight(1f), label = "Opcional", value = odd3, onValueChange = { odd3 = it }, placeholder = "Odd 3")
                        }
                        InputColumn(label = "Valor Apostado na Odd 1 (R$)", value = valorApostado1, onValueChange = { valorApostado1 = it }, placeholder = "Valor")
                        Button(
                            onClick = {
                                val o1 = odd1.replace(',', '.').toDoubleOrNull()
                                val o2 = odd2.replace(',', '.').toDoubleOrNull()
                                val o3 = odd3.replace(',', '.').toDoubleOrNull()
                                val a1 = valorApostado1.replace(',', '.').toDoubleOrNull()

                                if (o1 != null && o2 != null && a1 != null && o1 > 1 && o2 > 1 && a1 > 0) {
                                    val isThreeWay = o3 != null && o3 > 1
                                    val margem = (1 / o1) + (1 / o2) + (if (isThreeWay) (1 / o3) else 0.0)
                                    val retorno = a1 * o1
                                    val a2 = retorno / o2
                                    val a3 = if (isThreeWay) retorno / o3 else null
                                    val totalInvestido = a1 + a2 + (a3 ?: 0.0)

                                    if (margem < 1) {
                                        val lucro = retorno - totalInvestido
                                        val perc = (lucro / totalInvestido) * 100
                                        result = SurebetResult.Success(a1, a2, a3, totalInvestido, retorno, lucro, perc)
                                    } else {
                                        val prejuizo = totalInvestido - retorno
                                        val percPrejuizo = (prejuizo / totalInvestido) * 100
                                        result = SurebetResult.Failure(totalInvestido, prejuizo, percPrejuizo, a1, a2, a3)
                                    }
                                } else {
                                    result = SurebetResult.InvalidInput
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                        ) {
                            Text("Calcular Surebet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                AnimatedVisibility(visible = result != null) {
                    result?.let { NewResultCard(it) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun InputColumn(modifier: Modifier = Modifier, label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = TextStyle(color = Color(0xFF6C6399), fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        CustomTextField(value = value, onValueChange = onValueChange, placeholder = placeholder)
    }
}

@Composable
fun CustomTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Black, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isEmpty() && !isFocused) {
                    Text(text = placeholder, color = Color.LightGray, fontSize = 16.sp)
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun NewResultCard(result: SurebetResult) {
    val successColor = Color(0xFF16A34A)
    val errorColor = Color(0xFFD32F2F)
    Card(
        modifier = Modifier.widthIn(max = 400.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEBE6F0)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (result) {
                is SurebetResult.Success -> {
                    ResultTitle(text = "Surebet Encontrada!", color = successColor, icon = Icons.Default.CheckCircle)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Distribua sua aposta da seguinte forma:", fontWeight = FontWeight.Bold)
                    ResultRow("Apostar na Odd 1:", formatCurrency(result.aposta1))
                    ResultRow("Apostar na Odd 2:", formatCurrency(result.aposta2))
                    result.aposta3?.let {
                        ResultRow("Apostar na Odd 3:", formatCurrency(it))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ResultRow("Total Investido:", formatCurrency(result.totalInvestido))
                    ResultRow("Retorno Garantido:", formatCurrency(result.retorno))
                    ResultRow("Lucro Líquido:", "${formatCurrency(result.lucro)} (+${"%.2f".format(result.porcentagem)}%)", contentColor = successColor)
                }
                // MUDANÇA: Exibindo os detalhes do prejuízo
                is SurebetResult.Failure -> {
                    ResultTitle(text = "Não há Surebet", color = errorColor, icon = Icons.Default.Warning)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Distribua sua aposta da seguinte forma (para minimizar prejuízo):", fontWeight = FontWeight.Bold)
                    ResultRow("Apostar na Odd 1:", formatCurrency(result.aposta1))
                    ResultRow("Apostar na Odd 2:", formatCurrency(result.aposta2))
                    result.aposta3?.let {
                        ResultRow("Apostar na Odd 3:", formatCurrency(it))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ResultRow("Total Investido:", formatCurrency(result.totalInvestido))
                    ResultRow(
                        label = "Prejuízo Estimado:",
                        value = "-${formatCurrency(result.prejuizo.absoluteValue)}",
                        contentColor = errorColor
                    )
                }

                is SurebetResult.InvalidInput -> {
                    ResultTitle(text = "Erro", color = errorColor, icon = Icons.Default.Warning)
                    Text("Por favor, preencha os campos corretamente.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun ResultTitle(text: String, color: Color, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = text, tint = color)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ResultRow(label: String, value: String, contentColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = contentColor)
    }
}
