package me.ash.reader.infrastructure.net.openai

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface OpenAiApiService {
    @GET("models")
    suspend fun getModels(): Response<ModelsResponse>

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    @POST("responses")
    suspend fun createRawResponse(
        @Body request: OpenAiResponsesRequest
    ): Response<ResponseBody>

    companion object {
        fun getInstance(
            baseUrl: String,
            apiKey: String,
            timeoutSeconds: Long = 30L,
            callTimeoutSeconds: Long? = null,
        ): OpenAiApiService {
            val authInterceptor = Interceptor { chain ->
                val originalRequest: Request = chain.request()
                val requestBuilder: Request.Builder = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")

                val request: Request = requestBuilder.build()
                chain.proceed(request)
            }

            val clientBuilder = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            if (callTimeoutSeconds != null) {
                clientBuilder.callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
            }
            val client = clientBuilder.build()

            return Retrofit.Builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenAiApiService::class.java)
        }

        internal fun normalizeBaseUrl(baseUrl: String): String =
            baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() }?.let { "$it/" }
                ?: "https://api.openai.com/v1/"
    }
}
