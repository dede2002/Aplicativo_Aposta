package com.example.apostas.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.ui.graphics.vector.ImageVector

sealed class RotasNavBar(val rota: String, val label: String, val icon: ImageVector) {
    object Apostas : RotasNavBar("apostas", "Apostas", Icons.Default.Home)
    object Estatisticas : RotasNavBar("estatisticas", "Estatísticas", Icons.Default.BarChart)
    object Surebet : RotasNavBar("surebet", "Surebet", Icons.Default.Lightbulb)
}