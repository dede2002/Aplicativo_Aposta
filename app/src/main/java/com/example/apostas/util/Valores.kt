package com.example.apostas.util

// Converte o que o usuário digita em número, aceitando o formato brasileiro.
// "1.234,56" → 1234.56 · "1234,56" → 1234.56 · "R$ 50" → 50.0 · "2.5" → 2.5 · "1.234.567" → 1234567.0
// Com um único ponto e sem vírgula, o ponto é tratado como decimal (ex.: odds "1.85").
fun parseValorBR(texto: String): Double? {
    val limpo = texto
        .replace("R$", "")
        .replace(" ", "")
        .replace(" ", "")
        .trim()
    if (limpo.isEmpty()) return null

    val normalizado = when {
        limpo.contains(',') -> limpo.replace(".", "").replace(',', '.')
        limpo.count { it == '.' } > 1 -> limpo.replace(".", "")
        else -> limpo
    }
    return normalizado.toDoubleOrNull()
}
