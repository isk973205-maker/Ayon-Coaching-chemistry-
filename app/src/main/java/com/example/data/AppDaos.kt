package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE role = 'STUDENT' ORDER BY name ASC")
    fun getAllStudents(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Int): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE studentId = :studentId ORDER BY date DESC")
    fun getAttendanceForStudent(studentId: Int): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE date = :date")
    fun getAttendanceByDate(date: String): Flow<List<Attendance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: Attendance): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMultipleAttendance(attendanceList: List<Attendance>)

    @Query("SELECT COUNT(*) FROM attendance WHERE studentId = :studentId AND isPresent = 1")
    fun getPresentCount(studentId: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance WHERE studentId = :studentId")
    fun getTotalCount(studentId: Int): Flow<Int>
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades WHERE studentId = :studentId ORDER BY date DESC")
    fun getGradesForStudent(studentId: Int): Flow<List<Grade>>

    @Query("SELECT * FROM grades ORDER BY date DESC")
    fun getAllGrades(): Flow<List<Grade>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrade(grade: Grade): Long

    @Delete
    suspend fun deleteGrade(grade: Grade)
}

@Dao
interface LearningResourceDao {
    @Query("SELECT id, title, fileType, CASE WHEN url LIKE 'data:application/pdf;base64,%' THEN 'base64_cached' ELSE url END AS url, fileSize, className, chapterName, authorName, dateAdded FROM learning_resources ORDER BY dateAdded DESC")
    fun getAllResources(): Flow<List<LearningResource>>

    @Query("SELECT id, title, fileType, CASE WHEN url LIKE 'data:application/pdf;base64,%' THEN 'base64_cached' ELSE url END AS url, fileSize, className, chapterName, authorName, dateAdded FROM learning_resources WHERE className = :className AND chapterName = :chapterName ORDER BY dateAdded DESC")
    fun getResourcesForChapter(className: String, chapterName: String): Flow<List<LearningResource>>

    @Query("SELECT url FROM learning_resources WHERE id = :id")
    suspend fun getResourceFullUrl(id: Int): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(resource: LearningResource): Long

    @Delete
    suspend fun deleteResource(resource: LearningResource)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE receiverId = :userId OR receiverId IS NULL OR senderId = :userId ORDER BY timestamp DESC")
    fun getMessagesForUser(userId: Int): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE isBroadcast = 1 OR receiverId IS NULL ORDER BY timestamp DESC")
    fun getBroadcastMessages(): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignments ORDER BY dueDate ASC")
    fun getAllAssignments(): Flow<List<Assignment>>

    @Query("SELECT * FROM assignments WHERE className = :className ORDER BY dueDate ASC")
    fun getAssignmentsForClass(className: String): Flow<List<Assignment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: Assignment): Long

    @Delete
    suspend fun deleteAssignment(assignment: Assignment)
}
