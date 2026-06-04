package com.example.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import java.io.Serializable

data class DrawingStroke(
    val points: List<Pair<Float, Float>>,
    val colorHex: Long,
    val strokeWidth: Float
) : Serializable

class CoachingViewModel(
    application: Application,
    private val repository: CoachingRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("coaching_prefs", Context.MODE_PRIVATE)

    private fun persistSession(userId: Int, role: String) {
        prefs.edit()
            .putInt("logged_in_user_id", userId)
            .putString("logged_in_user_role", role)
            .apply()
    }

    private fun clearSession() {
        prefs.edit()
            .putInt("logged_in_user_id", -1)
            .putString("logged_in_user_role", "")
            .apply()
    }

    // Active session user state (Prof. Ayan or Rahul Sharma)
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // All registered users & students
    val allUsers: StateFlow<List<User>> = repository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allStudents: StateFlow<List<User>> = repository.allStudents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Learning Resources
    val allResources: StateFlow<List<LearningResource>> = repository.allResources.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Assignments
    val allAssignments: StateFlow<List<Assignment>> = repository.allAssignments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Grade history
    val allGrades: StateFlow<List<Grade>> = repository.allGrades.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Shared State for Selected Role Switching
    private val _currentRole = MutableStateFlow<String>("TEACHER") // "TEACHER" or "STUDENT"
    val currentRole: StateFlow<String> = _currentRole.asStateFlow()

    // Language Selection state: "EN" (English) or "BN" (Bengali)
    private val _currentLanguage = MutableStateFlow<String>("EN")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
    }

    // Active Student details under progress verification (for Teacher view)
    private val _selectedStudentForReport = MutableStateFlow<User?>(null)
    val selectedStudentForReport: StateFlow<User?> = _selectedStudentForReport.asStateFlow()

    // State for generated AI parent report
    private val _isGeneratingReport = MutableStateFlow(false)
    val isGeneratingReport: StateFlow<Boolean> = _isGeneratingReport.asStateFlow()

    private val _generatedReportText = MutableStateFlow<String?>(null)
    val generatedReportText: StateFlow<String?> = _generatedReportText.asStateFlow()

    // Messaging feeds
    private val _activeChatUserId = MutableStateFlow<Int?>(null) // Student ID we are direct messaging, null for broadcast
    val activeChatUserId: StateFlow<Int?> = _activeChatUserId.asStateFlow()

    // Logs of simulated push alerts and SMS to parents
    private val _simulatedParentAlerts = MutableStateFlow<List<String>>(emptyList())
    val simulatedParentAlerts: StateFlow<List<String>> = _simulatedParentAlerts.asStateFlow()

    // Local profile photos (presets to simulate profile uploading easily)
    val presetAvatars = listOf(
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80",
        "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&w=200&q=80",
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&w=200&q=80",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=200&q=80"
    )

    // --- PDF Reading & Learning Progress State ---
    // Maps ResourceId to current page index (0-based)
    private val _pdfReadingProgress = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val pdfReadingProgress: StateFlow<Map<Int, Int>> = _pdfReadingProgress.asStateFlow()

    // Maps ResourceId to bookmarked pages
    private val _pdfBookmarks = MutableStateFlow<Map<Int, Set<Int>>>(emptyMap())
    val pdfBookmarks: StateFlow<Map<Int, Set<Int>>> = _pdfBookmarks.asStateFlow()

    // Maps ResourceId to Map of PageIndex to highlighted terms
    private val _pdfHighlights = MutableStateFlow<Map<Int, Map<Int, Set<String>>>>(emptyMap())
    val pdfHighlights: StateFlow<Map<Int, Map<Int, Set<String>>>> = _pdfHighlights.asStateFlow()

    // Maps ResourceId to Map of PageIndex to drawing strokes (Pair of relative coordinates x, y)
    private val _pdfDoodles = MutableStateFlow<Map<Int, Map<Int, List<DrawingStroke>>>>(emptyMap())
    val pdfDoodles: StateFlow<Map<Int, Map<Int, List<DrawingStroke>>>> = _pdfDoodles.asStateFlow()

    // Maps ResourceId to personal reading/review notes
    private val _pdfPersonalNotes = MutableStateFlow<Map<Int, String>>(emptyMap())
    val pdfPersonalNotes: StateFlow<Map<Int, String>> = _pdfPersonalNotes.asStateFlow()

    fun updatePdfProgress(resourceId: Int, pageIndex: Int) {
        val current = _pdfReadingProgress.value.toMutableMap()
        current[resourceId] = pageIndex
        _pdfReadingProgress.value = current
    }

    fun togglePdfBookmark(resourceId: Int, pageIndex: Int) {
        val current = _pdfBookmarks.value.toMutableMap()
        val currentSet = current[resourceId]?.toMutableSet() ?: mutableSetOf()
        if (currentSet.contains(pageIndex)) {
            currentSet.remove(pageIndex)
        } else {
            currentSet.add(pageIndex)
        }
        current[resourceId] = currentSet
        _pdfBookmarks.value = current
    }

    fun addPdfHighlight(resourceId: Int, pageIndex: Int, text: String) {
        val current = _pdfHighlights.value.toMutableMap()
        val pageMap = current[resourceId]?.toMutableMap() ?: mutableMapOf()
        val highlightSet = pageMap[pageIndex]?.toMutableSet() ?: mutableSetOf()
        highlightSet.add(text)
        pageMap[pageIndex] = highlightSet
        current[resourceId] = pageMap
        _pdfHighlights.value = current
    }

    fun removePdfHighlight(resourceId: Int, pageIndex: Int, text: String) {
        val current = _pdfHighlights.value.toMutableMap()
        val pageMap = current[resourceId]?.toMutableMap() ?: mutableMapOf()
        val highlightSet = pageMap[pageIndex]?.toMutableSet() ?: mutableSetOf()
        highlightSet.remove(text)
        pageMap[pageIndex] = highlightSet
        current[resourceId] = pageMap
        _pdfHighlights.value = current
    }

    fun addPdfDoodleStroke(resourceId: Int, pageIndex: Int, stroke: DrawingStroke) {
        val current = _pdfDoodles.value.toMutableMap()
        val pageMap = current[resourceId]?.toMutableMap() ?: mutableMapOf()
        val strokeList = pageMap[pageIndex]?.toMutableList() ?: mutableListOf()
        strokeList.add(stroke)
        pageMap[pageIndex] = strokeList
        current[resourceId] = pageMap
        _pdfDoodles.value = current
    }

    fun clearPdfDoodles(resourceId: Int, pageIndex: Int) {
        val current = _pdfDoodles.value.toMutableMap()
        val pageMap = current[resourceId]?.toMutableMap() ?: mutableMapOf()
        pageMap.remove(pageIndex)
        current[resourceId] = pageMap
        _pdfDoodles.value = current
    }

    fun savePdfPersonalNote(resourceId: Int, note: String) {
        val current = _pdfPersonalNotes.value.toMutableMap()
        current[resourceId] = note
        _pdfPersonalNotes.value = current
    }

    // Initialization
    init {
        createNotificationChannel()
        viewModelScope.launch {
            var sessionRestorationAttempted = false
            allUsers.collect { users ->
                if (users.isNotEmpty()) {
                    if (_selectedStudentForReport.value == null) {
                        _selectedStudentForReport.value = users.find { it.role == "STUDENT" }
                    }
                    if (!sessionRestorationAttempted && _currentUser.value == null) {
                        sessionRestorationAttempted = true
                        val savedUid = prefs.getInt("logged_in_user_id", -1)
                        val savedRole = prefs.getString("logged_in_user_role", "") ?: ""
                        if (savedUid != -1) {
                            val savedUser = users.find { it.id == savedUid }
                            if (savedUser != null) {
                                _currentUser.value = savedUser
                                _currentRole.value = savedUser.role
                            }
                        } else if (savedRole.isNotEmpty()) {
                            _currentRole.value = savedRole
                        }
                    }
                }
            }
        }
        
        // Safe backend-level self-healing startup seed to guarantee the master teacher profile always exists
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            val currentUsers = allUsers.value
            val hasTeacher = currentUsers.any { it.role == "TEACHER" }
            if (!hasTeacher) {
                repository.insertUser(
                    User(
                        name = "Prof. Ayan",
                        role = "TEACHER",
                        avatarUrl = null,
                        parentName = null,
                        parentEmail = null,
                        parentPhone = null,
                        activeClass = "Class 11 & 12",
                        phone = "8389044540"
                    )
                )
            }
        }
    }

    // Perform login with mobile number
    fun loginWithNumber(phone: String, role: String): Boolean {
        if (phone == "8389044540" && role == "TEACHER") {
            // Guarantee teacher exists and is logged in
            val teacher = allUsers.value.find { it.role == "TEACHER" && it.phone == "8389044540" }
            if (teacher != null) {
                _currentUser.value = teacher
                _currentRole.value = "TEACHER"
                persistSession(teacher.id, "TEACHER")
                return true
            }
        }
        val matchedUser = getUserByPhoneAndRole(phone, role)
        if (matchedUser != null) {
            _currentUser.value = matchedUser
            _currentRole.value = role
            persistSession(matchedUser.id, role)
            return true
        }
        return false
    }

    fun loginWithEmailAndPasswordFallback(email: String, passwordEntered: String, role: String): Boolean {
        val matchedUser = getUserByPhoneAndRole(email, role)
        if (matchedUser != null) {
            // If the user has a password, we must strictly check it
            if (matchedUser.password != null && matchedUser.password != passwordEntered) {
                return false
            }
            // If user exists but has no password set (legacy seed or social), save the entered password as their secure password
            if (matchedUser.password == null && passwordEntered.isNotEmpty()) {
                viewModelScope.launch {
                    val updated = matchedUser.copy(password = passwordEntered)
                    repository.updateUser(updated)
                }
            }
            _currentUser.value = matchedUser
            _currentRole.value = role
            persistSession(matchedUser.id, role)
            return true
        }
        return false
    }

    fun getUserByPhoneAndRole(phone: String, role: String): User? {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) return null
        val users = allUsers.value
        return users.find {
            val uPhone = it.phone?.trim() ?: ""
            val cleanUserPhone = uPhone.replace(" ", "").replace("+91", "").replace("-", "")
            val cleanInputPhone = trimmed.replace(" ", "").replace("+91", "").replace("-", "")
            (uPhone == trimmed || cleanUserPhone == cleanInputPhone) && it.role == role
        }
    }

    fun enrollNewStudentAndLogin(name: String, phone: String, selectedClass: String, passwordEntered: String? = null) {
        viewModelScope.launch {
            val trimmedName = name.trim().ifEmpty { "New Student" }
            val cleanPhone = phone.trim()
            val newStudent = User(
                name = trimmedName,
                role = "STUDENT",
                avatarUrl = null,
                parentName = "Mr. Parent",
                parentEmail = "parent@example.com",
                parentPhone = "+91 99999 99999",
                activeClass = selectedClass,
                phone = cleanPhone,
                password = passwordEntered
            )
            val newId = repository.insertUser(newStudent)
            val finalUser = newStudent.copy(id = newId.toInt())
            _currentUser.value = finalUser
            _currentRole.value = "STUDENT"
            persistSession(finalUser.id, "STUDENT")
            // Sync to Supabase
            SupabaseService.syncUser(finalUser)
        }
    }

    // --- REAL SUPABASE AUTHENTICATION INTEGRATION ---
    suspend fun signUpWithEmailSupabase(email: String, password: String, role: String, fullName: String, targetClass: String): String? {
        return try {
            val response = SupabaseService.signUpWithEmail(email, password, mapOf("role" to role, "name" to fullName))
            if (response != null) {
                // Ensure record is prefilled in Room / Supabase DB too
                val existingUser = allUsers.value.find { it.phone?.trim() == email.trim() || it.name == fullName }
                if (existingUser == null) {
                    val newUser = User(
                        name = fullName,
                        role = role,
                        avatarUrl = null,
                        parentName = if (role == "STUDENT") "Mr. Parent" else null,
                        parentEmail = if (role == "STUDENT") "parent@example.com" else null,
                        parentPhone = null,
                        activeClass = targetClass,
                        phone = email
                    )
                    val newId = repository.insertUser(newUser)
                    val finalUser = newUser.copy(id = newId.toInt())
                    SupabaseService.syncUser(finalUser)
                }
                null // Success sign up
            } else {
                "Auth server error: Check your Internet / URL Credentials"
            }
        } catch (e: Exception) {
            e.localizedMessage ?: "Sign up failed."
        }
    }

    suspend fun loginWithEmailSupabase(email: String, password: String): String? {
        return try {
            val response = SupabaseService.logInWithEmail(email, password)
            if (response != null) {
                var matchedUser = allUsers.value.find { it.phone?.trim() == email.trim() }
                if (matchedUser == null) {
                    val role = if (email.contains("faculty") || email == "8389044540") "TEACHER" else "STUDENT"
                    val newUser = User(
                        name = email.substringBefore("@"),
                        role = role,
                        avatarUrl = null,
                        activeClass = if (role == "TEACHER") "Class 11 & 12" else "Class 11 - Batch A",
                        phone = email
                    )
                    val newId = repository.insertUser(newUser)
                    matchedUser = newUser.copy(id = newId.toInt())
                    SupabaseService.syncUser(matchedUser)
                }
                _currentUser.value = matchedUser
                _currentRole.value = matchedUser.role
                persistSession(matchedUser.id, matchedUser.role)
                null // Success
            } else {
                "Authentication failure: check email or connection."
            }
        } catch (e: Exception) {
            e.localizedMessage ?: "Login failed."
        }
    }

    suspend fun sendSupabasePhoneOtp(phone: String): String? {
        return try {
            SupabaseService.sendPhoneOtp(phone)
            null // Success callback
        } catch (e: Exception) {
            e.localizedMessage ?: "Failed to transmit OTP."
        }
    }

    suspend fun verifySupabasePhoneOtp(phone: String, token: String, role: String, targetClass: String, fullName: String? = null): String? {
        return try {
            val response = SupabaseService.verifyPhoneOtp(phone, token)
            if (response != null) {
                var matchedUser = getUserByPhoneAndRole(phone, role)
                if (matchedUser == null) {
                    val finalName = fullName?.trim() ?: "Ayan Student"
                    val newUser = User(
                        name = finalName,
                        role = role,
                        avatarUrl = null,
                        parentName = if (role == "STUDENT") "Mr. Parent" else null,
                        parentEmail = if (role == "STUDENT") "parent@example.com" else null,
                        parentPhone = null,
                        activeClass = targetClass,
                        phone = phone
                    )
                    val newId = repository.insertUser(newUser)
                    matchedUser = newUser.copy(id = newId.toInt())
                    SupabaseService.syncUser(matchedUser)
                }
                _currentUser.value = matchedUser
                _currentRole.value = matchedUser.role
                persistSession(matchedUser.id, matchedUser.role)
                null // Success verified
            } else {
                "Verification token rejected by server."
            }
        } catch (e: Exception) {
            e.localizedMessage ?: "OTP verify failed."
        }
    }

    fun loginUser(user: User, selectedClass: String? = null) {
        viewModelScope.launch {
            var finalUser = user
            if (user.role == "STUDENT" && selectedClass != null && user.activeClass != selectedClass) {
                finalUser = user.copy(activeClass = selectedClass)
                repository.updateUser(finalUser)
                // Sync updated user to Supabase
                SupabaseService.syncUser(finalUser)
            }
            _currentUser.value = finalUser
            _currentRole.value = finalUser.role
            persistSession(finalUser.id, finalUser.role)
        }
    }

    // Clear session and change target role selection
    fun switchRole(role: String) {
        viewModelScope.launch {
            _currentRole.value = role
            _currentUser.value = null
            clearSession()
        }
    }

    fun logout() {
        _currentUser.value = null
        clearSession()
    }

    fun selectStudentForReport(student: User) {
        _selectedStudentForReport.value = student
        _generatedReportText.value = null
    }

    fun selectChatUser(userId: Int?) {
        _activeChatUserId.value = userId
    }

    // Fetch message stream for currently selected login
    fun getMessagesForCurrentUser(): Flow<List<Message>> {
        return currentUser.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else repository.getMessagesForUser(user.id)
        }
    }

    // Get attendance stats for a student
    fun getAttendanceStats(studentId: Int): Flow<Map<String, Any>> {
        return combine(
            repository.getPresentCount(studentId),
            repository.getTotalCount(studentId)
        ) { present, total ->
            val rate = if (total > 0) String.format("%.1f%%", (present.toDouble() / total) * 100) else "100%"
            mapOf(
                "present" to present,
                "total" to total,
                "rate" to rate
            )
        }
    }

    fun getStudentGrades(studentId: Int): Flow<List<Grade>> {
        return repository.getGradesForStudent(studentId)
    }

    // 1. Mark attendance for today
    fun submitAttendanceRecord(studentId: Int, isPresent: Boolean, comments: String?) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val record = Attendance(
                studentId = studentId,
                date = dateStr,
                isPresent = isPresent,
                comments = comments
            )
            val newId = repository.insertAttendance(record)
            val finalRecord = record.copy(id = newId.toInt())
            
            // Sync to Supabase
            SupabaseService.syncAttendance(finalRecord)
            
            // Push Notification Simulation
            showSystemNotification(
                title = "Attendance Marked",
                content = "Rahul Sharma is marked ${if (isPresent) "PRESENT" else "ABSENT"} for today's Chemistry class."
            )
        }
    }

    // Bulk submission helper
    fun submitBulkAttendance(records: List<Attendance>) {
        viewModelScope.launch {
            repository.insertMultipleAttendance(records)
            // Backup/sync bulk additions
            SupabaseService.backupLocalDatabaseToCloud(repository)
            showSystemNotification(
                title = "Session Attendance Submitted",
                content = "Coaching session attendance successfully compiled for all batch students."
            )
        }
    }

    // 2. Track grade
    fun addNewGrade(studentId: Int, examName: String, chapterName: String, score: Double, maxScore: Double, remarks: String) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val grade = Grade(
                studentId = studentId,
                examName = examName,
                chapterName = chapterName,
                marksObtained = score,
                maxMarks = maxScore,
                date = dateStr,
                teacherRemarks = remarks
            )
            val newId = repository.insertGrade(grade)
            val finalGrade = grade.copy(id = newId.toInt())
            
            // Sync to Supabase
            SupabaseService.syncGrade(finalGrade)
            
            showSystemNotification(
                title = "New Grades Published",
                content = "Published score for $examName chapter: $chapterName ($score/$maxScore)."
            )
        }
    }

    // 3. Automated Parent Updates (using Gemini!)
    fun generateAndSendParentUpdate(student: User, teacherNotes: String) {
        viewModelScope.launch {
            _isGeneratingReport.value = true
            _generatedReportText.value = null

            // Aggregate metrics
            val presentFlow = repository.getPresentCount(student.id).first()
            val totalFlow = repository.getTotalCount(student.id).first()
            val attendanceRate = if (totalFlow > 0) String.format("%.1f%%", (presentFlow.toDouble() / totalFlow) * 100) else "100%"
            
            val gradesList = repository.getGradesForStudent(student.id).first()

            val aiSummary = GeminiService.generateParentUpdate(
                studentName = student.name,
                attendanceRate = attendanceRate,
                presentCount = presentFlow,
                totalCount = totalFlow,
                gradesList = gradesList,
                teacherNotes = teacherNotes
            )

            _generatedReportText.value = aiSummary
            _isGeneratingReport.value = false

            // Append parents alert simulation logs
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val alertMsg = "[$timestamp] Parent Notification Sent to ${student.parentName ?: "Parent"} (${student.parentPhone}): Attendance: $attendanceRate, Avg Grades: Good"
            _simulatedParentAlerts.value = listOf(alertMsg) + _simulatedParentAlerts.value

            showSystemNotification(
                title = "Automated Progress Update Sent",
                content = "AI update successfully dispatched directly to ${student.parentName}'s mobile application."
            )
        }
    }

    // 4. Share supplementary resources
    fun shareLearningResource(title: String, fileType: String, className: String, chapterName: String, url: String, customSize: String? = null) {
        viewModelScope.launch {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val size = customSize ?: when(fileType) {
                "PDF" -> "3.4 MB"
                "PPTX" -> "8.1 MB"
                "DOCX" -> "1.2 MB"
                else -> "24.5 MB"
            }
            
            var savedUrl = url
            if (url.startsWith("content://") && !url.contains("com.ayan.coaching.provider")) {
                try {
                    val context = getApplication<Application>()
                    val uri = android.net.Uri.parse(url)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        savedUrl = "data:application/pdf;base64,$base64Str"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val resource = LearningResource(
                title = title,
                fileType = fileType,
                url = savedUrl.ifEmpty { "https://example.com/ayan_coaching/docs/${title.lowercase().replace(" ", "_")}.${fileType.lowercase()}" },
                fileSize = size,
                className = className,
                chapterName = chapterName,
                authorName = _currentUser.value?.name ?: "Prof. Ayan",
                dateAdded = dateStr
            )
            val newId = repository.insertResource(getApplication(), resource)
            val finalResource = resource.copy(id = newId.toInt())
            
            // Sync to Supabase
            SupabaseService.syncLearningResource(finalResource)

            // Auto broadcast notify
            val welcomeBroadcast = Message(
                senderId = _currentUser.value?.id ?: 1,
                senderName = _currentUser.value?.name ?: "Prof. Ayan",
                senderRole = "TEACHER",
                receiverId = null,
                title = "New Chemistry Resource Uploaded",
                content = "I have uploaded a new $fileType lecture material: \"$title\" for chapter \"$chapterName\". Please read to prepare for upcoming tests.",
                isBroadcast = true
            )
            repository.insertMessage(welcomeBroadcast)

            showSystemNotification(
                title = "Resource Added: $title",
                content = "New files successfully integrated into the $chapterName PDF and lecture archives."
            )
        }
    }

    fun deleteResource(resource: LearningResource) {
        viewModelScope.launch {
            repository.deleteResource(resource)
        }
    }

    suspend fun getFullResourceUrl(resourceId: Int): String {
        return repository.getResourceFullUrl(resourceId) ?: ""
    }

    fun downloadResource(context: Context, res: LearningResource) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val url = getFullResourceUrl(res.id)
            val resolvedRes = res.copy(url = url)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                downloadPdfResource(context, resolvedRes)
            }
        }
    }

    // 5. Send message / Alerts
    fun sendMessage(receiverId: Int?, title: String, content: String) {
        viewModelScope.launch {
            val sender = _currentUser.value ?: return@launch
            val isBroadcast = receiverId == null
            val msg = Message(
                senderId = sender.id,
                senderName = sender.name,
                senderRole = sender.role,
                receiverId = receiverId,
                title = title,
                content = content,
                isBroadcast = isBroadcast
            )
            repository.insertMessage(msg)

            val alertLabel = if (isBroadcast) "Broadcast Announcement Posted" else "Direct Message Dispatched"
            showSystemNotification(
                title = alertLabel,
                content = "Message: \"${if (title.length > 20) title.take(20) + "..." else title}\""
            )

            // If the sender is a student, trigger an automated helpful reply from Prof. Ayan!
            if (sender.role == "STUDENT") {
                launch {
                    kotlinx.coroutines.delay(2000)
                    val replyContent = when {
                        content.contains("atomic", ignoreCase = true) || title.contains("atomic", ignoreCase = true) -> 
                            "Hi ${sender.name}, regarding your doubt on Atomic Structure: Note that Bohr's model is only applicable for single-electron systems like H, He+, Li2+. For multi-electron systems, wave mechanics (Schrödinger equation) governs electron distributions."
                        content.contains("kinetic", ignoreCase = true) || title.contains("kinetic", ignoreCase = true) ->
                            "Hello ${sender.name}, on Chemical Kinetics: Remember that activation energy (Ea) is independent of temperature, whereas rate constant (k) depends exponentially on temperature as per the Arrhenius Equation. Try practicing the log-based integration questions first."
                        content.contains("organic", ignoreCase = true) || title.contains("organic", ignoreCase = true) ->
                            "Hey ${sender.name}, for Organic Electrophilic Substitutions: Nitro groups (-NO2) are strongly deactivating and meta-directing due to resonance and inductive electron withdrawal. Methyl and hydroxyl groups are activating and ortho/para-directing."
                        else ->
                            "Hi ${sender.name}, I have received your chemistry query on '$title'. I recommend checking the textbook pages in the Study Notes section representing this chapter, and I'll walk you through the fundamental principles in our live session tomorrow!"
                    }
                    val teacherReply = Message(
                        senderId = 1, // Prof. Ayan's ID is 1
                        senderName = "Prof. Ayan",
                        senderRole = "TEACHER",
                        receiverId = sender.id, // Direct message back to the student
                        title = "Re: $title",
                        content = replyContent,
                        isBroadcast = false
                    )
                    repository.insertMessage(teacherReply)
                    showSystemNotification(
                        title = "Prof. Ayan Replied",
                        content = "New expert chemistry response matches your doubt on '$title'."
                    )
                }
            }
        }
    }

    // 6. Student tasks
    fun uploadProfilePhoto(avatarUrl: String) {
        viewModelScope.launch {
            val user = _currentUser.value ?: return@launch
            val updatedUser = user.copy(avatarUrl = avatarUrl)
            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser
            showSystemNotification(
                title = "Profile Photo Updated",
                content = "Ayan Coaching server successfully saved your selected profile avatar."
            )
        }
    }

    fun updateUserData(updatedUser: User) {
        viewModelScope.launch {
            repository.updateUser(updatedUser)
            _currentUser.value = updatedUser
            // Sync to Supabase
            try {
                SupabaseService.syncUser(updatedUser)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            showSystemNotification(
                title = "Profile Updated Successfully",
                content = "Your chemistry portal details have been synchronized on all nodes."
            )
        }
    }

    fun downloadStudySchedule() {
        viewModelScope.launch {
            showSystemNotification(
                title = "Schedule Download Started",
                content = "Downloading 'Weekly Chemistry Schedule - Batch A.pdf' (1.4MB)..."
            )
            kotlinx.coroutines.delay(1500)
            showSystemNotification(
                title = "Download Complete",
                content = "Weekly study schedule successfully saved to standard Downloads folder."
            )
        }
    }

    fun addNewAssignment(title: String, description: String, dueDate: String, maxMarks: Double, chapterName: String, className: String) {
        viewModelScope.launch {
            val assignment = Assignment(
                title = title,
                description = description,
                dueDate = dueDate,
                maxMarks = maxMarks,
                chapterName = chapterName,
                className = className
            )
            val newId = repository.insertAssignment(assignment)
            val finalAssignment = assignment.copy(id = newId.toInt())
            
            // Sync to Supabase
            SupabaseService.syncAssignment(finalAssignment)
            
            showSystemNotification(
                title = "New Assignment Assigned",
                content = "Assigned '$title' for $className. Due date: $dueDate."
            )
        }
    }

    fun deleteAssignment(assignment: Assignment) {
        viewModelScope.launch {
            repository.deleteAssignment(assignment)
        }
    }

    // --- Supabase Cloud Operations ---
    val supabaseSyncState: StateFlow<SupabaseSyncState> = SupabaseService.syncState
    val supabaseConnectionStatus: StateFlow<SupabaseConnectionStatus> = SupabaseService.connectionStatus

    fun configureSupabase(url: String, key: String, projId: String) {
        SupabaseService.configure(url, key, projId)
        checkSupabaseConnection()
    }

    fun checkSupabaseConnection() {
        viewModelScope.launch {
            SupabaseService.checkConnection()
        }
    }

    fun backupToSupabase() {
        viewModelScope.launch {
            SupabaseService.backupLocalDatabaseToCloud(repository)
        }
    }

    fun restoreFromSupabase() {
        viewModelScope.launch {
            SupabaseService.restoreDatabaseFromCloud(getApplication(), repository)
        }
    }

    // Push Notification Framework integration
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Ayan Coaching Notifications"
            val descriptionText = "Displays coaching alerts, grades updates, and messaging records."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("ayan_coaching_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showSystemNotification(title: String, content: String) {
        val context = getApplication<Application>()
        
        // Android 13+ runtime notification permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                // If permission is not clean, we gracefully skip posting instead of throwing a SecurityException
                return
            }
        }

        try {
            val builder = NotificationCompat.Builder(context, "ayan_coaching_channel")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class CoachingViewModelFactory(
    private val application: Application,
    private val repository: CoachingRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CoachingViewModel::class.java)) {
            return CoachingViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
