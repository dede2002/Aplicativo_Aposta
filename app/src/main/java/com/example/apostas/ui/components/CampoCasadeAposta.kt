package com.example.apostas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

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

    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface

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
                    .background(backgroundColor)
                    .padding(vertical = 4.dp)
            ) {
                items(sugestoesFiltradas) { sugestao ->
                    Text(
                        text = sugestao,
                        color = textColor,
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
    "Bet365", "Superbet", "Estrelabet", "Betfair", "Novibet", "ApostaGanha", "McGames","Matchbook",
    "Betano", "Faz1bet", "Betpix365", "VaiDeBet", "F12Bet", "B1Bet", "ApostouBet", "Uxbet","Betao",
    "Betfast", "Seubet", "HanzBet", "BeteEsporte", "Betsul", "VBet", "4Playbet", "Betaki", "Verabet","MMA","Stake","BolsadeAposta",
    "Lance de Sorte", "Segurobet", "Sportingbet", "KTO", "MaximaBet", "BateuBet", "BetdaSorte","Goldbet","BetBra","BetMGM",
    "CasaDeApostas", "7KBET", "JogueFácil", "TivoBet", "Brbet", "Esportiva", "CassinoBet", "Hiperbet","BrasildaSorte","Betnacional",
    "ApostaTudo", "Bullsbet", "Jogodeouro", "Bravobet", "BetdoJogo", "7Games","Pixbet","R7","Realsbet","Betvip","Multibet","Luvabet"
)