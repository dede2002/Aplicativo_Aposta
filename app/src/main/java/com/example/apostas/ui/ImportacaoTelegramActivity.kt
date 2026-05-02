package com.example.apostas.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.example.apostas.MainActivity
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.ui.theme.ApostasTheme
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// ── Sticker separador (fixo — é do grupo, não muda por usuário) ───
private const val STICKER_UNIQUE = "AgAD7QUAAlP8wEQ"

// ── Modelo ────────────────────────────────────────────────────────
data class SurebetPrevia(
    val descricao: String,
    val casas: List<String>,
    val lucro: Double,
    val data: String
)

// ── SharedPreferences helpers ─────────────────────────────────────
fun salvarUltimoUpdateId(context: Context, updateId: Long) {
    context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)
        .edit { putLong("ultimo_update_id", updateId) }
}

fun carregarUltimoUpdateId(context: Context): Long =
    context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)
        .getLong("ultimo_update_id", 0L)

fun salvarConfiguracaoTelegram(context: Context, token: String, chatId: String) {
    context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE).edit {
        putString("bot_token", token.trim())
        putString("chat_id", chatId.trim())
    }
}

fun carregarConfiguracaoTelegram(context: Context): Pair<String, String> {
    val prefs = context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)
    return Pair(
        prefs.getString("bot_token", "") ?: "",
        prefs.getString("chat_id", "") ?: ""
    )
}

