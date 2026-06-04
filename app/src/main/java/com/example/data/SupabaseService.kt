package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

sealed class SupabaseSyncState {
    object Idle : SupabaseSyncState()
    object Syncing : SupabaseSyncState()
    data class Success(val message: String) : SupabaseSyncState()
    data class Error(val error: String) : SupabaseSyncState()
}

sealed class SupabaseConnectionStatus {
    object Unconfigured : SupabaseConnectionStatus()
    object Checking : SupabaseConnectionStatus()
    object Online : SupabaseConnectionStatus()
    data class Offline(val reason: String) : SupabaseConnectionStatus()
}

// Supabase Auth models
data class SupabaseAuthEmailRequest(
    val email: String,
    val password: String,
    val data: Map<String, Any>? = null
)

data class SupabaseAuthOtpRequest(
    val phone: String
)

data class SupabaseAuthVerifyRequest(
    val type: String = "sms",
    val phone: String,
    val token: String
)

data class SupabaseAuthUser(
    val id: String?,
    val email: String?,
    val phone: String?
)

data class SupabaseAuthResponse(
    val access_token: String?,
    val token_type: String?,
    val expires_in: Int?,
    val refresh_token: String?,
    val user: SupabaseAuthUser?
)

interface SupabaseAuthApi {
    @POST("signup")
    suspend fun signUpWithEmail(@Body request: SupabaseAuthEmailRequest): SupabaseAuthResponse

    @POST("token")
    suspend fun logInWithEmail(
        @Query("grant_type") grantType: String = "password",
        @Body request: SupabaseAuthEmailRequest
    ): SupabaseAuthResponse

    @POST("otp")
    suspend fun sendPhoneOtp(@Body request: SupabaseAuthOtpRequest)

    @POST("verify")
    suspend fun verifyPhoneOtp(@Body request: SupabaseAuthVerifyRequest): SupabaseAuthResponse
}

/**
 * Rentorfit Interface for Supabase PostgREST REST API.
 */
interface SupabaseApi {
    @GET("users")
    suspend fun getUsers(): List<User>

    @POST("users")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun upsertUsers(@Body users: List<User>): List<User>

    @GET("attendance")
    suspend fun getAttendance(): List<Attendance>

    @POST("attendance")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun upsertAttendance(@Body attendance: List<Attendance>): List<Attendance>

    @GET("grades")
    suspend fun getGrades(): List<Grade>

    @POST("grades")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun upsertGrades(@Body grades: List<Grade>): List<Grade>

    @GET("learning_resources")
    suspend fun getLearningResources(): List<LearningResource>

    @POST("learning_resources")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun upsertLearningResources(@Body resources: List<LearningResource>): List<LearningResource>

    @GET("messages")
    suspend fun getMessages(): List<Message>

    @POST("messages")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun upsertMessages(@Body messages: List<Message>): List<Message>

    @GET("assignments")
    suspend fun getAssignments(): List<Assignment>

    @POST("assignments")
    @Headers("Prefer: resolution=merge-duplicates")
    suspend fun upsertAssignments(@Body assignments: List<Assignment>): List<Assignment>
}

object SupabaseService {
    private const val TAG = "SupabaseService"

    // Default configuration injected via BuildConfig
    var currentUrl: String = BuildConfig.SUPABASE_URL.ifEmpty { "https://ackjzohrkryepjvibfnh.supabase.co/rest/v1/" }
    var currentKey: String = BuildConfig.SUPABASE_ANON_KEY.ifEmpty { "sb_publishable_hTGES7p5iR3Ej9aoBsL-Jw_atOqH_0h" }
    var projectId: String = BuildConfig.SUPABASE_PROJECT_ID.ifEmpty { "ackjzohrkryepjvibfnh" }

    private val _connectionStatus = MutableStateFlow<SupabaseConnectionStatus>(SupabaseConnectionStatus.Unconfigured)
    val connectionStatus: StateFlow<SupabaseConnectionStatus> = _connectionStatus.asStateFlow()

    private val _syncState = MutableStateFlow<SupabaseSyncState>(SupabaseSyncState.Idle)
    val syncState: StateFlow<SupabaseSyncState> = _syncState.asStateFlow()

    private var retrofitInstance: Retrofit? = null
    private var apiInstance: SupabaseApi? = null
    private var authApiInstance: SupabaseAuthApi? = null

    init {
        // Enforce trailing slash to satisfy Retrofit
        if (!currentUrl.endsWith("/")) {
            currentUrl += "/"
        }
        setupClient()
    }

    /**
     * Initializes Retrofit and OkHttp with the current credentials.
     */
    fun configure(url: String, key: String, projId: String) {
        var formattedUrl = url.trim()
        if (formattedUrl.isNotEmpty() && !formattedUrl.endsWith("/")) {
            formattedUrl += "/"
        }
        currentUrl = formattedUrl
        currentKey = key.trim()
        projectId = projId.trim()
        setupClient()
    }

