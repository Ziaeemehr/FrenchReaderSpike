package com.ziaee.frenchreader.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SourceMinutes(
    @ColumnInfo(name = "source_id") val sourceId: Long?,
    val minutes: Int
)

@Dao
interface StudyLogDao {
    @Query("SELECT MIN(date) FROM study_session")
    fun observeFirstSessionDate(): Flow<Long?>

    @Query("SELECT * FROM study_session WHERE date BETWEEN :start AND :end ORDER BY date DESC, created_at DESC")
    fun observeSessions(start: Long, end: Long): Flow<List<StudySession>>

    @Query("SELECT * FROM study_session ORDER BY date DESC, created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<StudySession>>

    @Query("SELECT * FROM study_source ORDER BY archived, name COLLATE NOCASE")
    fun observeSources(): Flow<List<StudySource>>

    @Query("SELECT * FROM study_session WHERE id = :id")
    suspend fun getSession(id: Long): StudySession?

    @Query("SELECT * FROM study_source WHERE id = :id")
    suspend fun getSource(id: Long): StudySource?

    @Insert
    suspend fun insertSession(session: StudySession): Long

    @Update
    suspend fun updateSession(session: StudySession)

    @Delete
    suspend fun deleteSession(session: StudySession)

    @Insert
    suspend fun insertSource(source: StudySource): Long

    @Update
    suspend fun updateSource(source: StudySource)

    @Delete
    suspend fun deleteSource(source: StudySource)

    @Query("SELECT COUNT(*) FROM study_session WHERE source_id = :sourceId")
    suspend fun countSessionsForSource(sourceId: Long): Int

    @Query(
        "SELECT source_id, COALESCE(SUM(duration_min), 0) AS minutes " +
            "FROM study_session WHERE date BETWEEN :start AND :end GROUP BY source_id"
    )
    fun observeSourceTotals(start: Long, end: Long): Flow<List<SourceMinutes>>

    @Query("SELECT source_id, COALESCE(SUM(duration_min), 0) AS minutes FROM study_session GROUP BY source_id")
    fun observeAllSourceTotals(): Flow<List<SourceMinutes>>

    @Query("SELECT COALESCE(SUM(duration_min), 0) FROM study_session WHERE date = :date AND id != :excludeId")
    suspend fun sumMinutesForDateExcluding(date: Long, excludeId: Long = -1): Int
}
