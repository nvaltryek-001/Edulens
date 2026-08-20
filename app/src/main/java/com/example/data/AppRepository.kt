package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val appDao: AppDao) {
    val allStudents: Flow<List<Student>> = appDao.getAllStudents()
    val allAttendance: Flow<List<Attendance>> = appDao.getAllAttendance()
    val allLogs: Flow<List<LogEntry>> = appDao.getAllLogs()

    suspend fun insertStudent(student: Student) {
        appDao.insertStudent(student)
    }

    suspend fun insertAttendance(attendance: Attendance) {
        appDao.insertAttendance(attendance)
    }

    suspend fun insertLog(log: LogEntry) {
        appDao.insertLog(log)
    }
    
    suspend fun getUnsyncedAttendance(): List<Attendance> {
        return appDao.getUnsyncedAttendance()
    }
    
    suspend fun markAttendanceSynced(id: Int) {
        appDao.markAttendanceSynced(id)
    }
}
