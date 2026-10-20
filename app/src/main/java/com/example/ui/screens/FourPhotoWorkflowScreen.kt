package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FourPhotoWorkflowScreen(onBack: () -> Unit) {
    val navy = Color(0xFF12345A)
    val blue = Color(0xFF1769AA)
    val labels = listOf("Left", "Center", "Right", "Back")
    val photos = remember { mutableStateListOf<String?>(
        null, null, null, null
    ) }
    var active by remember { mutableIntStateOf(0) }
    var review by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            photos[active] = uri.toString()
            message = "${labels[active]} photo selected locally"
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(Color(0xFFF3F7FC))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back", color = blue) }
        Text("SVIT  /  SMART ATTENDANCE",
            color = blue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(
            if (review) "Review & Verify" else "Take Attendance",
            color = navy, fontWeight = FontWeight.Bold, fontSize = 28.sp
        )
        Text(
            "Four-photo classroom evidence",
            color = Color(0xFF53657A), fontSize = 14.sp
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                labels.forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${index + 1}. $label view",
                                color = navy, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (photos[index] == null) "Photo not selected"
                                else "Selected",
                                color = if (photos[index] == null)
                                    Color(0xFF65758B) else Color(0xFF18794E),
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(onClick = {
                            active = index
                            picker.launch("image/*")
                        }) {
                            Text(if (photos[index] == null) "Choose photo" else "Replace")
                        }
                    }
                    if (index < labels.lastIndex) {
                        HorizontalDivider(color = Color(0xFFE0E8F1))
                    }
                }
            }
        }

        Text("${photos.count { it != null }} / 4 photos selected",
            color = navy, fontWeight = FontWeight.SemiBold)

        Button(
            onClick = { review = !review; message = "" },
            enabled = if (review) true else photos.all { it != null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = navy)
        ) {
            Text(if (review) "Back to photos" else "Review evidence")
        }

        if (review) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Teacher verification required",
                        color = navy, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Selected images are local only. AI processing, server upload, " +
                        "and official attendance finalization are not connected in this screen.",
                        color = Color(0xFF53657A), fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Unknown or uncertain detections must remain for teacher review; " +
                        "they must not automatically be marked absent.",
                        color = Color(0xFF53657A), fontSize = 13.sp
                    )
                }
            }
        }

        if (message.isNotBlank()) {
            Text(message, color = blue, fontSize = 13.sp)
        }
    }
}
