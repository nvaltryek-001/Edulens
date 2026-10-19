package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EnrollmentScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ReportsScreen
import kotlinx.coroutines.delay

@Composable
fun EduLensApp() {
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(
            navController = navController,
            startDestination = "splash"
        ) {
            composable("splash") {
                SvitSplashScreen()
                LaunchedEffect(Unit) {
                    delay(2200)
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            composable("login") {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("login") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("dashboard") {
                DashboardScreen(
                    onNavigateToAttendance = { subject, faculty, department, semester, section ->
                        val route = "attendance/" +
                            "${android.net.Uri.encode(subject)}/" +
                            "${android.net.Uri.encode(faculty)}/" +
                            "${android.net.Uri.encode(department)}/" +
                            "${android.net.Uri.encode(semester)}/" +
                            "${android.net.Uri.encode(section)}"
                        navController.navigate(route)
                    },
                    onNavigateToEnrollment = { navController.navigate("enrollment") },
                    onNavigateToReports = { navController.navigate("reports") }
                )
            }

            composable(
                route = "attendance/{subject}/{faculty}/{department}/{semester}/{section}"
            ) { backStackEntry ->
                val subject = backStackEntry.arguments?.getString("subject") ?: ""
                val faculty = backStackEntry.arguments?.getString("faculty") ?: ""
                val department = backStackEntry.arguments?.getString("department") ?: ""
                val semester = backStackEntry.arguments?.getString("semester") ?: ""
                val section = backStackEntry.arguments?.getString("section") ?: ""

                AttendanceScreen(
                    subject = subject,
                    faculty = faculty,
                    department = department,
                    semester = semester,
                    section = section,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("enrollment") {
                EnrollmentScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("reports") {
                ReportsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun SvitSplashScreen() {
    val charcoal = Color(0xFF252525)
    val warmWhite = Color(0xFFFFFBF5)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(warmWhite)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.svit_logo),
                contentDescription = "Sai Vidya Institute of Technology logo",
                modifier = Modifier.size(244.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "SVIT SMART ATTENDANCE",
                color = charcoal,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sai Vidya Institute of Technology",
                color = Color(0xFF626262),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Learn to lead",
                color = Color(0xFFB3261E),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
