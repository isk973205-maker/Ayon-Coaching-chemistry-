package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class CoachingRepository(private val database: AppDatabase) {

    private val userDao = database.userDao()
    private val attendanceDao = database.attendanceDao()
    private val gradeDao = database.gradeDao()
    private val resourceDao = database.learningResourceDao()
    private val messageDao = database.messageDao()
    private val assignmentDao = database.assignmentDao()

    val allUsers: Flow<List<User>> = userDao.getAllUsers()
    val allStudents: Flow<List<User>> = userDao.getAllStudents()
    val allGrades: Flow<List<Grade>> = gradeDao.getAllGrades()
    val allResources: Flow<List<LearningResource>> = resourceDao.getAllResources()
    val allAssignments: Flow<List<Assignment>> = assignmentDao.getAllAssignments()

    suspend fun insertUser(user: User): Long = userDao.insertUser(user)
    suspend fun updateUser(user: User) = userDao.updateUser(user)
    suspend fun deleteUser(user: User) = userDao.deleteUser(user)
    suspend fun getUserById(userId: Int): User? = userDao.getUserById(userId)

    fun getAttendanceForStudent(studentId: Int): Flow<List<Attendance>> =
        attendanceDao.getAttendanceForStudent(studentId)

    fun getAttendanceByDate(date: String): Flow<List<Attendance>> =
        attendanceDao.getAttendanceByDate(date)

    suspend fun insertAttendance(attendance: Attendance): Long =
        attendanceDao.insertAttendance(attendance)

    suspend fun insertMultipleAttendance(attendanceList: List<Attendance>) =
        attendanceDao.insertMultipleAttendance(attendanceList)

    fun getPresentCount(studentId: Int): Flow<Int> = attendanceDao.getPresentCount(studentId)
    fun getTotalCount(studentId: Int): Flow<Int> = attendanceDao.getTotalCount(studentId)

    fun getGradesForStudent(studentId: Int): Flow<List<Grade>> =
        gradeDao.getGradesForStudent(studentId)

    suspend fun insertGrade(grade: Grade): Long = gradeDao.insertGrade(grade)
    suspend fun deleteGrade(grade: Grade) = gradeDao.deleteGrade(grade)

    fun getResourcesForChapter(className: String, chapterName: String): Flow<List<LearningResource>> =
        resourceDao.getResourcesForChapter(className, chapterName)

    suspend fun getResourceFullUrl(id: Int): String? =
        resourceDao.getResourceFullUrl(id)

    suspend fun insertResource(context: Context, resource: LearningResource): Long {
        var finalResource = resource
        if (resource.url.startsWith("data:application/pdf;base64,")) {
            try {
                val base64Data = resource.url.substringAfter("base64,")
                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                val customFolder = java.io.File(context.filesDir, "shared_pdfs")
                if (!customFolder.exists()) {
                    customFolder.mkdirs()
                }
                val sanitizedTitle = resource.title.replace("[^a-zA-Z0-9]".toRegex(), "_")
                val fileName = "PDF_${System.currentTimeMillis()}_${sanitizedTitle}.pdf"
                val file = java.io.File(customFolder, fileName)
                file.writeBytes(bytes)
                finalResource = resource.copy(url = file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return resourceDao.insertResource(finalResource)
    }

    suspend fun deleteResource(resource: LearningResource) =
        resourceDao.deleteResource(resource)

    fun getMessagesForUser(userId: Int): Flow<List<Message>> =
        messageDao.getMessagesForUser(userId)

    fun getBroadcastMessages(): Flow<List<Message>> =
        messageDao.getBroadcastMessages()

    suspend fun insertMessage(message: Message) =
        messageDao.insertMessage(message)

    fun getAssignmentsForClass(className: String): Flow<List<Assignment>> =
        assignmentDao.getAssignmentsForClass(className)

    suspend fun insertAssignment(assignment: Assignment): Long =
        assignmentDao.insertAssignment(assignment)

    suspend fun deleteAssignment(assignment: Assignment) =
        assignmentDao.deleteAssignment(assignment)
}
