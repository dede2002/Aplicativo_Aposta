package com.example.apostas.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TelaPrincipal(val rota: String, val label: String, val icon: ImageVector) {
    object Apostas : TelaPrincipal("apostas", "Apostas", Icons.Default.Home)
    object Estatisticas : TelaPrincipal("estatisticas", "Carteira", Icons.Default.BarChart)
    object Surebet : TelaPrincipal("surebet", "Surebet", Icons.Default.Lightbulb)
}