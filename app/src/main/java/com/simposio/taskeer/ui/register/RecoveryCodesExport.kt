package com.simposio.taskeer.ui.register

import com.simposio.taskeer.domain.auth.RecoveryCodes

// Sin correo ni fecha: códigos y correo juntos en un archivo serían la cuenta entera.
object RecoveryCodesExport {
    const val FILE_NAME = "taskeer-codigos-recuperacion.txt"

    private const val HEADER = "Códigos de recuperación de Taskeer. Cada código sirve una sola vez. " +
        "Guárdalo en un lugar seguro."

    fun clipboardText(codes: RecoveryCodes): String = codes.values.joinToString("\n")

    fun fileText(codes: RecoveryCodes): String = HEADER + "\n\n" + clipboardText(codes) + "\n"
}
