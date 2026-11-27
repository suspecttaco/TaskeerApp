package com.simposio.taskeer.ui.navigation

// El flujo de cuenta puede nacer en Ajustes o en Home: un `popUpTo` fijo a Ajustes no desapilaría nada desde Home.
fun accountFlowAnchor(routes: List<String?>): String {
    val present = routes.filterNotNull()
    return when {
        AppDestination.SETTINGS.route in present -> AppDestination.SETTINGS.route
        AppDestination.HOME.route in present -> AppDestination.HOME.route
        else -> AppDestination.START.route
    }
}
