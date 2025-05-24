package com.example.apostas.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.*
import com.example.apostas.navigation.BottomNavItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import com.example.apostas.data.Aposta
import com.example.apostas.TelaPrincipal


@Composable
fun MainScreen(
    apostas: List<Aposta>,
    onNovaApostaClick: () -> Unit,
    onExcluirClick: (Aposta) -> Unit,
    onEditarClick: (Aposta) -> Unit,
    onAtualizarLucro: (Aposta) -> Unit,
) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    NavigationBarItem(
                        selected = navController.currentDestination?.route == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            when (item) {
                                BottomNavItem.APOSTAS -> Icon(Icons.Default.Home, contentDescription = "Apostas")
                                BottomNavItem.ESTATISTICAS -> Icon(Icons.Default.BarChart, contentDescription = "Estatísticas")
                                BottomNavItem.SUREBET -> Icon(Icons.Default.Lightbulb, contentDescription = "Surebet")
                            }
                        },
                        label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.APOSTAS.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.APOSTAS.route) {
                TelaPrincipal(
                    apostas = apostas,
                    onNovaApostaClick = onNovaApostaClick,
                    onExcluirClick = onExcluirClick,
                    onEditarClick = onEditarClick,
                    onAtualizarLucro = onAtualizarLucro
                )
            }
            composable(BottomNavItem.ESTATISTICAS.route) {
                TelaEstatisticas()
            }
            composable(BottomNavItem.SUREBET.route) {
                SurebetScreen()
            }
        }
    }
}
