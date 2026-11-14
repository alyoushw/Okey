package com.example.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.GameRecordEntity
import com.example.data.model.PlayerResult
import com.example.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerStat(
    val name: String,
    val wins: Int,
    val gamesPlayed: Int
)

data class HistoryUiState(
    val records: List<GameRecordEntity> = emptyList(),
    val totalGames: Int = 0,
    val topPlayer: PlayerStat? = null
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)

    val uiState: StateFlow<HistoryUiState> = repository.gameRecords
        .map { records ->
            val statsMap = mutableMapOf<String, Pair<Int, Int>>() // Name -> (wins, gamesPlayed)

            records.forEach { rec ->
                val results = repository.parseResultsJson(rec.resultsJson)
                results.forEach { res ->
                    val cur = statsMap.getOrDefault(res.name, 0 to 0)
                    val newWins = cur.first + (if (res.isWinner) 1 else 0)
                    val newGames = cur.second + 1
                    statsMap[res.name] = newWins to newGames
                }
            }

            val top = statsMap.maxByOrNull { it.value.first }?.let { (name, pair) ->
                PlayerStat(name, pair.first, pair.second)
            }

            HistoryUiState(
                records = records,
                totalGames = records.size,
                topPlayer = top
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteRecord(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllRecords()
        }
    }

    fun parseResults(json: String): List<PlayerResult> {
        return repository.parseResultsJson(json)
    }
}