fun configuracaoValida(context: Context): Boolean {
    val (token, chatId) = carregarConfiguracaoTelegram(context)
    return token.isNotBlank() && chatId.isNotBlank()
}

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

    // Verifica se já tem configuração salva
    var configValida by remember { mutableStateOf(configuracaoValida(context)) }
    var mostrarConfig by remember { mutableStateOf(!configValida) }

    // Campos de configuração
    val (tokenSalvo, chatIdSalvo) = carregarConfiguracaoTelegram(context)
    var inputToken  by remember { mutableStateOf(tokenSalvo) }
    var inputChatId by remember { mutableStateOf(chatIdSalvo) }
    var erroConfig  by remember { mutableStateOf<String?>(null) }

    // Estado da importação
    var loading  by remember { mutableStateOf(false) }
    var erro     by remember { mutableStateOf<String?>(null) }
    val previews = remember { mutableStateListOf<SurebetPrevia>() }
    var salvando by remember { mutableStateOf(false) }
    var sucesso  by remember { mutableStateOf(false) }

    // Estado do diálogo de edição
    var editandoIndex by remember { mutableStateOf<Int?>(null) }
    var editDescricao by remember { mutableStateOf("") }
    var editLucro     by remember { mutableStateOf("") }
    var editData      by remember { mutableStateOf("") }
    val editCasas     = remember { mutableStateListOf<String>() }

    val bgPrimary   = Color(0xFF141824)
    val bgCard      = Color(0xFF1A1F30)
    val bgField     = Color(0xFF1E2338)
    val accentBlue  = Color(0xFF4F6FFF)
    val borderColor = Color.White.copy(alpha = 0.12f)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = bgField,
        focusedContainerColor = bgField,
        unfocusedBorderColor = borderColor,
        focusedBorderColor = accentBlue,
        unfocusedLabelColor = Color.White.copy(alpha = 0.45f),
        focusedLabelColor = accentBlue,
        unfocusedTextColor = Color.White.copy(alpha = 0.85f),
        focusedTextColor = Color.White,
        cursorColor = accentBlue
    )

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(color = bgPrimary, darkIcons = false)
        systemUiController.setNavigationBarColor(color = bgPrimary, darkIcons = false)
    }

    fun buscar() {
        loading = true
        erro = null
        previews.clear()
        sucesso = false
        scope.launch {
            try {
                val resultado = withContext(Dispatchers.IO) { buscarMensagensTelegram(context) }
                previews.addAll(resultado)
                if (resultado.isEmpty())
                    erro = "Nenhuma surebet nova encontrada. Encaminhe as mensagens para o bot e tente novamente."
            } catch (e: Exception) {
                erro = "Erro ao conectar com o Telegram: ${e.message}"
            }
            loading = false
        }
    }

    // Busca automática ao abrir se já tiver configuração
    LaunchedEffect(configValida) {
        if (configValida && !mostrarConfig) buscar()
    }

    // ── Diálogo de edição ─────────────────────────────────────────
    editandoIndex?.let { idx ->
        AlertDialog(
            onDismissRequest = { editandoIndex = null },
            containerColor = Color(0xFF1A1F30),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    "Editar Surebet ${idx + 1}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editDescricao,
                        onValueChange = { editDescricao = it },
                        label = { Text("Descrição") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        singleLine = true
                    )
                    editCasas.forEachIndexed { i, casa ->
                        OutlinedTextField(
                            value = casa,
                            onValueChange = { editCasas[i] = it },
                            label = { Text("Casa ${i + 1}") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors,
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = editLucro,
                        onValueChange = { editLucro = it },
                        label = { Text("Lucro (R$)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editData,
                        onValueChange = { editData = it },
                        label = { Text("Data (dd/MM/yyyy)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val novoLucro = editLucro.replace(',', '.').toDoubleOrNull()
                            ?: previews[idx].lucro
                        previews[idx] = previews[idx].copy(
                            descricao = editDescricao.trim(),
                            casas = editCasas.filter { it.isNotBlank() },
                            lucro = novoLucro,
                            data = editData.trim()
                        )
                        editandoIndex = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue)
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { editandoIndex = null }) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgPrimary)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ── Header ───────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Importar do Telegram",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    if (mostrarConfig) "Configure seu bot para continuar"
                    else "Surebets detectadas nas suas mensagens",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp
                )
            }

            // Botão de configurações (só aparece quando não está na tela de config)
            if (!mostrarConfig && configValida) {
                IconButton(
                    onClick = { mostrarConfig = true },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Configurações",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ── Tela de configuração ──────────────────────────────────
        if (mostrarConfig) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = bgCard),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "⚙️ Configuração do Bot",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "1. Crie um bot no @BotFather no Telegram\n2. Cole o token abaixo\n3. Mande uma mensagem pro bot e acesse:\napi.telegram.org/bot<TOKEN>/getUpdates\n4. Copie o número em \"id\" dentro de \"chat\"",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)

                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it },
                        label = { Text("Token do Bot") },
                        placeholder = { Text("1234567890:AAF...", color = Color.White.copy(alpha = 0.2f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputChatId,
                        onValueChange = { inputChatId = it },
                        label = { Text("Seu Chat ID") },
                        placeholder = { Text("123456789", color = Color.White.copy(alpha = 0.2f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    erroConfig?.let {
                        Text(it, color = Color(0xFFEF9A9A), fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (inputToken.isBlank() || inputChatId.isBlank()) {
                                erroConfig = "Preencha o token e o Chat ID."
                                return@Button
                            }
                            salvarConfiguracaoTelegram(context, inputToken, inputChatId)
                            erroConfig = null
                            configValida = true
                            mostrarConfig = false
                            buscar()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Salvar e conectar", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            return@Column
        }

        // ── Loading ──────────────────────────────────────────────
        if (loading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = bgCard),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = accentBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Buscando mensagens novas...", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                }
            }
        }

        // ── Erro ─────────────────────────────────────────────────
        erro?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2F1A1A)),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF5E2E2E))
            ) {
                Text(msg, color = Color(0xFFEF9A9A), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
        }

        // ── Sucesso ──────────────────────────────────────────────
        if (sucesso) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2F1A)),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF2E5E2E))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF81C784))
                    Spacer(Modifier.width(10.dp))
                    Text("Surebets salvas com sucesso!", color = Color(0xFF81C784), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ── Cards de prévia ──────────────────────────────────────
        previews.forEachIndexed { i, previa ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = bgCard),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(accentBlue.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Surebet ${i + 1}", color = Color(0xFF7B97FF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }

                        IconButton(
                            onClick = {
                                editDescricao = previa.descricao
                                editLucro = "%.2f".format(previa.lucro).replace('.', ',')
                                editData = previa.data
                                editCasas.clear()
                                editCasas.addAll(previa.casas)
                                editandoIndex = i
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(7.dp))
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(previa.descricao, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)

                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)

                    previa.casas.forEachIndexed { idx, casa ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Casa ${idx + 1}", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                            Text(casa, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lucro", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                        Text(
                            "R$ %.2f".format(previa.lucro),
                            color = if (previa.lucro >= 0) Color(0xFF81C784) else Color(0xFFEF9A9A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Data", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
                        Text(previa.data, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // ── Botão Salvar ─────────────────────────────────────────
        if (previews.isNotEmpty() && !sucesso) {
            Button(
                onClick = {
                    salvando = true
                    scope.launch {
                        val dao = AppDatabase.getDatabase(context).apostaDao()
                        withContext(Dispatchers.IO) {
                            previews.forEach { previa ->
                                dao.insert(
                                    Aposta(
                                        id = 0,
                                        descricao = previa.descricao,
                                        casa = previa.casas.joinToString(" | "),
                                        valor = previa.lucro,
                                        odds = 1.0,
                                        retornoPotencial = previa.lucro,
                                        lucro = 0.0,
                                        data = previa.data
                                    )
                                )
                            }
                        }
                        salvando = false
                        sucesso = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                contentPadding = PaddingValues(vertical = 14.dp),
                enabled = !salvando
            ) {
                if (salvando) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "Salvar ${previews.size} surebet${if (previews.size > 1) "s" else ""}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Botão Atualizar ──────────────────────────────────────
        if (!loading) {
            OutlinedButton(
                onClick = { buscar() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.07f),
                    contentColor = Color.White.copy(alpha = 0.85f)
                ),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Atualizar mensagens", fontSize = 14.sp)
            }
        }

        // ── Botão Ver apostas após salvar ────────────────────────
        if (sucesso) {
            Button(
                onClick = {
                    val intent = Intent(context, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    (context as? ComponentActivity)?.finish()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E5E2E)),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text("Ver minhas apostas", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF81C784))
            }
        }
    }
}

// ── Busca com controle de offset ──────────────────────────────────
fun buscarMensagensTelegram(context: Context): List<SurebetPrevia> {
    val (botToken, chatId) = carregarConfiguracaoTelegram(context)
    if (botToken.isBlank() || chatId.isBlank()) return emptyList()

    val ultimoId = carregarUltimoUpdateId(context)
    val offset = if (ultimoId > 0L) ultimoId + 1 else 0L

    val url = "https://api.telegram.org/bot$botToken/getUpdates?limit=100" +
            if (offset > 0L) "&offset=$offset" else ""

    val resposta = URL(url).readText()
    val json = JSONObject(resposta)
    val results = json.getJSONArray("result")

    if (results.length() == 0) return emptyList()

    data class MensagemRaw(val texto: String, val isSticker: Boolean, val stickerUniqueId: String = "")

    val mensagens = mutableListOf<MensagemRaw>()
    var maiorUpdateId = ultimoId

    for (i in 0 until results.length()) {
        val update = results.getJSONObject(i)
        val updateId = update.getLong("update_id")
        if (updateId > maiorUpdateId) maiorUpdateId = updateId

        val message = update.optJSONObject("message") ?: continue
        val msgChatId = message.optJSONObject("chat")?.optLong("id") ?: continue
        if (msgChatId.toString() != chatId) continue

        when {
            message.has("sticker") -> {
                val uid = message.getJSONObject("sticker").optString("file_unique_id", "")
                mensagens.add(MensagemRaw("", isSticker = true, stickerUniqueId = uid))
            }
            message.has("photo") -> {
                val caption = message.optString("caption", "")
                if (caption.isNotBlank()) mensagens.add(MensagemRaw(caption, isSticker = false))
            }
            message.has("text") -> {
                val texto = message.optString("text", "")
                if (texto.isNotBlank()) mensagens.add(MensagemRaw(texto, isSticker = false))
            }
        }
    }

    if (maiorUpdateId > ultimoId) salvarUltimoUpdateId(context, maiorUpdateId)

    return parsearEmSurebets(mensagens.map { Triple(it.texto, it.isSticker, it.stickerUniqueId) })
}

// ── Parser ────────────────────────────────────────────────────────
fun parsearEmSurebets(mensagens: List<Triple<String, Boolean, String>>): List<SurebetPrevia> {
    val hoje = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    val previews = mutableListOf<SurebetPrevia>()

    val blocos = mutableListOf<MutableList<String>>()
    var blocoAtual = mutableListOf<String>()

    for ((texto, isSticker, stickerUniqueId) in mensagens) {
        if (isSticker && stickerUniqueId == STICKER_UNIQUE) {
            if (blocoAtual.isNotEmpty()) blocos.add(blocoAtual)
            blocoAtual = mutableListOf()
        } else if (!isSticker && texto.isNotBlank()) {
            blocoAtual.add(texto)
        }
    }
    if (blocoAtual.isNotEmpty()) blocos.add(blocoAtual)

    for (bloco in blocos) {
        val casas = mutableListOf<String>()
        var partida = ""
        var lucro = 0.0
        var temAposta = false
        var isFreebet = false

        for (mensagem in bloco) {
            val linhas = mensagem.lines()
            when {
                mensagem.contains("APOSTA PRINCIPAL") || mensagem.contains("PROTEÇÃO DA APOSTA") -> {
                    temAposta = true
                    for (linha in linhas) {
                        val l = linha.trim()
                        when {
                            l.contains("Partida:") && partida.isBlank() ->
                                partida = l.substringAfter("Partida:").trim()
                            l.contains("Casa:") -> {
                                val casa = l.substringAfter("Casa:").trim()
                                if (casa.isNotBlank() && !casas.contains(casa)) casas.add(casa)
                            }
                        }
                    }
                }
                mensagem.contains("LUCRO DA OPERAÇÃO") || mensagem.contains("Lucro Líquido") -> {
                    if (mensagem.contains("FREEBET", ignoreCase = true)) isFreebet = true
                    for (linha in linhas) {
                        val l = linha.trim()
                        if (l.contains("Lucro Líquido:")) {
                            val valorStr = l
                                .substringAfter("Lucro Líquido:")
                                .split("ou").first()
                                .split("(").first()
                                .replace("R$", "")
                                .replace(".", "")
                                .replace(",", ".")
                                .trim()
                            lucro = valorStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                }
            }
        }

        if (temAposta && casas.isNotEmpty()) {
            val descricaoBase = if (isFreebet && partida.isNotBlank())
                "Surebet ✅ - Extração Jogo - $partida"
            else
                "Surebet ✅" + if (partida.isNotBlank()) " - $partida" else ""

            previews.add(SurebetPrevia(descricao = descricaoBase, casas = casas, lucro = lucro, data = hoje))
        }
    }

    return previews
}