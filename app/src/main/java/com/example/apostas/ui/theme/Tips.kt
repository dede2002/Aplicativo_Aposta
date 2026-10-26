package com.example.apostas.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Identidade visual do Tips: preto e laranja (o "T" do ícone), cards de vidro, números grandes.
// Use estes componentes nas telas em vez de repetir cores e formas em cada arquivo.

object TipsCores {
    val Fundo = Color(0xFF09090B)
    // Laranja do Tips (mais escuro a pedido do dono, 26/10); o claro é só para texto de link sobre o preto
    val Laranja = Color(0xFFEA580C)
    val LaranjaClaro = Color(0xFFF97316)
    val LaranjaEscuro = Color(0xFF9A3412)
    val Texto = Color(0xFFF5F5F5)
    val TextoSecundario = Color.White.copy(alpha = 0.55f)
    val TextoApagado = Color.White.copy(alpha = 0.35f)
    val Vidro = Color.White.copy(alpha = 0.05f)
    val VidroBorda = Color.White.copy(alpha = 0.09f)
    val Campo = Color.White.copy(alpha = 0.06f)
    // Campo de texto opaco (dentro de diálogos e cards)
    val CampoOpaco = Color(0xFF1F1F23)
    val Verde = Color(0xFF4ADE80)
    val Vermelho = Color(0xFFF87171)
    // Superfície opaca para diálogos e menus (vidro sobre vidro fica ilegível)
    val Superficie = Color(0xFF17171A)
}

// Números alinhados (todos os dígitos com a mesma largura): valores não "dançam" na tela
val NumerosAlinhados = TextStyle(fontFeatureSettings = "tnum")

// Fundo preto com um brilho laranja suave no alto, como luz atrás do vidro
fun Modifier.fundoTips(): Modifier = this
    .background(TipsCores.Fundo)
    .drawBehind {
        drawRect(
            Brush.radialGradient(
                colors = listOf(TipsCores.Laranja.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.85f, -size.height * 0.02f),
                radius = size.width * 1.1f
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(TipsCores.LaranjaEscuro.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(0f, size.height * 0.75f),
                radius = size.width * 0.9f
            )
        )
    }

// Card de vidro: fundo translúcido com leve degradê e borda fina iluminada em cima
@Composable
fun CartaoVidro(
    modifier: Modifier = Modifier,
    cantos: Dp = 24.dp,
    destaque: Color? = null,
    onClick: (() -> Unit)? = null,
    conteudo: @Composable ColumnScope.() -> Unit
) {
    val forma = RoundedCornerShape(cantos)
    Column(
        modifier = modifier
            .clip(forma)
            .background(
                Brush.verticalGradient(
                    listOf(
                        (destaque ?: Color.White).copy(alpha = if (destaque != null) 0.10f else 0.07f),
                        Color.White.copy(alpha = 0.025f)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            (destaque ?: Color.White).copy(alpha = if (destaque != null) 0.35f else 0.16f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                ),
                forma
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
        content = conteudo
    )
}

// Ícone dentro de um círculo translúcido (substitui os emojis)
@Composable
fun IconeCirculo(
    icone: ImageVector,
    modifier: Modifier = Modifier,
    cor: Color = TipsCores.Laranja,
    tamanho: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    descricao: String? = null
) {
    Box(
        modifier = modifier
            .size(tamanho)
            .clip(CircleShape)
            .background(cor.copy(alpha = 0.14f))
            .border(1.dp, cor.copy(alpha = 0.25f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icone, contentDescription = descricao, tint = cor, modifier = Modifier.size(tamanho * 0.48f))
    }
}

// "R$ 1.250,00" com o R$ pequeno e o número grande
@Composable
fun ValorGrande(
    valor: Double,
    modifier: Modifier = Modifier,
    cor: Color = TipsCores.Texto,
    tamanho: TextUnit = 34.sp,
    mostrarSinal: Boolean = false
) {
    val sinal = if (mostrarSinal && valor > 0) "+" else if (valor < 0) "−" else ""
    val numero = String.format(Locale("pt", "BR"), "%,.2f", kotlin.math.abs(valor))
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            "${sinal}R$",
            color = cor.copy(alpha = 0.7f),
            fontSize = tamanho * 0.42f,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = (tamanho.value * 0.16f).dp, end = 4.dp)
        )
        Text(
            numero,
            color = cor,
            fontSize = tamanho,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5).sp,
            style = NumerosAlinhados
        )
    }
}

// Botão principal: pílula laranja com texto preto
@Composable
fun BotaoTips(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icone: ImageVector? = null,
    habilitado: Boolean = true,
    secundario: Boolean = false,
    // Menor, para usar dentro de cards (Ajustar, Sacar...)
    compacto: Boolean = false
) {
    val forma = RoundedCornerShape(50)
    val fundo = when {
        !habilitado -> Color.White.copy(alpha = 0.06f)
        secundario -> Color.White.copy(alpha = 0.07f)
        else -> TipsCores.Laranja
    }
    val corTexto = when {
        !habilitado -> TipsCores.TextoApagado
        secundario -> TipsCores.Texto
        else -> Color.Black
    }
    Row(
        modifier = modifier
            .clip(forma)
            .background(fundo)
            .then(if (secundario) Modifier.border(1.dp, TipsCores.VidroBorda, forma) else Modifier)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = if (compacto) 14.dp else 18.dp, vertical = if (compacto) 8.dp else 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icone?.let {
            Icon(it, contentDescription = null, tint = corTexto, modifier = Modifier.size(if (compacto) 15.dp else 18.dp))
            Spacer(Modifier.width(if (compacto) 6.dp else 8.dp))
        }
        Text(texto, color = corTexto, fontSize = if (compacto) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// Chip de filtro: pílula de vidro; selecionado fica laranja
@Composable
fun ChipTips(
    texto: String,
    selecionado: Boolean,
    onClick: () -> Unit,
    icone: ImageVector? = null
) {
    val forma = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(forma)
            .background(if (selecionado) TipsCores.Laranja else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selecionado) TipsCores.Laranja else TipsCores.VidroBorda, forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icone?.let {
            Icon(
                it, contentDescription = null,
                tint = if (selecionado) Color.Black else TipsCores.TextoSecundario,
                modifier = Modifier.size(16.dp)
            )
            if (texto.isNotEmpty()) Spacer(Modifier.width(6.dp))
        }
        if (texto.isNotEmpty()) {
            Text(
                texto,
                color = if (selecionado) Color.Black else TipsCores.TextoSecundario,
                fontSize = 13.sp,
                fontWeight = if (selecionado) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

// Etiqueta pequena de status ("Em aberto", "Green", "Red")
@Composable
fun EtiquetaTips(texto: String, cor: Color) {
    Text(
        texto,
        color = cor,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(cor.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// Barra de navegação flutuante (pílula de vidro); o item ativo vira um círculo laranja
data class ItemBarra(val rotulo: String, val icone: ImageVector)

@Composable
fun BarraNavegacaoTips(
    itens: List<ItemBarra>,
    selecionado: Int,
    onSelecionar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val forma: Shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 48.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(forma)
            .background(TipsCores.Superficie.copy(alpha = 0.92f))
            .border(1.dp, TipsCores.VidroBorda, forma)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        itens.forEachIndexed { i, item ->
            val ativo = i == selecionado
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (ativo) TipsCores.Laranja else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelecionar(i) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.icone,
                    contentDescription = item.rotulo,
                    tint = if (ativo) Color.Black else TipsCores.TextoSecundario,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Título de seção pequeno, em caixa alta
@Composable
fun TituloSecao(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        color = TipsCores.TextoApagado,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier
    )
}
