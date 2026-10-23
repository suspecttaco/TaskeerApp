package com.simposio.taskeer.domain.model

object LocalUser {
    // Identidad del usuario local único de la fase local-first. NO es un id de User del
    // servidor: el motor de sincronización debe sustituirlo por el id real.
    const val ID: String = "local-user"
}
