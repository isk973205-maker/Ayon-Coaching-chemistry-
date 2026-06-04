package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val role: String, // "TEACHER", "STUDENT"
    val avatarUrl: String? = null,
    val parentName: String? = null,
    val parentEmail: String? = null,
    val parentPhone: String? = null,
    val activeClass: String = "Class 11 - Batch A", // "Class 11 - Batch A", "Class 12 - Batch B", etc.
    val phone: String? = null,
    val password: String? = null
) : Serializable

@Entity(tableName = "attendance")
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val date: String, // "YYYY-MM-DD"
    val isPresent: Boolean,
    val comments: String? = null
) : Serializable

@Entity(tableName = "grades")
data class Grade(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val examName: String, // e.g. "Periodic Test 1", "Thermodynamics Quiz"
    val chapterName: String, // e.g. "Chemical Bonding"
    val maxMarks: Double = 100.0,
    val marksObtained: Double,
    val date: String,
    val teacherRemarks: String? = null
) : Serializable

@Entity(tableName = "learning_resources")
data class LearningResource(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val fileType: String, // "PDF", "PPTX", "DOCX", "VIDEO"
    val url: String, // Mock local resource or online address
    val fileSize: String, // e.g., "2.4 MB"
    val className: String, // e.g., "Class 11"
    val chapterName: String, // e.g., "Atomic Structure"
    val authorName: String = "Prof. Ayan",
    val dateAdded: String
) : Serializable

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderId: Int,
    val senderName: String,
    val senderRole: String,
    val receiverId: Int?, // null if broadcast
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isBroadcast: Boolean = false
) : Serializable

@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val dueDate: String, // "YYYY-MM-DD"
    val maxMarks: Double = 100.0,
    val chapterName: String,
    val className: String
) : Serializable
