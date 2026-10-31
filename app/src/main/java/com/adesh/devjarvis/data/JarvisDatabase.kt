package com.adesh.devjarvis.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/** Ek yaad: "Sir ki mummy ka naam contacts mein 'Mummy Ji' hai". */
@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    /** person | preference | routine | project | other */
    val category: String,
    val created: Long,
    val lastUsed: Long,
)

/** Har baat-cheet ka ek line ka summary. */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val summary: String,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val atMillis: Long,
    val done: Boolean = false,
)

@Entity(tableName = "activity")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMillis: Long,
    val tool: String,
    val args: String,
    val ok: Boolean,
    val result: String,
)

@Dao
interface FactDao {
    @Query("SELECT * FROM facts ORDER BY lastUsed DESC")
    fun observeAll(): Flow<List<FactEntity>>

    @Query("SELECT * FROM facts")
    suspend fun all(): List<FactEntity>

    @Insert suspend fun insert(f: FactEntity): Long
    @Update suspend fun update(f: FactEntity)

    @Query("UPDATE facts SET lastUsed = :time WHERE id IN (:ids)")
    suspend fun touch(ids: List<Long>, time: Long)

    @Query("DELETE FROM facts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM facts")
    suspend fun deleteAll()
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<ConversationEntity>>

    @Insert suspend fun insert(c: ConversationEntity): Long

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE done = 0 ORDER BY atMillis")
    suspend fun pending(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE done = 0 AND atMillis BETWEEN :from AND :to ORDER BY atMillis")
    suspend fun between(from: Long, to: Long): List<ReminderEntity>

    @Insert suspend fun insert(r: ReminderEntity): Long

    @Query("UPDATE reminders SET done = 1 WHERE id = :id")
    suspend fun markDone(id: Long)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity ORDER BY timeMillis DESC LIMIT 300")
    fun observeRecent(): Flow<List<ActivityEntity>>

    @Insert suspend fun insert(a: ActivityEntity)

    @Query("DELETE FROM activity WHERE id NOT IN (SELECT id FROM activity ORDER BY timeMillis DESC LIMIT 300)")
    suspend fun trim()

    @Query("DELETE FROM activity")
    suspend fun deleteAll()
}

@Database(
    entities = [FactEntity::class, ConversationEntity::class, ReminderEntity::class, ActivityEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun facts(): FactDao
    abstract fun conversations(): ConversationDao
    abstract fun reminders(): ReminderDao
    abstract fun activity(): ActivityDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext context: Context): JarvisDatabase =
        Room.databaseBuilder(context, JarvisDatabase::class.java, "jarvis.db").build()

    @Provides fun facts(db: JarvisDatabase) = db.facts()
    @Provides fun conversations(db: JarvisDatabase) = db.conversations()
    @Provides fun reminders(db: JarvisDatabase) = db.reminders()
    @Provides fun activity(db: JarvisDatabase) = db.activity()
}
