package com.swooby.alfred.data

import androidx.room.*
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

    @Query("""
        SELECT * FROM events
        WHERE userId = :userId AND tsStart BETWEEN :fromTs AND :toTs
        ORDER BY tsStart DESC
        LIMIT :limit
    """)
    suspend fun listByTime(
        userId: String,
        fromTs: Instant,
        toTs: Instant,
        limit: Int = 500
    ): List<EventEntity>
}