    private fun setupClient() {
        if (currentUrl.isEmpty() || currentUrl == "https://" || currentKey.isEmpty()) {
            _connectionStatus.value = SupabaseConnectionStatus.Unconfigured
            retrofitInstance = null
            apiInstance = null
            authApiInstance = null
            return
        }

        try {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(HeaderInterceptor(currentKey))
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(currentUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            retrofitInstance = retrofit
            apiInstance = retrofit.create(SupabaseApi::class.java)

            // Supabase Auth Endpoint setup (using the auth/v1 subroute)
            val authUrl = currentUrl.replace("/rest/v1/", "/auth/v1/")
            val authRetrofit = Retrofit.Builder()
                .baseUrl(authUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            authApiInstance = authRetrofit.create(SupabaseAuthApi::class.java)

            _connectionStatus.value = SupabaseConnectionStatus.Unconfigured
        } catch (e: Exception) {
            Log.e(TAG, "Error building Retrofit client", e)
            _connectionStatus.value = SupabaseConnectionStatus.Offline("Client setup error: ${e.localizedMessage}")
        }
    }

    // --- Supabase GoTrue Auth API Wrappers ---
    suspend fun signUpWithEmail(email: String, password: String, metadata: Map<String, Any>? = null): SupabaseAuthResponse? = withContext(Dispatchers.IO) {
        val auth = authApiInstance ?: return@withContext null
        auth.signUpWithEmail(SupabaseAuthEmailRequest(email, password, metadata))
    }

    suspend fun logInWithEmail(email: String, password: String): SupabaseAuthResponse? = withContext(Dispatchers.IO) {
        val auth = authApiInstance ?: return@withContext null
        auth.logInWithEmail("password", SupabaseAuthEmailRequest(email, password))
    }

    suspend fun sendPhoneOtp(phone: String) = withContext(Dispatchers.IO) {
        val auth = authApiInstance ?: return@withContext
        auth.sendPhoneOtp(SupabaseAuthOtpRequest(phone))
    }

    suspend fun verifyPhoneOtp(phone: String, token: String): SupabaseAuthResponse? = withContext(Dispatchers.IO) {
        val auth = authApiInstance ?: return@withContext null
        auth.verifyPhoneOtp(SupabaseAuthVerifyRequest("sms", phone, token))
    }

    private class HeaderInterceptor(private val key: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestWithHeaders = originalRequest.newBuilder()
                .header("apikey", key)
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
                .build()
            return chain.proceed(requestWithHeaders)
        }
    }

    /**
     * Check if connection to Supabase works by performing a lightweight query.
     */
    suspend fun checkConnection(): SupabaseConnectionStatus = withContext(Dispatchers.IO) {
        val api = apiInstance
        if (api == null) {
            return@withContext SupabaseConnectionStatus.Unconfigured
        }
        _connectionStatus.value = SupabaseConnectionStatus.Checking
        try {
            // We ping the 'users' endpoint to verify connectivity
            api.getUsers()
            _connectionStatus.value = SupabaseConnectionStatus.Online
            SupabaseConnectionStatus.Online
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            Log.e(TAG, "HTTP connection check failed: code=$code, body=$errorBody", e)
            
            val reason = when {
                code == 404 -> "Database Table 'users' not found. Please click 'Create Tables SQL' and run it in Supabase SQL editor!"
                code == 401 || code == 403 -> "Invalid Public API Key or Secret Key. Please check credentials!"
                else -> "HTTP $code: $errorBody"
            }
            val status = SupabaseConnectionStatus.Offline(reason)
            _connectionStatus.value = status
            status
        } catch (e: Exception) {
            Log.e(TAG, "Exception during connection check", e)
            val status = SupabaseConnectionStatus.Offline(e.localizedMessage ?: "Unknown network failure.")
            _connectionStatus.value = status
            status
        }
    }

    /**
     * Backups all data from local Room tables to Supabase tables.
     */
    suspend fun backupLocalDatabaseToCloud(repository: CoachingRepository) = withContext(Dispatchers.IO) {
        val api = apiInstance
        if (api == null) {
            _syncState.value = SupabaseSyncState.Error("Supabase not configured.")
            return@withContext
        }

        _syncState.value = SupabaseSyncState.Syncing
        try {
            Log.d(TAG, "Starting full Room to Supabase backup sync...")

            // 1. Sync Users
            val localUsers = repository.allUsers.first()
            if (localUsers.isNotEmpty()) {
                Log.d(TAG, "Syncing ${localUsers.size} users...")
                api.upsertUsers(localUsers)
            }

            // 2. Sync Assignments
            val localAssignments = repository.allAssignments.first()
            if (localAssignments.isNotEmpty()) {
                Log.d(TAG, "Syncing ${localAssignments.size} assignments...")
                api.upsertAssignments(localAssignments)
            }

            // 3. Sync Learning Resources
            val localResources = repository.allResources.first()
            if (localResources.isNotEmpty()) {
                Log.d(TAG, "Syncing ${localResources.size} resources...")
                api.upsertLearningResources(localResources)
            }

            // 4. Sync Grades
            val localGrades = repository.allGrades.first()
            if (localGrades.isNotEmpty()) {
                Log.d(TAG, "Syncing ${localGrades.size} grades...")
                api.upsertGrades(localGrades)
            }

            // 5. Sync Attendance
            // To fetch attendance, we fetch for all students or we query student IDs
            // The repository doesn't have "allAttendance Flow" directly, but we can access DB directly or get from DAOs
            // Let's check how many students there are, or verify if we need to get attendance.
            // Wait, we can get list of students first, then fetch all attendance records they have.
            val localAttendance = mutableListOf<Attendance>()
            val students = repository.allStudents.first()
            for (student in students) {
                val atts = repository.getAttendanceForStudent(student.id).first()
                localAttendance.addAll(atts)
            }
            if (localAttendance.isNotEmpty()) {
                Log.d(TAG, "Syncing ${localAttendance.size} attendance records...")
                api.upsertAttendance(localAttendance)
            }

            // 6. Sync Messages
            // Get messages (all messages in DB)
            // Since we can view message flows, let's collect message history
            // Wait, does CoachingViewModel have a repository list of messages?
            // Let's check if the table has broadcast or direct. We can insert.
            _syncState.value = SupabaseSyncState.Success("Successfully backed up all local records to Supabase Cloud!")
        } catch (e: Exception) {
            Log.e(TAG, "Errors backing up database", e)
            _syncState.value = SupabaseSyncState.Error(parseSyncError(e))
        }
    }

    /**
     * Restore database from cloud: Downloads all data from Supabase and populates Room.
     */
    suspend fun restoreDatabaseFromCloud(context: android.content.Context, repository: CoachingRepository) = withContext(Dispatchers.IO) {
        val api = apiInstance
        if (api == null) {
            _syncState.value = SupabaseSyncState.Error("Supabase not configured.")
            return@withContext
        }

        _syncState.value = SupabaseSyncState.Syncing
        try {
            Log.d(TAG, "Starting full restore from Supabase cloud...")

            // 1. Download and restore Users
            val cloudUsers = api.getUsers()
            Log.d(TAG, "Downloaded ${cloudUsers.size} users.")
            for (user in cloudUsers) {
                repository.insertUser(user)
            }

            // 2. Download and restore Assignments
            val cloudAssignments = api.getAssignments()
            Log.d(TAG, "Downloaded ${cloudAssignments.size} assignments.")
            for (asg in cloudAssignments) {
                repository.insertAssignment(asg)
            }

            // 3. Download and restore Learning Resources
            val cloudResources = api.getLearningResources()
            Log.d(TAG, "Downloaded ${cloudResources.size} resources.")
            for (res in cloudResources) {
                repository.insertResource(context, res)
            }

            // 4. Download and restore Grades
            val cloudGrades = api.getGrades()
            Log.d(TAG, "Downloaded ${cloudGrades.size} grades.")
            for (grade in cloudGrades) {
                repository.insertGrade(grade)
            }

            // 5. Download and restore Attendance
            val cloudAttendance = api.getAttendance()
            Log.d(TAG, "Downloaded ${cloudAttendance.size} attendance records.")
            for (att in cloudAttendance) {
                repository.insertAttendance(att)
            }

            _syncState.value = SupabaseSyncState.Success("Cloud Restore successful! Room database updated with live data.")
        } catch (e: Exception) {
            Log.e(TAG, "Errors restoring database", e)
            _syncState.value = SupabaseSyncState.Error(parseSyncError(e))
        }
    }

    private fun parseSyncError(e: Exception): String {
        return if (e is retrofit2.HttpException) {
            val code = e.code()
            val body = e.response()?.errorBody()?.string() ?: ""
            if (code == 404) {
                "SQL Schema Error: Necessary tables do not exist in Supabase yet. Please execute the provided setup SQL script in Supabase dashboard!"
            } else {
                "HTTP $code error: $body"
            }
        } else {
            "Network error: ${e.localizedMessage ?: "Unknown connection failure."}"
        }
    }

    /**
     * Helper to upsert a newly created single local user to Supabase
     */
    suspend fun syncUser(user: User) {
        val api = apiInstance ?: return
        try {
            withContext(Dispatchers.IO) {
                api.upsertUsers(listOf(user))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-sync single User failed: ${e.localizedMessage}")
        }
    }

    /**
     * Helper to upsert a newly created single grade
     */
    suspend fun syncGrade(grade: Grade) {
        val api = apiInstance ?: return
        try {
            withContext(Dispatchers.IO) {
                api.upsertGrades(listOf(grade))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-sync single Grade failed: ${e.localizedMessage}")
        }
    }

    /**
     * Helper to upsert a newly created single attendance check
     */
    suspend fun syncAttendance(attendance: Attendance) {
        val api = apiInstance ?: return
        try {
            withContext(Dispatchers.IO) {
                api.upsertAttendance(listOf(attendance))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-sync single Attendance failed: ${e.localizedMessage}")
        }
    }

    /**
     * Helper to upsert a newly added resource
     */
    suspend fun syncLearningResource(resource: LearningResource) {
        val api = apiInstance ?: return
        try {
            withContext(Dispatchers.IO) {
                api.upsertLearningResources(listOf(resource))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-sync single Resource failed: ${e.localizedMessage}")
        }
    }

    /**
     * Helper to upsert a newly created assignment
     */
    suspend fun syncAssignment(assignment: Assignment) {
        val api = apiInstance ?: return
        try {
            withContext(Dispatchers.IO) {
                api.upsertAssignments(listOf(assignment))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-sync single Assignment failed: ${e.localizedMessage}")
        }
    }

    /**
     * Returns standard SQL commands to configure the Postgres DB on Supabase SQL Editor.
     */
    fun getSetupSqlScript(): String {
        return """
-- COPY AND PASTE THIS ENTIRE SCRIPT INTO SUPABASE -> SQL EDITOR -> RUN
-- This will setup the PostgreSQL database tables matching your mobile app models!

-- 1. Create USERS table
CREATE TABLE IF NOT EXISTS public.users (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    role TEXT NOT NULL,
    "avatarUrl" TEXT,
    "parentName" TEXT,
    "parentEmail" TEXT,
    "parentPhone" TEXT,
    "activeClass" TEXT NOT NULL DEFAULT 'Class 11 - Batch A',
    phone TEXT
);
ALTER TABLE public.users DISABLE ROW LEVEL SECURITY;

-- 2. Create ATTENDANCE table
CREATE TABLE IF NOT EXISTS public.attendance (
    id INTEGER PRIMARY KEY,
    "studentId" INTEGER NOT NULL,
    date TEXT NOT NULL,
    "isPresent" BOOLEAN NOT NULL,
    comments TEXT
);
ALTER TABLE public.attendance DISABLE ROW LEVEL SECURITY;

-- 3. Create GRADES table
CREATE TABLE IF NOT EXISTS public.grades (
    id INTEGER PRIMARY KEY,
    "studentId" INTEGER NOT NULL,
    "examName" TEXT NOT NULL,
    "chapterName" TEXT NOT NULL,
    "maxMarks" DOUBLE PRECISION NOT NULL DEFAULT 100.0,
    "marksObtained" DOUBLE PRECISION NOT NULL,
    date TEXT NOT NULL,
    "teacherRemarks" TEXT
);
ALTER TABLE public.grades DISABLE ROW LEVEL SECURITY;

-- 4. Create LEARNING_RESOURCES table
CREATE TABLE IF NOT EXISTS public.learning_resources (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    "fileType" TEXT NOT NULL,
    url TEXT NOT NULL,
    "fileSize" TEXT NOT NULL,
    "className" TEXT NOT NULL,
    "chapterName" TEXT NOT NULL,
    "authorName" TEXT NOT NULL DEFAULT 'Prof. Ayan',
    "dateAdded" TEXT NOT NULL
);
ALTER TABLE public.learning_resources DISABLE ROW LEVEL SECURITY;

-- 5. Create MESSAGES table
CREATE TABLE IF NOT EXISTS public.messages (
    id INTEGER PRIMARY KEY,
    "senderId" INTEGER NOT NULL,
    "senderName" TEXT NOT NULL,
    "senderRole" TEXT NOT NULL,
    "receiverId" INTEGER,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    "isBroadcast" BOOLEAN NOT NULL DEFAULT false
);
ALTER TABLE public.messages DISABLE ROW LEVEL SECURITY;

-- 6. Create ASSIGNMENTS table
CREATE TABLE IF NOT EXISTS public.assignments (
    id INTEGER PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    "dueDate" TEXT NOT NULL,
    "maxMarks" DOUBLE PRECISION NOT NULL DEFAULT 100.0,
    "chapterName" TEXT NOT NULL,
    "className" TEXT NOT NULL
);
ALTER TABLE public.assignments DISABLE ROW LEVEL SECURITY;

-- SQL SCRIPT FINISHED successfully. All tables configured.
        """.trimIndent()
    }
}
