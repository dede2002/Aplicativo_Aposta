package com.example.apostas.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.ui.graphics.vector.ImageVector

sealed class RotasNavBar(val rota: String, val label: String, val icon: ImageVector) {
    object Apostas : RotasNavBar("apostas", "Apostas", Icons.Outlined.Home)
    object Estatisticas : RotasNavBar("estatisticas", "Estatísticas", Icons.Outlined.BarChart)
    object Surebet : RotasNavBar("surebet", "Surebet", Icons.Outlined.Calculate)
}