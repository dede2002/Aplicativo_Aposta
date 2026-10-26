package com.example.apostas.ui

import android.content.ActivityNotFoundException
import com.example.apostas.ui.theme.TipsCores
import com.example.apostas.ui.theme.fundoTips
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import com.example.apostas.telegram.EstadoAparelho
import com.example.apostas.telegram.ImportacaoAutomatica
import com.example.apostas.telegram.ImportadorTelegram
import com.example.apostas.telegram.NotificacoesImportacao
import com.example.apostas.telegram.PushDoAparelho
import com.example.apostas.telegram.SegundoPlano
import com.example.apostas.telegram.ServidorTips
import com.example.apostas.telegram.TelegramException
import com.example.apostas.telegram.aparelhoConectado
import com.example.apostas.telegram.carregarAparelho
import com.example.apostas.telegram.carregarExecucaoSegundoPlano
import com.example.apostas.telegram.carregarNomeConectado
import com.example.apostas.telegram.carregarNomeDoBot
import com.example.apostas.telegram.carregarUltimaImportacao
import com.example.apostas.telegram.esquecerAparelho
import com.example.apostas.telegram.salvarEstadoConexao
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// Conexão com o Telegram e status da importação automática.
// A conexão é um toque: o app abre o bot com um código e a pessoa toca em Iniciar.
class ImportacaoTelegramActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ApostasTheme {
                ImportacaoTelegramScreen()
            }
        }
    }
}

