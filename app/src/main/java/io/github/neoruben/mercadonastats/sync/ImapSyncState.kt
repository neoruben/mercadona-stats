package io.github.neoruben.mercadonastats.sync

import io.github.neoruben.mercadonastats.ui.SyncProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ImapSyncState {
    data object Idle : ImapSyncState
    data class Running(val progress: SyncProgress) : ImapSyncState
    data class Finished(val message: String?, val error: String?) : ImapSyncState
}

/** Estado de la sincronización IMAP, que corre en [ImapSyncService] y observa la pantalla. */
object ImapSyncBus {
    private val _state = MutableStateFlow<ImapSyncState>(ImapSyncState.Idle)
    val state: StateFlow<ImapSyncState> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value is ImapSyncState.Running

    fun update(state: ImapSyncState) {
        _state.value = state
    }

    /** La pantalla ya ha mostrado el resultado: vuelve a reposo. */
    fun consume(finished: ImapSyncState.Finished) {
        _state.compareAndSet(finished, ImapSyncState.Idle)
    }
}
