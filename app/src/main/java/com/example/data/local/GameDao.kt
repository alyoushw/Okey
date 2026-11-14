package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: GameRecordEntity): Long

    @Query("SELECT * FROM game_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<GameRecordEntity>>

    @Query("DELETE FROM game_records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM game_records")
    suspend fun clearAllRecords()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveGame(activeGame: ActiveGameEntity)

    @Query("SELECT * FROM active_game WHERE id = 1 LIMIT 1")
    suspend fun getActiveGame(): ActiveGameEntity?

    @Query("DELETE FROM active_game WHERE id = 1")
    suspend fun clearActiveGame()
}
