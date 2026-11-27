package com.simposio.taskeer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
)

internal fun TextStyle.emphasized(): TextStyle = copy(fontWeight = FontWeight.SemiBold)

internal fun TextStyle.boldIf(bold: Boolean): TextStyle =
    copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

internal fun TextStyle.unread(isUnread: Boolean): TextStyle = boldIf(isUnread)
