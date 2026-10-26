package com.example.apostas.ui

import android.content.Intent
import com.example.apostas.ui.theme.TipsCores
import com.example.apostas.ui.theme.fundoTips
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.data.Freebet
import com.example.apostas.data.diasParaVencer
import com.example.apostas.ui.screens.formatarBR
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// "Freebets" (botão "Usar freebet" da tela principal): ver as disponíveis (com a validade), usar numa surebet manual
// e o histórico das usadas. O cadastro é na tela Nova freebet ("+ Adicionar freebet").
class FreebetsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { ApostasTheme { TelaFreebets() } }
    }
}

private val Fundo = TipsCores.Fundo
private val FundoCard = TipsCores.Superficie
private val FundoCampo = TipsCores.CampoOpaco
private val Azul = TipsCores.Laranja
private val AzulClaro = TipsCores.LaranjaClaro
private val Verde = TipsCores.Verde
private val Vermelho = TipsCores.Vermelho
private val Laranja = TipsCores.LaranjaClaro

@Composable
private fun TelaFreebets() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = Fundo, darkIcons = false)
        systemUiController.setNavigationBarColor(color = Fundo, darkIcons = false)
    }

    var freebets by remember { mutableStateOf<List<Freebet>>(emptyList()) }
    var apostasPorId by remember { mutableStateOf<Map<Int, Aposta>>(emptyMap()) }
    var removendo by remember { mutableStateOf<Freebet?>(null) }

    fun recarregar() {
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            withContext(Dispatchers.IO) {
                db.freebetDao().getAll() to db.apostaDao().getAll().associateBy { it.id }
            }.let { (todas, apostas) ->
                freebets = todas
                apostasPorId = apostas
            }
        }
    }

    // Recarrega ao voltar da surebet (a freebet usada sai das disponíveis) e do cadastro (a nova aparece)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, evento -> if (evento == Lifecycle.Event.ON_RESUME) recarregar() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val disponiveis = freebets.filter { it.disponivel }.sortedBy { it.diasParaVencer() ?: Long.MAX_VALUE }
    val usadas = freebets.filterNot { it.disponivel }
    // Lucro com freebets: surebets já resolvidas que usaram freebet (o lucro já desconta o que se perdeu)
    val surebetsResolvidas = usadas.mapNotNull { apostasPorId[it.apostaId] }.filter { it.lucro != 0.0 }.distinctBy { it.id }
    val lucroComFreebets = surebetsResolvidas.sumOf { it.lucro }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fundoTips()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.padding(bottom = 4.dp)) {
            Text("Freebets", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Anote as freebets das missões e use numa surebet para extrair.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }

        // ── Resumo ───────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = FundoCard),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinhaResumo("Disponíveis", "${disponiveis.size} · ${formatarBR(disponiveis.sumOf { it.valor })}")
                LinhaResumo("Lucro com freebets", formatarBR(lucroComFreebets), if (lucroComFreebets > 0) Verde else Color.White.copy(alpha = 0.85f))
            }
        }

        Button(
            onClick = { context.startActivity(Intent(context, NovaFreebetActivity::class.java)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Azul),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) { Text("+ Adicionar freebet", fontSize = 15.sp, fontWeight = FontWeight.Medium) }

        // ── Disponíveis ──────────────────────────────────────────
        Text("DISPONÍVEIS", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, letterSpacing = 0.6.sp)
        if (disponiveis.isEmpty()) {
            Text("Nenhuma freebet disponível.", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp)
        }
        disponiveis.forEach { fb ->
            CardFreebet(fb) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { removendo = fb },
                        modifier = Modifier
                            .size(36.dp)
                            .background(TipsCores.Vermelho.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remover freebet", tint = Vermelho, modifier = Modifier.size(14.dp))
                    }
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(context, CadastroSureActivity::class.java).putExtra(CadastroSureActivity.EXTRA_FREEBET_ID, fb.id)
                            )
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Azul),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("Usar", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }

        // ── Usadas ───────────────────────────────────────────────
        if (usadas.isNotEmpty()) {
            Text("USADAS", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 8.dp))
            usadas.take(30).forEach { fb ->
                val surebet = apostasPorId[fb.apostaId]
                CardFreebet(fb, usada = true) {
                    Column(horizontalAlignment = Alignment.End) {
                        when {
                            surebet == null -> Text("Usada", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                            surebet.lucro == 0.0 -> {
                                Text("Em aberto", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                                Text(formatarBR(surebet.valor), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            else -> {
                                Text("Lucro", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                                Text(
                                    formatarBR(surebet.lucro),
                                    color = if (surebet.lucro > 0) Verde else Vermelho,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    removendo?.let { fb ->
        AlertDialog(
            onDismissRequest = { removendo = null },
            containerColor = FundoCard,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.7f),
            title = { Text("Remover freebet?") },
            text = { Text("${fb.casa} · ${formatarBR(fb.valor)}. Remova se ela venceu ou foi anotada errada.") },
            confirmButton = {
                TextButton(onClick = {
                    removendo = null
                    scope.launch {
                        withContext(Dispatchers.IO) { AppDatabase.getDatabase(context).freebetDao().apagar(fb) }
                        recarregar()
                    }
                }) { Text("Remover", color = Vermelho) }
            },
            dismissButton = { TextButton(onClick = { removendo = null }) { Text("Cancelar", color = Color.White.copy(alpha = 0.6f)) } }
        )
    }
}

@Composable
private fun LinhaResumo(rotulo: String, valor: String, cor: Color = Color.White.copy(alpha = 0.85f)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        Text(valor, color = cor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CardFreebet(fb: Freebet, usada: Boolean = false, acoes: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FundoCard),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${fb.casa} · ${formatarBR(fb.valor)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Perdeu ${formatarBR(fb.custo)} · ganhou em ${fb.data}", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                if (!usada) {
                    val dias = fb.diasParaVencer()
                    when {
                        dias == null -> {}
                        dias < 0 -> Text("Vencida em ${fb.validade}", color = Vermelho, fontSize = 12.sp)
                        dias == 0L -> Text("Vence hoje", color = Laranja, fontSize = 12.sp)
                        dias == 1L -> Text("Vence amanhã", color = Laranja, fontSize = 12.sp)
                        else -> Text("Vence em ${fb.validade}", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    }
                }
            }
            acoes()
        }
    }
}
