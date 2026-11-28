package com.example.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_audio_files")
data class LocalAudioEntity(
    @PrimaryKey val recordingId: String,
    val userId: String,
    val fileName: String,
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val createdAtMs: Long,
    val syncedToCloud: Boolean
)

@Dao
interface LocalAudioDao {
    @Query("SELECT * FROM local_audio_files WHERE userId = :userId ORDER BY createdAtMs DESC")
    fun observeForUser(userId: String): Flow<List<LocalAudioEntity>>

    @Query("SELECT * FROM local_audio_files WHERE recordingId = :recordingId LIMIT 1")
    suspend fun getByRecordingId(recordingId: String): LocalAudioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalAudioEntity)

    @Query("UPDATE local_audio_files SET syncedToCloud = :synced WHERE recordingId = :recordingId")
    suspend fun markSynced(recordingId: String, synced: Boolean)

    @Query("DELETE FROM local_audio_files WHERE recordingId = :recordingId")
    suspend fun deleteByRecordingId(recordingId: String)

    @Query("DELETE FROM local_audio_files WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

@Database(entities = [LocalAudioEntity::class], version = 1, exportSchema = false)
abstract class LocalAudioDatabase : RoomDatabase() {
    abstract fun localAudioDao(): LocalAudioDao

    companion object {
        @Volatile
        private var INSTANCE: LocalAudioDatabase? = null

        fun getInstance(context: Context): LocalAudioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalAudioDatabase::class.java,
                    "memomind_local_audio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
