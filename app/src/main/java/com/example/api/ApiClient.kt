package com.example.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // ============================================================
    // EDULENS BACKEND
    // Development laptop IP address on the current Wi-Fi network
    // ============================================================

    
private const val BASE_URL = "http://10.0.2.2:5000/"
    // ============================================================
    // MOSHI
    // ============================================================

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // ============================================================
    // OKHTTP CLIENT
    // ============================================================

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(
            30,
            TimeUnit.SECONDS
        )
        .readTimeout(
            30,
            TimeUnit.SECONDS
        )
        .writeTimeout(
            30,
            TimeUnit.SECONDS
        )
        .build()

    // ============================================================
    // RETROFIT
    // ============================================================

    val retrofit: Retrofit by lazy {

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                MoshiConverterFactory.create(moshi)
            )
            .build()
    }

    // ============================================================
    // FLASK API SERVICE
    // ============================================================

    val flaskApiService: FlaskApiService by lazy {

        retrofit.create(
            FlaskApiService::class.java
        )
    }
}
