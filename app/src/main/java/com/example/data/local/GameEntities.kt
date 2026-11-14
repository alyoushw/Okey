package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_records")
data class GameRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val scoringRule: String,
    val roundsCount: Int,
    val winnerName: String,
    val winnerScore: Int,
    val resultsJson: String
)

@Entity(tableName = "active_game")
data class ActiveGameEntity(
    @PrimaryKey val id: Int = 1,
    val gameStateJson: String
)
