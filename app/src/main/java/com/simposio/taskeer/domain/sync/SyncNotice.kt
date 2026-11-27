package com.simposio.taskeer.domain.sync

enum class SyncLoss { REJECTED, REPLACED_BY_SERVER, DELETED_BY_SERVER, ACCESS_LOST }

// Solo cantidades: el aviso nunca nombra la tarea ni su contenido.
data class SyncNotice(val counts: Map<SyncLoss, Int>) {
    val total: Int get() = counts.values.sum()

    override fun toString(): String = "SyncNotice(total=$total)"
}

object SyncNoticeRules {
    fun summarize(losses: List<SyncLoss>): SyncNotice? {
        if (losses.isEmpty()) return null
        return SyncNotice(SyncLoss.entries.associateWith { kind -> losses.count { it == kind } }.filterValues { it > 0 })
    }

    fun message(notice: SyncNotice): String {
        val parts = SyncLoss.entries.mapNotNull { kind ->
            val count = notice.counts[kind] ?: return@mapNotNull null
            when (kind) {
                SyncLoss.REJECTED -> "${changes(count)} no ${if (count == 1) "se aceptó" else "se aceptaron"} en el servidor"
                SyncLoss.REPLACED_BY_SERVER ->
                    "${changes(count)} ${if (count == 1) "se sustituyó" else "se sustituyeron"} por una versión más reciente"
                SyncLoss.DELETED_BY_SERVER ->
                    "${changes(count)} ${if (count == 1) "se descartó" else "se descartaron"} porque el elemento se borró"
                SyncLoss.ACCESS_LOST ->
                    if (count == 1) "dejaste de tener acceso a 1 tablero" else "dejaste de tener acceso a $count tableros"
            }
        }
        return "Sincronización: " + parts.joinToString("; ")
    }

    private fun changes(count: Int): String = if (count == 1) "1 cambio" else "$count cambios"
}
