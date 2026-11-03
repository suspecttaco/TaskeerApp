package com.simposio.taskeer.ui.board

fun wipCounterLabel(count: Int, wipLimit: Int?): String? = wipLimit?.let { "$count/$it" }
