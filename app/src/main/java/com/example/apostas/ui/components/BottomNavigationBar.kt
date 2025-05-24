package com.example.apostas.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.Default
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.apostas.MainActivity
import com.example.apostas.ui.EstatisticasActivity
import com.example.apostas.ui.SurebetActivity

@Composable
fun BottomNavigationBar(
    selected: String,
    context: Context
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == "apostas",
            onClick = {
                if (selected != "apostas") {
                    context.startActivity(Intent(context, MainActivity::class.java))
                }
            },
            icon = { Icon(Default.Home, contentDescription = "Apostas") },
            label = { Text("Apostas") }
        )

        NavigationBarItem(
            selected = selected == "estatisticas",
            onClick = {
                if (selected != "estatisticas") {
                    context.startActivity(Intent(context, EstatisticasActivity::class.java))
                }
            },
            icon = { Icon(Icons.Filled.BarChart, contentDescription = "Estatísticas") },
            label = { Text("Estatísticas") }
        )

        NavigationBarItem(
            selected = selected == "surebet",
            onClick = {
                if (selected != "surebet") {
                    context.startActivity(Intent(context, SurebetActivity::class.java))
                }
            },
            icon = { Icon(Icons.Filled.Lightbulb, contentDescription = "Surebet") },
            label = { Text("Surebet") }
        )
    }
}
