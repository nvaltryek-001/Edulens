package com.example.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EnrollmentScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ReportsScreen

@Composable
fun EduLensApp() {

    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        NavHost(
            navController = navController,
            startDestination = "login"
        ) {

            // ====================================================
            // LOGIN
            // ====================================================

            composable("login") {

                LoginScreen(

                    onLoginSuccess = {

                        navController.navigate(
                            "dashboard"
                        ) {

                            popUpTo("login") {
                                inclusive = true
                            }
                        }
                    }
                )
            }

            // ====================================================
            // DASHBOARD
            // ====================================================

            composable("dashboard") {

                DashboardScreen(

                    // ------------------------------------------------
                    // ATTENDANCE
                    // ------------------------------------------------

                    onNavigateToAttendance = {
                        subject,
                        faculty,
                        department,
                        semester,
                        section ->

                        val route =
                            "attendance/" +
                            "${android.net.Uri.encode(subject)}/" +
                            "${android.net.Uri.encode(faculty)}/" +
                            "${android.net.Uri.encode(department)}/" +
                            "${android.net.Uri.encode(semester)}/" +
                            "${android.net.Uri.encode(section)}"

                        navController.navigate(route)
                    },

                    // ------------------------------------------------
                    // ENROLLMENT
                    // ------------------------------------------------

                    onNavigateToEnrollment = {

                        navController.navigate(
                            "enrollment"
                        )
                    },

                    // ------------------------------------------------
                    // REPORTS
                    // ------------------------------------------------

                    onNavigateToReports = {

                        navController.navigate(
                            "reports"
                        )
                    }
                )
            }

            // ====================================================
            // ATTENDANCE
            // ====================================================

            composable(
                route =
                    "attendance/{subject}/{faculty}/{department}/{semester}/{section}"
            ) { backStackEntry ->

                val subject =
                    backStackEntry.arguments
                        ?.getString("subject")
                        ?: ""

                val faculty =
                    backStackEntry.arguments
                        ?.getString("faculty")
                        ?: ""

                val department =
                    backStackEntry.arguments
                        ?.getString("department")
                        ?: ""

                val semester =
                    backStackEntry.arguments
                        ?.getString("semester")
                        ?: ""

                val section =
                    backStackEntry.arguments
                        ?.getString("section")
                        ?: ""

                AttendanceScreen(

                    subject = subject,

                    faculty = faculty,

                    department = department,

                    semester = semester,

                    section = section,

                    onNavigateBack = {

                        navController.popBackStack()
                    }
                )
            }

            // ====================================================
            // ENROLLMENT
            // ====================================================

            composable("enrollment") {

                EnrollmentScreen(

                    onNavigateBack = {

                        navController.popBackStack()
                    }
                )
            }

            // ====================================================
            // REPORTS
            // ====================================================

            composable("reports") {

                ReportsScreen(

                    onNavigateBack = {

                        navController.popBackStack()
                    }
                )
            }
        }
    }
}