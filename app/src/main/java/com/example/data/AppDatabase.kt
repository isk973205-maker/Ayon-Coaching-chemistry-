package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Database(
    entities = [
        User::class,
        Attendance::class,
        Grade::class,
        LearningResource::class,
        Message::class,
        Assignment::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun gradeDao(): GradeDao
    abstract fun learningResourceDao(): LearningResourceDao
    abstract fun messageDao(): MessageDao
    abstract fun assignmentDao(): AssignmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ayan_coaching_db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            scope.launch(Dispatchers.IO) {
                var retries = 0
                while (INSTANCE == null && retries < 50) {
                    kotlinx.coroutines.delay(100)
                    retries++
                }
                INSTANCE?.let { database ->
                    populateDatabase(database)
                }
            }
        }

        private suspend fun populateDatabase(db: AppDatabase) {
            val userDao = db.userDao()
            val attendanceDao = db.attendanceDao()
            val gradeDao = db.gradeDao()
            val resourceDao = db.learningResourceDao()
            val messageDao = db.messageDao()
            val assignmentDao = db.assignmentDao()

            val calendar = Calendar.getInstance()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            // Seed Users (Teacher & Students)
            val teacherId = userDao.insertUser(
                User(
                    name = "Prof. Ayan",
                    role = "TEACHER",
                    avatarUrl = null,
                    parentName = null,
                    parentEmail = null,
                    parentPhone = null,
                    activeClass = "Class 11 & 12",
                    phone = "8389044540",
                    password = "password123"
                )
            ).toInt()

            val s1 = userDao.insertUser(
                User(
                    name = "Injamam sk",
                    role = "STUDENT",
                    avatarUrl = null,
                    parentName = "Mr. Sk",
                    parentEmail = "sk@example.com",
                    parentPhone = "+91 98765 43210",
                    activeClass = "Class 11 - Batch A",
                    phone = "injamam@example.com",
                    password = "password123"
                )
            ).toInt()

            val s2 = userDao.insertUser(
                User(
                    name = "Imran Mondal",
                    role = "STUDENT",
                    avatarUrl = null,
                    parentName = "Mr. Mondal",
                    parentEmail = "imran.m@example.com",
                    parentPhone = "+91 94567 12345",
                    activeClass = "Class 11 - Batch A",
                    phone = "imran@example.com",
                    password = "password123"
                )
            ).toInt()

            val s3 = userDao.insertUser(
                User(
                    name = "Momin mondal",
                    role = "STUDENT",
                    avatarUrl = null,
                    parentName = "Mr. Mondal",
                    parentEmail = "momin.m@example.com",
                    parentPhone = "+91 91234 56789",
                    activeClass = "Class 12 - Batch B",
                    phone = "momin@example.com",
                    password = "password123"
                )
            ).toInt()

            val s4 = userDao.insertUser(
                User(
                    name = "Atanu Paramanik",
                    role = "STUDENT",
                    avatarUrl = null,
                    parentName = "Mr. Paramanik",
                    parentEmail = "atanu.p@example.com",
                    parentPhone = "+91 98989 12345",
                    activeClass = "Class 12 - Batch B",
                    phone = "atanu@example.com",
                    password = "password123"
                )
            ).toInt()

            val s5 = userDao.insertUser(
                User(
                    name = "Ayan pradhan",
                    role = "STUDENT",
                    avatarUrl = null,
                    parentName = "Mr. Pradhan",
                    parentEmail = "ayan.pradhan@example.com",
                    parentPhone = "+91 99999 12345",
                    activeClass = "Class 11 - Batch A",
                    phone = "ayan@example.com",
                    password = "password123"
                )
            ).toInt()

            // Seed Assignments
            val currentDayStr = sdf.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, 3)
            val dayPlus3 = sdf.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, 4)
            val dayPlus7 = sdf.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, -7) // reset

            assignmentDao.insertAssignment(
                Assignment(
                    title = "Chemical Kinetics Practice Sheet",
                    description = "Submit problems 1-25 on rate laws, half-life equations, and Arrhenius activation energy.",
                    dueDate = dayPlus3,
                    maxMarks = 50.0,
                    chapterName = "Chemical Kinetics",
                    className = "Class 11 - Batch A"
                )
            )

            assignmentDao.insertAssignment(
                Assignment(
                    title = "Organic Chemistry - IUPAC & Mechanisms",
                    description = "Identify IUPAC names and show complete step-by-step nucleophilic addition mechanisms.",
                    dueDate = dayPlus7,
                    maxMarks = 100.0,
                    chapterName = "Organic Chemistry",
                    className = "Class 12 - Batch B"
                )
            )

            // Seed Attendance
            val students = listOf(s1, s2, s3, s4, s5)
            for (offset in -4..0) {
                calendar.add(Calendar.DAY_OF_YEAR, offset)
                val dateStr = sdf.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, -offset) // reset calendar

                for (student in students) {
                    val p = when (student) {
                        s1 -> offset != -3  // Absent on offset -3
                        s2 -> true          // Always present
                        s3 -> offset != -1  // Absent on offset -1
                        s4 -> offset != -2  // Absent on offset -2
                        else -> true
                    }
                    attendanceDao.insertAttendance(
                        Attendance(
                            studentId = student,
                            date = dateStr,
                            isPresent = p,
                            comments = if (!p) "Informed of headache" else "Attentive in stoichiometry discussion"
                        )
                    )
                }
            }

            // Seed Grades
            gradeDao.insertGrade(
                Grade(
                    studentId = s1,
                    examName = "Atomic Structure Quiz",
                    chapterName = "Atomic Structure",
                    marksObtained = 44.0,
                    maxMarks = 50.0,
                    date = currentDayStr,
                    teacherRemarks = "Excellent conceptual understanding of Bohr model and quantum numbers."
                )
            )
            gradeDao.insertGrade(
                Grade(
                    studentId = s1,
                    examName = "Stoichiometry Monthly Test",
                    chapterName = "Basic Concepts of Chemistry",
                    marksObtained = 78.5,
                    maxMarks = 100.0,
                    date = currentDayStr,
                    teacherRemarks = "Good effort, but review the limiting reagent calculation section."
                )
            )

            gradeDao.insertGrade(
                Grade(
                    studentId = s2,
                    examName = "Atomic Structure Quiz",
                    chapterName = "Atomic Structure",
                    marksObtained = 48.0,
                    maxMarks = 50.0,
                    date = currentDayStr,
                    teacherRemarks = "Top-tier student. Keep writing detailed answers!"
                )
            )
            gradeDao.insertGrade(
                Grade(
                    studentId = s2,
                    examName = "Stoichiometry Monthly Test",
                    chapterName = "Basic Concepts of Chemistry",
                    marksObtained = 95.0,
                    maxMarks = 100.0,
                    date = currentDayStr,
                    teacherRemarks = "Stellar performance."
                )
            )

            gradeDao.insertGrade(
                Grade(
                    studentId = s3,
                    examName = "Organic Reactions Mini-Exam",
                    chapterName = "Organic Chemistry",
                    marksObtained = 82.0,
                    maxMarks = 100.0,
                    date = currentDayStr,
                    teacherRemarks = "Great mechanism accuracy. Minor errors in naming compounds."
                )
            )

            gradeDao.insertGrade(
                Grade(
                    studentId = s4,
                    examName = "Organic Reactions Mini-Exam",
                    chapterName = "Organic Chemistry",
                    marksObtained = 91.0,
                    maxMarks = 100.0,
                    date = currentDayStr,
                    teacherRemarks = "Flawless synthesis diagrams!"
                )
            )

            // Seed Resources
            resourceDao.insertResource(
                LearningResource(
                    title = "Hydrogen Emission Spectra & Rydberg Equation",
                    fileType = "PDF",
                    url = "https://example.com/chem/hydrogen_spectrum.pdf",
                    fileSize = "1.8 MB",
                    className = "Class 11 - Batch A",
                    chapterName = "Atomic Structure",
                    authorName = "Prof. Ayan",
                    dateAdded = currentDayStr
                )
            )
            resourceDao.insertResource(
                LearningResource(
                    title = "Sn1 vs Sn2 Substitution Mechanisms Handout",
                    fileType = "PDF",
                    url = "https://example.com/chem/sn1_vs_sn2.pdf",
                    fileSize = "3.2 MB",
                    className = "Class 12 - Batch B",
                    chapterName = "Organic Chemistry",
                    authorName = "Prof. Ayan",
                    dateAdded = currentDayStr
                )
            )
            resourceDao.insertResource(
                LearningResource(
                    title = "Thermodynamics Laws & Standard Enthalpy Formula Sheet",
                    fileType = "DOCX",
                    url = "https://example.com/chem/thermo_formulas.docx",
                    fileSize = "512 KB",
                    className = "Class 11 - Batch A",
                    chapterName = "Chemical Kinetics",
                    authorName = "Prof. Ayan",
                    dateAdded = currentDayStr
                )
            )
            resourceDao.insertResource(
                LearningResource(
                    title = "Interactive Coordination Chemistry Slideshow",
                    fileType = "PPTX",
                    url = "https://example.com/chem/coordination_compounds.pptx",
                    fileSize = "12.4 MB",
                    className = "Class 12 - Batch B",
                    chapterName = "Coordination Compounds",
                    authorName = "Prof. Ayan",
                    dateAdded = currentDayStr
                )
            )

            // Seed Initial Welcome Broadcast & a Conversation
            messageDao.insertMessage(
                Message(
                    senderId = teacherId,
                    senderName = "Prof. Ayan",
                    senderRole = "TEACHER",
                    receiverId = null, // Broadcast
                    title = "Welcome to Ayan Coaching Chemistry Lab!",
                    content = "Hello students and parents! I am thrilled to launch our new interactive portal. All attendance updates, test scores, supplementary resource PDFs, and homework assignments will be shared directly here. Let's make Chemistry simple and fascinating!",
                    isBroadcast = true
                )
            )

            messageDao.insertMessage(
                Message(
                    senderId = teacherId,
                    senderName = "Prof. Ayan",
                    senderRole = "TEACHER",
                    receiverId = s1, // Direct message to Rahul
                    title = "Upcoming Chemistry Olympiad Guidance",
                    content = "Hey Rahul, I noticed your amazing talent in Atomic Structure. I highly recommend you register for the National Chemistry Olympiad. I've uploaded specialized study materials for you.",
                    isBroadcast = false
                )
            )

            messageDao.insertMessage(
                Message(
                    senderId = s1,
                    senderName = "Rahul Sharma",
                    senderRole = "STUDENT",
                    receiverId = teacherId, // Reply to Prof. Ayan
                    title = "Re: Olympiad Guidance",
                    content = "Thank you so much Prof. Ayan! I would love to register. I will download the resources and start practicing tonight.",
                    isBroadcast = false
                )
            )
        }
    }
}
