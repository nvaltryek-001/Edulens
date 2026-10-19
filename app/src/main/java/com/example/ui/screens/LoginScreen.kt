package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.api.ApiClient
import com.example.api.LoginRequest
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                Image(
                    painter = painterResource(
                        id = R.drawable.svit_logo
                    ),
                    contentDescription = "SVIT College Logo",
                    modifier = Modifier.size(120.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(
                    modifier = Modifier.height(24.dp)
                )

                Text(
                    text = "SVIT Smart Attendance",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Sai Vidya Institute of Technology",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(
                    modifier = Modifier.height(48.dp)
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        isError = false
                        errorMessage = ""
                    },
                    label = {
                        Text("Username")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isError
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        isError = false
                        errorMessage = ""
                    },
                    label = {
                        Text("Password")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null
                        )
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = isError
                )

                if (isError) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.height(32.dp)
                )

                Button(
                    onClick = {

                        if (
                            username.isBlank() ||
                            password.isBlank()
                        ) {
                            isError = true
                            errorMessage =
                                "Enter username and password"
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
                                            username =
                                                username.trim(),
                                            password =
                                                password
                                        )
                                    )

                                isLoading = false

                                if (response.token.isNotBlank()) {

                                    val preferences =
                                        context.getSharedPreferences(
                                            "edulens_session",
                                            Context.MODE_PRIVATE
                                        )

                                    preferences.edit()
                                        .putString(
                                            "token",
                                            response.token
                                        )
                                        .putString(
                                            "role",
                                            response.role
                                        )
                                        .putString(
                                            "username",
                                            username.trim()
                                        )
                                        .apply()

                                    println(
                                        "EDULENS_AUTH: Token saved"
                                    )

                                    println(
                                        "EDULENS_AUTH: Role = ${response.role}"
                                    )

                                    onLoginSuccess()

                                } else {

                                    isError = true
                                    errorMessage =
                                        "Login failed"
                                }

                            } catch (e: Exception) {

                                isLoading = false
                                isError = true

                                errorMessage =
                                    "Unable to connect to EduLens server"

                                println(
                                    "EDULENS_AUTH: Login error = ${e.message}"
                                )

                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("login_button"),
                    enabled = !isLoading
                ) {

                    if (isLoading) {

                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color =
                                MaterialTheme.colorScheme.onPrimary
                        )

                    } else {

                        Text(
                            "Secure Login",
                            style =
                                MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