@Composable
fun ImportacaoTelegramScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var conectado by remember { mutableStateOf(aparelhoConectado(context)) }
    var nomeConectado by remember { mutableStateOf(carregarNomeConectado(context)) }
    var nomeDoBot by remember { mutableStateOf(carregarNomeDoBot(context)) }
    var conectando by remember { mutableStateOf(false) }
    // Depois de abrir o Telegram, ao voltar para o app confere se a pessoa tocou em Iniciar
    var esperandoIniciar by remember { mutableStateOf(false) }
    var erroConexao by remember { mutableStateOf<String?>(null) }

    var importando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<ImportadorTelegram.Resultado?>(null) }
    var ultimaVerificacao by remember { mutableStateOf(carregarUltimaImportacao(context).second) }

    var semRestricaoBateria by remember { mutableStateOf(SegundoPlano.semRestricaoDeBateria(context)) }
    var notificacoesAtivas by remember { mutableStateOf(NotificacoesImportacao.podeNotificar(context)) }
    var ultimoSegundoPlano by remember { mutableStateOf(carregarExecucaoSegundoPlano(context).second) }

    val bgPrimary = TipsCores.Fundo
    val bgCard = TipsCores.Superficie
    val accentBlue = TipsCores.Laranja

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = bgPrimary, darkIcons = false)
        systemUiController.setNavigationBarColor(color = bgPrimary, darkIcons = false)
    }

    fun abrirLink(link: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (e: ActivityNotFoundException) {
            erroConexao = "Não encontrei o Telegram neste celular. Instale o Telegram e tente de novo."
        }
    }

    fun importarAgora() {
        importando = true
        scope.launch {
            resultado = ImportadorTelegram.importar(context)
            ultimaVerificacao = carregarUltimaImportacao(context).second
            importando = false
        }
    }

    // Cria o aparelho (ou um código novo) e abre o bot no Telegram
    fun conectar() {
        conectando = true
        erroConexao = null
        scope.launch {
            val link = withContext(Dispatchers.IO) {
                try {
                    val aparelho = carregarAparelho(context)
                    if (aparelho == null) ServidorTips.criarAparelho(context) else ServidorTips(aparelho).novoLink()
                } catch (e: TelegramException) {
                    // Aparelho apagado no servidor: começa do zero
                    esquecerAparelho(context)
                    runCatching { ServidorTips.criarAparelho(context) }.getOrNull()
                } catch (e: Exception) {
                    null
                }
            }
            conectando = false
            if (link == null) {
                erroConexao = "Sem conexão com o servidor do Tips. Verifique a internet e tente de novo."
                return@launch
            }
            esperandoIniciar = true
            abrirLink(link)
        }
    }

    // Pergunta ao servidor se a conexão terminou (tenta por ~30 s)
    fun conferirConexao() {
        val aparelho = carregarAparelho(context) ?: return
        conectando = true
        scope.launch {
            var estado: EstadoAparelho? = null
            for (tentativa in 1..15) {
                estado = withContext(Dispatchers.IO) { runCatching { ServidorTips(aparelho).estado() }.getOrNull() }
                if (estado?.conectado == true) break
                delay(2_000)
            }
            conectando = false
            val e = estado
            if (e?.conectado == true) {
                esperandoIniciar = false
                salvarEstadoConexao(context, true, e.nome, e.bot)
                conectado = true
                nomeConectado = e.nome
                nomeDoBot = e.bot
                ImportacaoAutomatica.agendar(context)
                PushDoAparelho.registrar(context)?.let { erroConexao = it }
                importarAgora()
            } else {
                erroConexao = "Ainda não recebi a confirmação. No Telegram, toque em “Iniciar” na conversa com o bot e volte para cá."
            }
        }
    }

    fun desconectar() {
        val aparelho = carregarAparelho(context)
        scope.launch {
            if (aparelho != null) withContext(Dispatchers.IO) { runCatching { ServidorTips(aparelho).desconectar() } }
            esquecerAparelho(context)
            conectado = false
            nomeConectado = null
            resultado = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) {
                semRestricaoBateria = SegundoPlano.semRestricaoDeBateria(context)
                notificacoesAtivas = NotificacoesImportacao.podeNotificar(context)
                ultimoSegundoPlano = carregarExecucaoSegundoPlano(context).second
                ultimaVerificacao = carregarUltimaImportacao(context).second
                if (esperandoIniciar && !conectado) conferirConexao()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    @Composable
    fun CardInfo(conteudo: @Composable ColumnScope.() -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = bgCard),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = conteudo
            )
        }
    }

    @Composable
    fun TextoInfo(texto: String, cor: Color = Color.White.copy(alpha = 0.55f)) {
        Text(texto, color = cor, fontSize = 12.sp, lineHeight = 18.sp)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fundoTips()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            // Dentro da rolagem: o fim da tela para acima dos botões do celular.
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ── Header ───────────────────────────────────────────────
        Column {
            Text("Importar do Telegram", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (conectado) "Importação automática de surebets" else "Conecte seu Telegram em um toque",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )
        }

        erroConexao?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = TipsCores.Vermelho.copy(alpha = 0.08f)),
                border = BorderStroke(0.5.dp, TipsCores.Vermelho.copy(alpha = 0.35f))
            ) {
                Text(msg, color = TipsCores.Vermelho, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
        }

        // ── Não conectado ────────────────────────────────────────
        if (!conectado) {
            CardInfo {
                Text("Como funciona", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                TextoInfo(
                    "1. Toque em “Conectar com o Telegram”.\n" +
                            "2. O Telegram abre na conversa com o bot do Tips: toque em “Iniciar”.\n" +
                            "3. Volte para cá. Pronto!\n\n" +
                            "Depois é só encaminhar as entradas do grupo para o bot: a surebet aparece no app na hora."
                )
            }
            Button(
                onClick = { conectar() },
                enabled = !conectando,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                if (conectando) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (esperandoIniciar) "Conferindo conexão…" else "Abrindo o Telegram…", fontSize = 15.sp)
                } else {
                    Text("Conectar com o Telegram", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (esperandoIniciar && !conectando) {
                OutlinedButton(
                    onClick = { conferirConexao() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                ) { Text("Já toquei em Iniciar", fontSize = 13.sp) }
            }
            TextoInfo(
                "As mensagens que você encaminha ficam no servidor do Tips por até 7 dias, só até o app buscar. " +
                        "Suas apostas ficam apenas neste celular.",
                Color.White.copy(alpha = 0.35f)
            )
            return@Column
        }

        // ── Conectado ────────────────────────────────────────────
        CardInfo {
            Text(
                "✅ Conectado" + (nomeConectado?.let { " como $it" } ?: ""),
                color = TipsCores.Verde,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            TextoInfo(
                "Encaminhe para ${nomeDoBot ?: "o bot"} as mensagens de cada entrada: o sticker, os prints com os links " +
                        "e o print do Resumo. A surebet é anotada assim que o Resumo chegar, e o bot confirma no Telegram."
            )
            nomeDoBot?.let { bot ->
                OutlinedButton(
                    onClick = { abrirLink("https://t.me/${bot.removePrefix("@")}") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TipsCores.LaranjaClaro),
                    border = BorderStroke(0.5.dp, accentBlue.copy(alpha = 0.35f))
                ) { Text("Abrir conversa com o bot", fontSize = 13.sp) }
            }
            if (ultimaVerificacao.isNotBlank()) {
                TextoInfo("Última verificação: $ultimaVerificacao", Color.White.copy(alpha = 0.4f))
            }
            if (ultimoSegundoPlano.isNotBlank()) {
                TextoInfo("Em segundo plano: $ultimoSegundoPlano", Color.White.copy(alpha = 0.4f))
            }
        }

        // ── Resultado da última importação manual ────────────────
        resultado?.let { r ->
            r.erro?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = TipsCores.Vermelho.copy(alpha = 0.08f)),
                    border = BorderStroke(0.5.dp, TipsCores.Vermelho.copy(alpha = 0.35f))
                ) {
                    Text(msg, color = TipsCores.Vermelho, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
            }
            if (r.erro == null && r.importadas.isEmpty()) {
                CardInfo {
                    Text(
                        if (r.pendentes > 0) "Nenhuma entrada completa ainda. ${r.pendentes} mensagem(ns) esperando o print do Resumo."
                        else "Nenhuma entrada nova.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
            r.importadas.forEach { aposta -> CardImportada(aposta, bgCard) }
        }

        Button(
            onClick = { importarAgora() },
            enabled = !importando,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            if (importando) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Buscando mensagens...", fontSize = 15.sp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Importar agora", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        // ── Segundo plano: bateria e notificações ────────────────
        CardInfo {
            Text("🔋 Funcionamento com o app fechado", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            TextoInfo(
                if (semRestricaoBateria) "✅ Bateria sem restrições."
                else "⚠️ O Tips está com economia de bateria. O Android pode atrasar as importações.",
                if (semRestricaoBateria) TipsCores.Verde else Color(0xFFFFCC80)
            )
            if (!semRestricaoBateria) {
                OutlinedButton(
                    onClick = { SegundoPlano.pedirSemRestricao(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = accentBlue.copy(alpha = 0.10f),
                        contentColor = TipsCores.LaranjaClaro
                    ),
                    border = BorderStroke(0.5.dp, accentBlue.copy(alpha = 0.35f)),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) { Text("Permitir em segundo plano", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            }
            TextoInfo(
                if (notificacoesAtivas) "✅ Notificações ativadas."
                else "⚠️ Notificações desativadas para o Tips. Ative em Configurações → Apps → Tips → Notificações.",
                if (notificacoesAtivas) TipsCores.Verde else Color(0xFFFFCC80)
            )
            OutlinedButton(
                onClick = {
                    if (!NotificacoesImportacao.notificarTeste(context)) {
                        Toast.makeText(context, "Não foi possível mostrar: notificações desativadas.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.06f),
                    contentColor = Color.White.copy(alpha = 0.85f)
                ),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) { Text("Testar notificação", fontSize = 13.sp) }
        }

        TextButton(onClick = { desconectar() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Desconectar este celular", color = TipsCores.Vermelho.copy(alpha = 0.8f), fontSize = 13.sp)
        }
    }
}

// Card de uma surebet importada; tocar abre a edição (para conferir ou preencher o lucro).
@Composable
private fun CardImportada(aposta: Aposta, bgCard: Color) {
    val context = LocalContext.current
    val semLucro = aposta.valor <= 0.0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(
                    Intent(context, CadastroApostaActivity::class.java).putExtra("aposta_id", aposta.id)
                )
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = bgCard),
        border = BorderStroke(0.5.dp, if (semLucro) Color(0xFF5E4A2E) else TipsCores.Verde.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (semLucro) "⚠️ Importada sem lucro" else "✅ Importada",
                color = if (semLucro) Color(0xFFFFCC80) else TipsCores.Verde,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(aposta.descricao, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(aposta.casa, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            Text(
                if (semLucro) "Lucro não identificado no print. Toque para preencher."
                else "Lucro potencial: R$ " + String.format(Locale("pt", "BR"), "%,.2f", aposta.valor) + " · " + aposta.data,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}
