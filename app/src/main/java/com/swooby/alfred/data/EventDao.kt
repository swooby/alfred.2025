package com.swooby.alfred.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(events: List<EventEntity>)

    @Query(
        """
        DELETE FROM events
        WHERE userId = :userId AND eventId IN (:eventIds)
        """
    )
    suspend fun deleteByIds(userId: String, eventIds: List<String>)

    @Query(
        """
        DELETE FROM events
        WHERE userId = :userId
        """
    )
    suspend fun clearAllForUser(userId: String)

    @Query(
        """
        SELECT * FROM events
        WHERE userId = :userId
          AND COALESCE(ingestAt, tsStart) BETWEEN :fromTs AND :toTs
        ORDER BY COALESCE(ingestAt, tsStart) DESC
        LIMIT :limit
        """
    )
    suspend fun listByTime(
        userId: String,
        fromTs: Instant,
        toTs: Instant,
        limit: Int = 500
    ): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE userId = :userId
          AND COALESCE(ingestAt, tsStart) >= :fromTs
        ORDER BY COALESCE(ingestAt, tsStart) DESC
        LIMIT :limit
        """
    )
    fun observeRecent(
        userId: String,
        fromTs: Instant,
        limit: Int = 500
    ): Flow<List<EventEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM events
        WHERE userId = :userId
        """
    )
    fun observeCount(userId: String): Flow<Long>
}
