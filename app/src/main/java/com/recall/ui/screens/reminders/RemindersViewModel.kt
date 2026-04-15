package com.recall.ui.screens.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recall.network.RecallApi
import com.recall.network.ReminderDto
import com.recall.reminders.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val api = RecallApi(application)
    private val _state = MutableStateFlow(RemindersState())
    val state: StateFlow<RemindersState> = _state.asStateFlow()

    init {
        loadReminders()
    }

    fun selectTab(tab: ReminderTab) {
        _state.update { it.copy(selectedTab = tab) }
        loadReminders()
    }

    fun loadReminders() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val active = runCatching { api.listReminders("active") }.getOrDefault(emptyList())
                val recurring = runCatching { api.listReminders("recurring") }.getOrDefault(emptyList())
                val history = runCatching { api.listReminders("history") }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        activeList = active,
                        recurringList = recurring,
                        historyList = history,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            runCatching {
                api.deleteReminder(id)
                ReminderScheduler.cancel(getApplication(), id)
            }
            loadReminders()
        }
    }

    fun pauseReminder(id: String) {
        viewModelScope.launch {
            runCatching {
                api.pauseReminder(id)
                ReminderScheduler.cancel(getApplication(), id)
            }
            loadReminders()
        }
    }

    fun resumeReminder(id: String) {
        viewModelScope.launch {
            runCatching {
                val resumed = api.resumeReminder(id)
                if (resumed != null && resumed.trigger?.type == "time") {
                    ReminderScheduler.schedule(getApplication(), resumed)
                }
            }
            loadReminders()
        }
    }

    /** Cache a loaded reminder for the detail screen. */
    fun getReminderById(id: String): ReminderDto? {
        val s = _state.value
        return s.activeList.find { it.id == id }
            ?: s.recurringList.find { it.id == id }
            ?: s.historyList.find { it.id == id }
    }
}

enum class ReminderTab { Active, Recurring, History }

data class RemindersState(
    val activeList: List<ReminderDto> = emptyList(),
    val recurringList: List<ReminderDto> = emptyList(),
    val historyList: List<ReminderDto> = emptyList(),
    val selectedTab: ReminderTab = ReminderTab.Active,
    val isLoading: Boolean = true,
    val error: String? = null,
)
