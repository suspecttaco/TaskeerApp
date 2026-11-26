package com.simposio.taskeer.ui.register

import com.simposio.taskeer.domain.auth.RecoveryCodes

fun numbered(codes: RecoveryCodes): List<Pair<Int, String>> =
    codes.values.mapIndexed { index, code -> (index + 1) to code }

// TalkBack lee «CTPQ-Q479» como una palabra: se deletrea carácter a carácter.
fun spokenForm(index: Int, total: Int, code: String): String {
    val spelled = code.map { if (it == '-') "guion" else it.toString() }.joinToString(", ")
    return "Código $index de $total: $spelled"
}
