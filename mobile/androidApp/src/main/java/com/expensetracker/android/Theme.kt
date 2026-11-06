package com.expensetracker.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val ExpenseFlowBlue = Color(0xFF2563EB)
internal val ExpenseFlowInk = Color(0xFF0F172A)
internal val ExpenseFlowMuted = Color(0xFF64748B)
internal val ExpenseFlowLine = Color(0xFFE5E7EB)
internal val ExpenseFlowCanvas = Color(0xFFF8FAFC)

private val LightColors = lightColorScheme(
    primary = ExpenseFlowBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = ExpenseFlowBlue,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = ExpenseFlowInk,
    surface = Color.White,
    onSurface = ExpenseFlowInk,
    onSurfaceVariant = ExpenseFlowMuted,
    outline = ExpenseFlowLine,
    error = Color(0xFFDC2626),
    onError = Color.White,
)

@Composable
internal fun ExpenseFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
