package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF12345A)
private val Blue = Color(0xFF1769AA)
private val Canvas = Color(0xFFF3F7FC)
private val Ink = Color(0xFF172B45)
private val Muted = Color(0xFF65758B)
private val Border = Color(0xFFE0E8F1)

@Composable
fun SvitScreen(
    title: String,
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Canvas)
            .statusBarsPadding().padding(20.dp)
    ) {
        Text(
            "SVIT  /  SMART ATTENDANCE",
            color = Blue, fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))
        Text(title, color = Navy, fontSize = 27.sp,
            fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun SvitCard(
    title: String,
    description: String = "",
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Ink, fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(description, color = Muted, fontSize = 13.sp)
            }
            content()
        }
    }
}

@Composable
fun SvitMetric(label: String, value: String, note: String = "") {
    SvitCard(label, note) {
        Spacer(Modifier.height(8.dp))
        Text(value, color = Blue, fontSize = 28.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SvitPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Navy,
            contentColor = Color.White
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SvitStatus(text: String, positive: Boolean = false) {
    val color = if (positive) Color(0xFF18794E) else Blue
    Surface(color = color.copy(alpha = 0.09f),
        shape = RoundedCornerShape(50.dp)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = color, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold)
    }
}
