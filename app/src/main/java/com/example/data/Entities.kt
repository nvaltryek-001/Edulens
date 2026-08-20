package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,
    val studentId: String,
    val usn: String,
    val department: String,
    val semester: String,
    val section: String,
    val email: String,
    val phoneNumber: String
)

@Entity(tableName = "attendance")
data class Attendance(
    @PrimaryKey(autoGenerate = true)
    val attendanceId: Int = 0,

    val studentId: String,
    val date: String,
    val time: String,
    val status: String,
    val subject: String,
    val faculty: String,

    // A8: attendance belongs to an attendance session
    val sessionId: Int? = null,

    val syncStatus: Boolean = false
)

@Entity(tableName = "logs")
data class LogEntry(
    @PrimaryKey(autoGenerate = true)
    val logId: Int = 0,

    val event: String,

    val timestamp: Long = System.currentTimeMillis(),

    val syncStatus: Boolean = false
)