package com.example.apostas.telegram

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Lê o texto de um print com o ML Kit (roda no próprio aparelho, sem internet).
object OcrPrints {

    private val reconhecedor by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    // Linhas de texto em ordem de leitura, ou null se a imagem não pôde ser lida.
    // Pedaços na mesma altura viram uma linha só (o ML Kit devolve cada célula de tabela
    // separada; juntas, a linha "Casa 1 Back 2.23 ... +R$25,31" fica inteira).
    suspend fun lerLinhas(bytes: ByteArray): List<String>? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val imagem = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            reconhecedor.process(imagem)
                .addOnSuccessListener { texto ->
                    val pedacos = texto.textBlocks.flatMap { it.lines }.mapNotNull { linha ->
                        linha.boundingBox?.let { Pedaco(linha.text, it.left, it.exactCenterY(), it.height()) }
                    }
                    cont.resume(juntarPorLinha(pedacos))
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    data class Pedaco(val texto: String, val esquerda: Int, val centroY: Float, val altura: Int)

    // Agrupa pedaços cujo centro vertical está a menos de ~60% da altura do texto da linha atual.
    fun juntarPorLinha(pedacos: List<Pedaco>): List<String> {
        val linhas = mutableListOf<MutableList<Pedaco>>()
        for (p in pedacos.sortedBy { it.centroY }) {
            val atual = linhas.lastOrNull()
            val centroAtual = atual?.map { it.centroY }?.average()?.toFloat()
            val alturaAtual = atual?.maxOf { it.altura } ?: 0
            if (atual != null && centroAtual != null &&
                kotlin.math.abs(p.centroY - centroAtual) < maxOf(alturaAtual, p.altura) * 0.6f
            ) {
                atual.add(p)
            } else {
                linhas.add(mutableListOf(p))
            }
        }
        return linhas.map { linha -> linha.sortedBy { it.esquerda }.joinToString(" ") { it.texto } }
    }
}
