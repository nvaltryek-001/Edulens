package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.api.ApiClient
import com.example.api.LoginRequest
import kotlinx.coroutines.launch

private val SvitBlue = Color(0xFF0755B8)
private val SvitDeepBlue = Color(0xFF073B83)
private val SvitBackground = Color(0xFFF3F7FD)
private val SvitText = Color(0xFF17243A)
private val SvitMuted = Color(0xFF637188)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onForgotPassword: () -> Unit = {}
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SvitBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFE5F0FF), Color.White, SvitBackground)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))

                Image(
                    painter = painterResource(id = R.drawable.svit_logo),
                    contentDescription = "SVIT institute logo",
                    modifier = Modifier.size(116.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "SVIT EduLens",
                    fontSize = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SvitDeepBlue
                )

                Text(
                    text = "SMART ATTENDANCE SYSTEM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = SvitBlue
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Sai Vidya Institute of Technology",
                    fontSize = 14.sp,
                    color = SvitMuted
                )

                Spacer(Modifier.height(28.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Welcome back",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = SvitText
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Sign in to continue to your campus",
                            fontSize = 14.sp,
                            color = SvitMuted
                        )

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = "College email / Student ID",
                            fontWeight = FontWeight.SemiBold,
                            color = SvitText,
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                isError = false
                                errorMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("name@saividya.ac.in") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Username"
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(15.dp),
                            isError = isError
                        )

                        Spacer(Modifier.height(18.dp))

                        Text(
                            text = "Password",
                            fontWeight = FontWeight.SemiBold,
                            color = SvitText,
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                isError = false
                                errorMessage = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter your password") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Password"
                                )
                            },
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        passwordVisible = !passwordVisible
                                    }
                                ) {
                                    Text(
                                        if (passwordVisible) "Hide" else "Show",
                                        color = SvitBlue
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(15.dp),
                            isError = isError
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onForgotPassword) {
                                Text("Forgot password?", color = SvitBlue)
                            }
                        }

                        if (isError) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    isError = true
                                    errorMessage = "Enter username and password"
                                    return@Button
                                }

                                isLoading = true
                                isError = false
                                errorMessage = ""

                                coroutineScope.launch {
                                    try {
                                        val response =
                                            ApiClient.flaskApiService.login(
                                                LoginRequest(
                                                    username = username.trim(),
                                                    password = password
                                                )
                                            )

                                        isLoading = false

                                        if (response.token.isNotBlank()) {
                                            context.getSharedPreferences(
                                                "edulens_session",
                                                Context.MODE_PRIVATE
                                            ).edit()
                                                .putString("token", response.token)
                                                .putString("role", response.role)
                                                .putString("username", username.trim())
                                                .apply()

                                            onLoginSuccess()
                                        } else {
                                            isError = true
                                            errorMessage = "Login failed. Check your credentials."
                                        }
                                    } catch (e: Exception) {
                                        isLoading = false
                                        isError = true
                                        errorMessage =
                                            "Unable to connect. Check your internet and try again."
                                        println("EDULENS_AUTH: Login error = ${e.message}")
                                        e.printStackTrace()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("login_button"),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SvitDeepBlue
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(23.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Signing in...")
                            } else {
                                Text(
                                    text = "Sign In",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(SvitBlue)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Secure campus access",
                                fontSize = 12.sp,
                                color = SvitMuted
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "LEARN  |  ATTEND  |  GROW",
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = SvitDeepBlue
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "SVIT | EduLens",
                    fontSize = 12.sp,
                    color = SvitMuted
                )
            }
        }
    }
}
