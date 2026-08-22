package com.rajnikant.moneybrain.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.rajnikant.moneybrain.capture.UndoEngine
import com.rajnikant.moneybrain.capture.UndoResult
import com.rajnikant.moneybrain.data.MoneyBrainDatabase
import com.rajnikant.moneybrain.data.RoomUndoStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ActivityViewModel(private val database: MoneyBrainDatabase) : ViewModel() {
    val actions = database.actionDao().observeAll()
    val unresolvedMessages = database.unparsedSmsDao().observeUnresolved()
    private val undoEngine = UndoEngine(RoomUndoStore(database))
    private val _undoResults = Channel<UndoResult>(Channel.BUFFERED)
    val undoResults = _undoResults.receiveAsFlow()

    fun dismissMessage(id: Long) {
        viewModelScope.launch { database.unparsedSmsDao().dismiss(id, System.currentTimeMillis()) }
    }

    fun undo(id: Long) {
        viewModelScope.launch {
            _undoResults.send(database.withTransaction { undoEngine.undo(id, System.currentTimeMillis()) })
        }
    }
}
