package com.example.apostas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


@Composable
fun CampoCasaDeAposta(
    label: String = "Casa de Aposta",
    valor: String,
    onValorChange: (String) -> Unit,
    sugestoes: List<String>
) {
    var showSuggestions by remember { mutableStateOf(false) }

    val sugestoesFiltradas = if (valor.isBlank()) emptyList()
    else sugestoes.filter { it.startsWith(valor, ignoreCase = true) }

    // Cores fixas no dark theme
    val bgField   = Color(0xFF1E2338)
    val border    = Color.White.copy(alpha = 0.12f)
    val borderFocus = Color(0xFF4F6FFF)
    val labelColor  = Color.White.copy(alpha = 0.45f)
    val textColor   = Color.White.copy(alpha = 0.85f)

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = {
                onValorChange(it)
                showSuggestions = it.isNotBlank()
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = bgField,
                focusedContainerColor = bgField,
                unfocusedBorderColor = border,
                focusedBorderColor = borderFocus,
                unfocusedLabelColor = labelColor,
                focusedLabelColor = borderFocus,
                unfocusedTextColor = textColor,
                focusedTextColor = Color.White,
                cursorColor = borderFocus
            )
        )

        if (showSuggestions && sugestoesFiltradas.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252B40), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(vertical = 4.dp)
            ) {
                sugestoesFiltradas.forEach { sugestao ->
                    Text(
                        text = sugestao,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onValorChange(sugestao)
                                showSuggestions = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                }
            }
        }
    }
}



val casasDeAposta = listOf(
    "Bet365", "Superbet", "Estrelabet", "Betfair", "Novibet", "ApostaGanha", "McGames","Matchbook", "Betespecial", "Reidopitaco",
    "Betano", "Faz1bet", "Betpix365", "VaiDeBet", "F12Bet", "B1Bet", "ApostouBet", "Uxbet","Betao",
    "Betfast", "Seubet", "HanzBet", "BeteEsporte", "Betsul", "VBet", "4Playbet", "Betaki", "Verabet","MMA","Stake","BolsadeAposta",
    "Lance de Sorte", "Segurobet", "Sportingbet", "KTO", "MaximaBet", "BateuBet", "BetdaSorte","Goldbet","BetBra","BetMGM",
    "CasaDeApostas", "7KBET", "JogueFácil", "TivoBet", "Brbet", "Esportiva", "CassinoBet", "Hiperbet","BrasildaSorte","Betnacional",
    "ApostaTudo", "Bullsbet", "Jogodeouro", "Bravobet", "BetdoJogo", "7Games","Pixbet","R7","Realsbet","Betvip","Multibet","Luvabet"
)