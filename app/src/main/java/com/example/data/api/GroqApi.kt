package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<ChatMessage>,
    @Json(name = "temperature") val temperature: Float? = null,
    @Json(name = "max_tokens") val maxTokens: Int? = null
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    @Json(name = "choices") val choices: List<ChatChoice>?
)

@JsonClass(generateAdapter = true)
data class ChatChoice(
    @Json(name = "message") val message: ChatMessage?
)

interface GroqApiService {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://api.groq.com/openai/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GroqApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GroqApiService::class.java)
    }
}

object GroqService {
    private const val TAG = "GroqService"

    // Checks if the key is default or empty
    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GROQ_API_KEY
        return !key.isNullOrEmpty() && key != "MY_GROQ_API_KEY" && key != "placeholder_api_key"
    }

    suspend fun generateReflectionPrompt(appName: String, mode: String): String {
        if (!isApiKeyConfigured()) {
            return getOfflinePrompt(appName, mode)
        }

        val prompt = "Create a short, mindful, 1-sentence reflection prompt or question for a user who " +
                "is opening $appName while in '$mode' focus mode. Keep it concise, motivational, non-judgmental, " +
                "and centered around digital wellbeing and focus and self-awareness."

        val systemPrompt = "You are a calming digital wellbeing AI Coach. You produce beautiful, single-statement " +
                "mindful prompts designed to pause mindless scrolling and encourage intentional choice."

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = prompt)
        )

        val request = ChatCompletionRequest(
            model = "llama-3.3-70b-versatile",
            messages = messages,
            temperature = 0.7f,
            maxTokens = 100
        )

        return try {
            val response = RetrofitClient.service.createChatCompletion(
                authorization = "Bearer ${BuildConfig.GROQ_API_KEY}",
                request = request
            )
            response.choices?.firstOrNull()?.message?.content?.trim()
                ?: getOfflinePrompt(appName, mode)
        } catch (e: Exception) {
            Log.e(TAG, "Groq call failed, falling back to offline content: ${e.message}")
            getOfflinePrompt(appName, mode)
        }
    }

    suspend fun generateCoachingSession(
        totalTriggers: Int,
        avoidedTriggers: Int,
        savedTimeMinutes: Int,
        streakDays: Int,
        topApps: List<String>
    ): String {
        if (!isApiKeyConfigured()) {
            return getOfflineCoaching(totalTriggers, avoidedTriggers, savedTimeMinutes, streakDays, topApps)
        }

        val topAppsText = if (topApps.isEmpty()) "none" else topApps.joinToString(", ")
        val prompt = """
            As an AI Digital Wellbeing Coach, analyze my mobile usage stats for the past week and give me a 2-paragraph inspiring check-in and personalized, actionable feedback:
            - Avoided distractions: $avoidedTriggers / $totalTriggers total triggers
            - Total screen time saved: $savedTimeMinutes minutes
            - Mindful streak: $streakDays days
            - Most-triggered apps: $topAppsText
            
            Keep the tone deeply supportive, mindful, encouraging, and centered on self-improvement. No markdown lists.
        """.trimIndent()

        val systemPrompt = "You are an empathetic, expert digital wellbeing coach who helps people build sustainable phone habits through positive reinforcement."

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = prompt)
        )

        val request = ChatCompletionRequest(
            model = "llama-3.3-70b-versatile",
            messages = messages,
            temperature = 0.8f,
            maxTokens = 350
        )

        return try {
            val response = RetrofitClient.service.createChatCompletion(
                authorization = "Bearer ${BuildConfig.GROQ_API_KEY}",
                request = request
            )
            response.choices?.firstOrNull()?.message?.content?.trim()
                ?: getOfflineCoaching(totalTriggers, avoidedTriggers, savedTimeMinutes, streakDays, topApps)
        } catch (e: Exception) {
            Log.e(TAG, "Groq call failed for coaching, falling back to offline content: ${e.message}")
            getOfflineCoaching(totalTriggers, avoidedTriggers, savedTimeMinutes, streakDays, topApps)
        }
    }

    private fun getOfflinePrompt(appName: String, mode: String): String {
        val prompts = when (mode) {
            "Study Mode" -> listOf(
                "Why are you launching $appName? Is this helping you learn and achieve your study goals today?",
                "Take a deep breath. Will opening $appName bring you closer to finishing your study session?",
                "Is opening $appName right now worth disrupting your academic momentum?"
            )
            "Deep Work Mode" -> listOf(
                "Are you seeking a quick distraction? Stop, breathe, and recall your main deep work project.",
                "Is launching $appName essential right now, or is it an automatic impulse to escape pressure?",
                "Take 1 second. Your deep focus is powerful. Do you really want to break it for $appName?"
            )
            "Sleep Mode" -> listOf(
                "Your body needs rest. Will opening $appName help you sleep peacefully tonight?",
                "Put the screen away. A calm mind translates to deep, restoring sleep. Take a breath.",
                "Is $appName more important than entering a rejuvenating night of rest?"
            )
            "Dopamine Detox Mode" -> listOf(
                "A detox is about resetting your focus. Are you chasing a quick dopamine hit from $appName?",
                "Notice the urge to scroll. Breathe in the silence. Do you really want to check $appName?",
                "Allow yourself to feel bored. True creativity is born in those silent moments. Pause."
            )
            else -> listOf(
                "Why are you opening $appName? Take a brief moment to connect with your body and feel your breathing.",
                "Is this a conscious choice, or is your hand acting on autopilot?",
                "A tiny pause can change your whole day. Do you really want to consume $appName right now?"
            )
        }
        return prompts.random()
    }

    private fun getOfflineCoaching(
        totalTriggers: Int,
        avoidedTriggers: Int,
        savedTimeMinutes: Int,
        streakDays: Int,
        topApps: List<String>
    ): String {
        val ratio = if (totalTriggers > 0) (avoidedTriggers.toFloat() / totalTriggers * 100).toInt() else 0
        val coachFeedback = when {
            ratio >= 70 -> "Incredible progress! You are turning back from distractions 70%+ of the times. Your self-discipline is thriving, and you are actively reclaiming your attention span with every single breath."
            ratio >= 40 -> "Solid consistency! You are interrupting about half of your habitual launches. Continue leaning heavily on the 1-second pause to reinforce mindful choice of where your focus goes."
            else -> "A gentle reminder that every tiny pause is a step forward. Even if you continue to your distracting apps, taking that 1-second breath is laying the network for future mindful habits. Be proud of taking the pause."
        }
        
        val appsString = if (topApps.isEmpty()) "digital platforms" else topApps.joinToString(", ")
        return """
            $coachFeedback
            
            By avoiding $avoidedTriggers distractions, you have saved approximately $savedTimeMinutes minutes of doom-scrolling, helping you maintain a $streakDays-day mindful streak. Keep an eye on $appsString — they tend to pull you in on autopilot. You are doing great!
        """.trimIndent()
    }
}
