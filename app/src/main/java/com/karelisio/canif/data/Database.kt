package com.karelisio.canif.data

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
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val toolId: String, val position: Int)

@Entity(tableName = "recents")
data class RecentEntity(@PrimaryKey val toolId: String, val usedAt: Long)

/** Named presets for any tool (metronome, timers, intervals…). [data] is a small JSON document. */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val data: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Last known exchange rates for a base currency (offline cache). */
@Entity(tableName = "rates")
data class RateCacheEntity(
    @PrimaryKey val base: String,
    val date: String,
    val ratesJson: String,
    val fetchedAt: Long,
)

/** A countdown timer. When running, [endAt] is the wall-clock end; when paused, [remainingMs] holds the rest. */
@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationMs: Long,
    val remainingMs: Long,
    val endAt: Long = 0,
    val running: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY position")
    fun observe(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY position")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM favorites")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE toolId = :id")
    suspend fun delete(id: String)

    @Upsert
    suspend fun upsertAll(list: List<FavoriteEntity>)

    @Query("DELETE FROM favorites")
    suspend fun clear()

    @Transaction
    suspend fun reorder(ids: List<String>) {
        clear()
        upsertAll(ids.mapIndexed { i, id -> FavoriteEntity(id, i) })
    }
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recents ORDER BY usedAt DESC LIMIT :limit")
    fun observe(limit: Int = 10): Flow<List<RecentEntity>>

    @Upsert
    suspend fun touch(r: RecentEntity)

    @Query("DELETE FROM recents WHERE toolId NOT IN (SELECT toolId FROM recents ORDER BY usedAt DESC LIMIT 20)")
    suspend fun trim()
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets WHERE kind = :kind ORDER BY createdAt")
    fun observe(kind: String): Flow<List<PresetEntity>>

    @Insert
    suspend fun insert(p: PresetEntity): Long

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates WHERE base = :base")
    suspend fun get(base: String): RateCacheEntity?

    @Query("SELECT * FROM rates ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun latest(): RateCacheEntity?

    @Upsert
    suspend fun put(r: RateCacheEntity)
}

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY createdAt")
    fun observe(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers ORDER BY createdAt")
    suspend fun all(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun get(id: Long): TimerEntity?

    @Insert
    suspend fun insert(t: TimerEntity): Long

    @Upsert
    suspend fun upsert(t: TimerEntity)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [FavoriteEntity::class, RecentEntity::class, PresetEntity::class, RateCacheEntity::class, TimerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CanifDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun recents(): RecentDao
    abstract fun presets(): PresetDao
    abstract fun rates(): RateDao
    abstract fun timers(): TimerDao

    companion object {
        fun build(context: Context): CanifDatabase =
            Room.databaseBuilder(context, CanifDatabase::class.java, "canif.db").build()
    }
}
