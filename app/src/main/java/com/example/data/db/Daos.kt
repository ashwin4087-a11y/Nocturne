package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.JourneyEntity
import com.example.data.model.ObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyDao {
    @Query("SELECT * FROM journeys WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    fun getActiveJourneyFlow(): Flow<JourneyEntity?>

    @Query("SELECT * FROM journeys WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveJourneyDirect(): JourneyEntity?

    @Query("SELECT * FROM journeys ORDER BY id DESC")
    fun getAllJourneysFlow(): Flow<List<JourneyEntity>>

    @Query("SELECT * FROM journeys WHERE id = :id")
    suspend fun getJourneyById(id: Long): JourneyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJourney(journey: JourneyEntity): Long

    @Update
    suspend fun updateJourney(journey: JourneyEntity)

    @Query("UPDATE journeys SET status = 'CANCELLED' WHERE status = 'ACTIVE'")
    suspend fun cancelAllActiveJourneys()

    @Query("DELETE FROM journeys")
    suspend fun clearAllJourneys()
}

@Dao
interface ObservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: ObservationEntity)

    @Query("SELECT * FROM observations WHERE journeyId = :journeyId ORDER BY timestamp DESC LIMIT 30")
    suspend fun getRecentObservations(journeyId: Long): List<ObservationEntity>

    @Query("DELETE FROM observations WHERE journeyId = :journeyId")
    suspend fun deleteObservationsForJourney(journeyId: Long)

    @Query("DELETE FROM observations")
    suspend fun clearAllObservations()
}
