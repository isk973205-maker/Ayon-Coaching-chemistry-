package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateParentUpdate(
        studentName: String,
        attendanceRate: String,
        presentCount: Int,
        totalCount: Int,
        gradesList: List<Grade>,
        teacherNotes: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getFallbackReport(studentName, attendanceRate, presentCount, totalCount, gradesList, teacherNotes)
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        // Format test history
        val gradesText = if (gradesList.isEmpty()) {
            "No exam records yet."
        } else {
            gradesList.joinToString("\n") { 
                "• ${it.examName} (${it.chapterName}): ${it.marksObtained}/${it.maxMarks} marks. Remarks: ${it.teacherRemarks ?: "None"}"
            }
        }

        val prompt = """
            Generate a personalized, encouraging progress update for the parents of $studentName.
            He/she attends the "Ayan Coaching" chemistry center.
            
            Academic Metrics:
            - Attendance rate: $attendanceRate ($presentCount out of $totalCount classes attended)
            - Grade history in chemistry tests:
            $gradesText
            
            Teacher remarks: 
            $teacherNotes
            
            Please write a friendly, detailed letter to parents. 
            Include:
            1. An appreciative opening statement.
            2. An analysis of their chemistry performance (e.g. mention specific topics like stoichiometry or kinetics if present in the data), highlighting strengths and advising on areas for improvements.
            3. A summary of their attendance and why consistency is key in mastering complex chemistry concepts (like molecular orbitals or organic reactions).
            4. Encouraging chemistry-themed advice.
            Keep the tone warm, highly professional, and encouraging. Use elegant paragraphs.
        """.trimIndent()

        val systemInstruction = "You are Prof. Ayan, the leader of Ayan Coaching Research & Mentorship Center. Address the parent with high respect, provide highly specific chemistry feedback, and keep it extremely professional."

        try {
            // Build the standard JSON structure manually to avoid dependency and serialization issues
            val rootJson = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
            }

            val requestBody = rootJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API call failed with code: ${response.code}")
                    return@withContext getFallbackReport(studentName, attendanceRate, presentCount, totalCount, gradesList, teacherNotes)
                }

                val responseStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseStr)
                val candidates = responseJson.getJSONArray("candidates")
                val contents = candidates.getJSONObject(0).getJSONObject("content")
                val parts = contents.getJSONArray("parts")
                parts.getJSONObject(0).getString("text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}", e)
            getFallbackReport(studentName, attendanceRate, presentCount, totalCount, gradesList, teacherNotes)
        }
    }

    private fun getFallbackReport(
        studentName: String,
        attendanceRate: String,
        presentCount: Int,
        totalCount: Int,
        gradesList: List<Grade>,
        teacherNotes: String
    ): String {
        val avgGradeText = if (gradesList.isEmpty()) {
            "No grade history available"
        } else {
            val avg = gradesList.map { (it.marksObtained / it.maxMarks) * 100 }.average()
            String.format("%.1f%%", avg)
        }

        return """
            Dear Parents,

            Greetings from Ayan Coaching Center! This is Prof. Ayan writing to share an academic progress update for your child, $studentName.

            Chemistry Performance:
            At Ayan Coaching, we focus on deep conceptual chemistry. $studentName has achieved an average score of $avgGradeText across recent evaluations. 
            ${if (teacherNotes.isNotBlank()) "Teacher Note: '$teacherNotes'" else "They are making stable progress in visualizing chemical structures and understanding periodic trends."}

            Class Attendance & Consistency:
            $studentName has attended $presentCount out of $totalCount sessions, giving an attendance rate of $attendanceRate. Mastery in Chemistry is highly cumulative—missing even a single lab or reaction mechanism lecture (such as organic nucleophilic substitutions or chemical stoichiometry) can make subsequent chapters challenging. We appreciate your assistance in keeping up this consistency.

            Future Focus:
            We recommend spending 30 minutes daily practicing inorganic balancing equations and active chapter worksheets. Our specialized study resources are accessible in their dashboard at any time.

            Thank you for your continuous support in your child's scientific journey!

            Warm regards,
            Prof. Ayan
            Ayan Coaching Research & Mentorship
        """.trimIndent()
    }
}
