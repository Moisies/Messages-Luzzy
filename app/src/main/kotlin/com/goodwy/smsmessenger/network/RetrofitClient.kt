package com.goodwy.smsmessenger.network

import com.goodwy.smsmessenger.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente Retrofit para hacer llamadas a la API
 */
object RetrofitClient {

    private const val BASE_URL = BuildConfig.SERVER_BASE_URL + "/api/"

    /**
     * Cliente HTTP con interceptores para logging y timeouts
     */
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Agregar logging solo en modo debug
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        // Interceptor para agregar headers comunes
        builder.addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-App-Version", BuildConfig.VERSION_NAME)
                .method(original.method, original.body)
                .build()

            chain.proceed(request)
        }

        builder.build()
    }

    /**
     * Instancia de Retrofit
     */
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Servicio de la API
     */
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
