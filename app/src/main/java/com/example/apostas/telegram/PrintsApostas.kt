package com.example.apostas.telegram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.apostas.data.Aposta
import com.example.apostas.data.pernas
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Cópias comprimidas dos prints de cada casa, para o usuário conferir o que apostou ao marcar o resultado.
// Ficam em filesDir/prints. Para não pesar com o tempo, são apagadas 15 dias depois da data da entrada
// quando a aposta já foi resolvida (ou quando a aposta é excluída). O texto da perna fica para sempre.
object PrintsApostas {

    private const val LARGURA_MAXIMA = 1080
    private const val QUALIDADE_JPEG = 75
    private const val DIAS_GUARDANDO = 15

    private fun pasta(context: Context) = File(context.filesDir, "prints").apply { mkdirs() }

    fun arquivo(context: Context, nome: String): File = File(pasta(context), nome)

    fun existe(context: Context, nome: String?): Boolean = nome != null && arquivo(context, nome).isFile

    // Salva o print reduzido (~80 KB) e devolve o nome do arquivo, ou null se a imagem não abriu.
    // O nome sai da origem da operação: importar de novo sobrescreve o mesmo arquivo.
    fun salvar(context: Context, origem: String, indice: Int, bytes: ByteArray): String? {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val bitmap = if (original.width > LARGURA_MAXIMA) {
            val altura = original.height * LARGURA_MAXIMA / original.width
            Bitmap.createScaledBitmap(original, LARGURA_MAXIMA, altura, true)
        } else original
        val nome = "${hash(origem)}-$indice.jpg"
        return runCatching {
            arquivo(context, nome).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, it) }
            nome
        }.getOrNull()
    }

    // Apaga prints de apostas resolvidas há mais de 15 dias e de apostas que não existem mais.
    fun limpar(context: Context, apostas: List<Aposta>) {
        val limite = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -DIAS_GUARDANDO) }.time
        val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val manter = apostas.filter { aposta ->
            val dataAposta = runCatching { formato.parse(aposta.data) }.getOrNull()
            aposta.lucro == 0.0 || dataAposta == null || dataAposta.after(limite)
        }.flatMap { it.pernas.mapNotNull { p -> p.print } }.toSet()

        pasta(context).listFiles()?.forEach { f -> if (f.name !in manter) f.delete() }
    }

    private fun hash(texto: String): String =
        MessageDigest.getInstance("SHA-1").digest(texto.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
}
