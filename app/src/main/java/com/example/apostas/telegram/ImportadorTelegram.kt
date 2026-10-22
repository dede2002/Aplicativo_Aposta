package com.example.apostas.telegram

import android.content.Context
import com.example.apostas.data.Aposta
import com.example.apostas.data.AppDatabase
import com.example.apostas.ui.components.casasDeAposta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Importação automática: lê as mensagens novas do bot, junta com as que estavam esperando
// (operações incompletas) e salva cada operação completa como surebet em aberto.
//
// Ordem segura: as apostas e o buffer são gravados no aparelho ANTES de o Telegram ser avisado
// de que as mensagens foram lidas (isso só acontece no próximo getUpdates com offset maior).
// Se algo falhar no meio, a próxima rodada refaz o trabalho; a coluna única origemTelegram
// impede que a mesma operação seja salva duas vezes.
object ImportadorTelegram {

    data class Resultado(
        val importadas: List<Aposta> = emptyList(),
        // Quantas das importadas ficaram sem lucro (print do Resumo ilegível)
        val semLucro: Int = 0,
        // Mensagens guardadas esperando o resto da operação
        val pendentes: Int = 0,
        val erro: String? = null,
        // Sem internet / Telegram fora do ar: vale tentar de novo depois
        val falhaDeRede: Boolean = false,
        val semConfiguracao: Boolean = false
    )

    // Worker e tela podem chamar ao mesmo tempo; só uma importação roda por vez.
    private val mutex = Mutex()

    private const val LIMITE_POR_PAGINA = 100
    private const val MAX_PAGINAS = 10
    // Restos de operação que nunca terminaram são descartados depois de 3 dias.
    private const val VALIDADE_PENDENTE_SEGUNDOS = 3L * 24 * 3600

    suspend fun importar(context: Context): Resultado = mutex.withLock {
        withContext(Dispatchers.IO) { importarInterno(context.applicationContext) }
    }

    private suspend fun importarInterno(context: Context): Resultado {
        // Tudo vem do servidor do Tips: só as mensagens e prints desta pessoa.
        val aparelho = carregarAparelho(context)
        if (aparelho == null || !aparelhoConectado(context)) return Resultado(semConfiguracao = true)
        val servidor = ServidorTips(aparelho)
        val dao = AppDatabase.getDatabase(context).apostaDao()
        val importadas = mutableListOf<Aposta>()
        var semLucro = 0
        var buffer = carregarBuffer(context)
        var erro: String? = null
        var falhaDeRede = false

        // Lê o print pelo file_id; foto inacessível conta como ilegível (não trava a importação).
        val lerFoto: suspend (String) -> List<String>? = { fileId ->
            try {
                OcrPrints.lerLinhas(servidor.baixarFoto(fileId))
            } catch (e: TelegramException) {
                null
            }
        }

        try {
            for (pagina in 0 until MAX_PAGINAS) {
                val ultimo = carregarUltimoUpdateId(context)
                val updates = servidor.getUpdates(after = ultimo, limite = LIMITE_POR_PAGINA)

                var maior = ultimo
                for (i in 0 until updates.length()) {
                    val update = updates.getJSONObject(i)
                    maior = maxOf(maior, update.getLong("update_id"))
                    // O servidor já filtra a conversa desta pessoa
                    mensagemDoUpdate(update)?.let { buffer = buffer + it }
                }

                val separacao = separarOperacoes(buffer)
                val naoProcessadas = mutableListOf<MensagemTelegram>()
                for ((indice, bloco) in separacao.completas.withIndex()) {
                    val operacao = try {
                        extrairOperacao(bloco, casasDeAposta, lerFoto)
                    } catch (e: IOException) {
                        // Caiu a internet no meio: esta operação e as seguintes ficam para a próxima.
                        naoProcessadas += separacao.completas.drop(indice).flatten()
                        falhaDeRede = true
                        break
                    } ?: continue

                    val aposta = Aposta(
                        descricao = operacao.descricao,
                        casa = operacao.casas.joinToString(" | "),
                        // Em surebets, o valor cadastrado é o lucro esperado
                        valor = operacao.lucro ?: 0.0,
                        odds = 1.0,
                        retornoPotencial = operacao.lucro ?: 0.0,
                        lucro = 0.0,
                        data = operacao.data,
                        origemTelegram = operacao.origem
                    )
                    val id = dao.insertSeNova(aposta)
                    if (id != -1L) {
                        importadas += aposta.copy(id = id.toInt())
                        if (operacao.lucro == null) semLucro++
                    }
                }

                val agora = System.currentTimeMillis() / 1000
                buffer = (naoProcessadas + separacao.pendentes)
                    .filter { it.recebidaUnix == 0L || agora - it.recebidaUnix < VALIDADE_PENDENTE_SEGUNDOS }
                salvarBuffer(context, buffer)
                salvarUltimoUpdateId(context, maior)

                if (falhaDeRede || updates.length() < LIMITE_POR_PAGINA) break
            }
        } catch (e: TelegramException) {
            erro = e.message
        } catch (e: IOException) {
            erro = "Sem conexão com o Telegram. Tentando de novo mais tarde."
            falhaDeRede = true
        } catch (e: Exception) {
            erro = "Resposta inesperada do Telegram. Tentando de novo mais tarde."
        }

        if (falhaDeRede && erro == null) erro = "A internet caiu durante a importação. Tentando de novo mais tarde."

        val resultado = Resultado(importadas, semLucro, buffer.size, erro, falhaDeRede)
        salvarUltimaImportacao(context, System.currentTimeMillis(), resumir(resultado))
        return resultado
    }

    fun resumir(r: Resultado): String {
        val hora = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        return buildString {
            append(hora).append(" · ")
            when {
                r.erro != null -> append(r.erro)
                r.importadas.isEmpty() -> append("nenhuma operação nova")
                r.importadas.size == 1 -> append("1 surebet importada")
                else -> append("${r.importadas.size} surebets importadas")
            }
            if (r.semLucro > 0) append(" (${r.semLucro} sem lucro identificado)")
            if (r.pendentes > 0) append(" · ${r.pendentes} mensagem(ns) esperando o Resumo")
        }
    }

    private fun carregarBuffer(context: Context): List<MensagemTelegram> {
        val json = carregarBufferJson(context) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { mensagemDeJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun salvarBuffer(context: Context, mensagens: List<MensagemTelegram>) {
        val array = JSONArray()
        mensagens.forEach { array.put(it.paraJson()) }
        salvarBufferJson(context, array.toString())
    }
}
