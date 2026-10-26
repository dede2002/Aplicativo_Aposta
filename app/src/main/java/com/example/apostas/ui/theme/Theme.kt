package com.example.apostas.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Tema fixo do Tips: preto e laranja, sempre escuro. Antes usava as cores dinâmicas do papel de parede
// (Android 12+), o que deixava diálogos e menus padrão em azul-claro, fora da identidade do app.
private val EsquemaTips = darkColorScheme(
    primary = TipsCores.Laranja,
    onPrimary = Color.Black,
    primaryContainer = TipsCores.LaranjaEscuro,
    onPrimaryContainer = Color.White,
    secondary = TipsCores.LaranjaClaro,
    onSecondary = Color.Black,
    background = TipsCores.Fundo,
    onBackground = TipsCores.Texto,
    surface = TipsCores.Superficie,
    onSurface = TipsCores.Texto,
    surfaceVariant = Color(0xFF222226),
    onSurfaceVariant = TipsCores.TextoSecundario,
    surfaceContainer = TipsCores.Superficie,
    surfaceContainerHigh = TipsCores.Superficie,
    surfaceContainerHighest = Color(0xFF222226),
    outline = TipsCores.VidroBorda,
    error = TipsCores.Vermelho
)

@Composable
fun ApostasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaTips,
        typography = Typography,
        content = content
    )
}
