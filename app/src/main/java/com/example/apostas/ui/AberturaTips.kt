package com.example.apostas.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.apostas.ui.theme.TipsCores
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

// Abertura do app no estilo da Netflix: o "T" do Tips aparece com brilho, uma faixa de luz passa por ele
// e a câmera "mergulha" na haste do T até a tela ficar laranja, que se dissolve revelando o app.
// Mostrada só quando o app abre do zero (não ao girar a tela nem ao voltar de outra tela).
@Composable
fun AberturaTips(onFim: () -> Unit) {
    val aparecer = remember { Animatable(0f) }   // 0 → 1: o T surge
    val brilho = remember { Animatable(-0.6f) }  // faixa de luz atravessando o T
    val mergulho = remember { Animatable(0f) }   // 0 → 1: zoom para dentro da haste
    val sumir = remember { Animatable(1f) }      // 1 → 0: a tela laranja se dissolve

    LaunchedEffect(Unit) {
        aparecer.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        brilho.animateTo(1.6f, tween(550, easing = LinearEasing))
        delay(80)
        val zoom = async { mergulho.animateTo(1f, tween(700, easing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f))) }
        zoom.await()
        sumir.animateTo(0f, tween(380, easing = LinearEasing))
        onFim()
    }

    Canvas(Modifier.fillMaxSize()) {
        val alfaGeral = sumir.value
        // Fundo: preto, e laranja no fim do mergulho
        drawRect(Color.Black.copy(alpha = alfaGeral))

        // O "T" (mesmas proporções do ícone: viewport 24, barra de cima e haste)
        val lado = size.minDimension * 0.34f
        val escalaBase = 0.86f + 0.14f * aparecer.value
        val zoom = 1f + 60f * mergulho.value * mergulho.value
        val unidade = lado / 24f * escalaBase
        val origem = Offset(center.x - 12f * unidade, center.y - 12f * unidade)
        fun r(x1: Float, y1: Float, x2: Float, y2: Float) =
            Rect(origem.x + x1 * unidade, origem.y + y1 * unidade, origem.x + x2 * unidade, origem.y + y2 * unidade)
        val barra = r(3.25f, 2f, 20.75f, 6.17f)
        val haste = r(9.62f, 6.17f, 14.48f, 22f)
        val formaT = Path().apply { addRect(barra); addRect(haste) }
        // O mergulho vai para o meio da haste, que acaba enchendo a tela de laranja
        val alvo = Offset(haste.center.x, haste.top + haste.height * 0.45f)

        withTransform({ scale(zoom, zoom, pivot = alvo) }) {
            // Brilho atrás do T
            drawCircle(
                Brush.radialGradient(
                    listOf(TipsCores.Laranja.copy(alpha = 0.35f * aparecer.value * alfaGeral), Color.Transparent),
                    center = center, radius = lado * 1.3f
                ),
                radius = lado * 1.3f, center = center
            )
            val alfaT = aparecer.value * alfaGeral
            drawPath(formaT, TipsCores.Laranja.copy(alpha = alfaT))
            // Faixa de luz diagonal passando por dentro do T
            clipPath(formaT) {
                val x = origem.x + lado * brilho.value
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.45f * alfaT), Color.Transparent),
                        start = Offset(x - lado * 0.25f, origem.y),
                        end = Offset(x + lado * 0.25f, origem.y + lado)
                    ),
                    topLeft = origem, size = Size(lado, lado)
                )
            }
        }
        // Depois do mergulho a tela inteira está laranja; ela some revelando o app
        if (mergulho.value >= 1f) drawRect(TipsCores.Laranja.copy(alpha = alfaGeral))
    }
}
