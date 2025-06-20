package com.example.apostas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    val sugestoesFiltradas = if (valor.isBlank()) {
        emptyList()
    } else {
        sugestoes.filter { it.startsWith(valor, ignoreCase = true) }
    }




    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = valor,
            onValueChange = {
                onValorChange(it)
                showSuggestions = it.isNotBlank()
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (showSuggestions && sugestoesFiltradas.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(vertical = 4.dp)
            ) {
                items(sugestoesFiltradas) { sugestao ->
                    Text(
                        text = sugestao,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onValorChange(sugestao)
                                showSuggestions = false
                            }
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}
val casasDeAposta = listOf(
    "Bet365", "Superbet", "Estrelabet", "Betesporte", "Novibet", "ApostaGanha", "McGames",
    "Betfast", "Faz1bet", "Betpix365", "VaiDeBet", "F12Bet", "B1Bet", "ApostouBet", "Uxbet",
    "Betano", "Seubet", "HanzBet", "Betfair", "Betsul", "VBet", "4Playbet", "Betaki", "Verabet",
    "Lance de Sorte", "Segurobet", "Sportingbet", "KTO", "MaximaBet", "BateuBet", "BetdaSorte",
    "CasaDeApostas", "7KBET", "JogueFácil", "TivoBet", "Brbet", "Esportiva", "CassinoBet", "Hiperbet","BrasildaSorte","Betnacional",
    "ApostaTudo", "Bullsbet", "Jogodeouro", "Bravobet", "BetdoJogo", "Betão", "7Games","Pixbet","R7","Realsbet","Betvip","Multibet","Luvabet"
)