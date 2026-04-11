package com.ranti.ui.screens.nicknames

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ranti.network.NicknameDto
import com.ranti.network.PlaceOption
import com.ranti.network.RantiApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * SPEC §10 — ViewModel for the nicknames management screens.
 */
class NicknamesViewModel(application: Application) : AndroidViewModel(application) {

    private val api = RantiApi(application)

    private val _state = MutableStateFlow(NicknamesState())
    val state: StateFlow<NicknamesState> = _state.asStateFlow()

    init {
        loadNicknames()
    }

    fun loadNicknames() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { api.listNicknames() }
                .onSuccess { list ->
                    _state.update { it.copy(nicknames = list, isLoading = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun deleteNickname(nickname: NicknameDto) {
        viewModelScope.launch {
            runCatching { api.deleteNickname(nickname.nickname) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(nicknames = s.nicknames.filter { it.id != nickname.id })
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
        }
    }

    // ─── Edit/Create form state ─────────────────────────────────────────

    private val _formState = MutableStateFlow(NicknameFormState())
    val formState: StateFlow<NicknameFormState> = _formState.asStateFlow()

    fun initForm(existing: NicknameDto? = null) {
        _formState.value = if (existing != null) {
            NicknameFormState(
                nickname = existing.nickname,
                placeName = existing.place_name,
                placeId = existing.place_id,
                lat = existing.lat,
                lng = existing.lng,
                isEdit = true,
            )
        } else {
            NicknameFormState()
        }
    }

    fun onNicknameChange(value: String) {
        _formState.update { it.copy(nickname = value) }
    }

    fun onSearchQueryChange(value: String) {
        _formState.update { it.copy(searchQuery = value) }
    }

    fun searchPlaces() {
        val query = _formState.value.searchQuery.trim()
        if (query.isBlank()) return

        _formState.update { it.copy(isSearching = true, searchResults = emptyList()) }
        viewModelScope.launch {
            runCatching { api.resolvePlace(query) }
                .onSuccess { results ->
                    _formState.update { it.copy(searchResults = results, isSearching = false) }
                }
                .onFailure { e ->
                    _formState.update { it.copy(isSearching = false, error = e.message) }
                }
        }
    }

    fun selectPlace(option: PlaceOption) {
        _formState.update {
            it.copy(
                placeName = option.name,
                placeId = option.place_id,
                lat = option.lat,
                lng = option.lng,
                searchResults = emptyList(),
                searchQuery = option.name,
            )
        }
    }

    fun saveNickname(onSuccess: () -> Unit) {
        val form = _formState.value
        if (form.nickname.isBlank() || form.placeName.isBlank()) return

        _formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                api.saveNickname(
                    nickname = form.nickname.trim(),
                    placeName = form.placeName,
                    placeId = form.placeId,
                    lat = form.lat,
                    lng = form.lng,
                )
            }
                .onSuccess {
                    _formState.update { it.copy(isSaving = false) }
                    loadNicknames()
                    onSuccess()
                }
                .onFailure { e ->
                    _formState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }
}

data class NicknamesState(
    val nicknames: List<NicknameDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class NicknameFormState(
    val nickname: String = "",
    val placeName: String = "",
    val placeId: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val searchQuery: String = "",
    val searchResults: List<PlaceOption> = emptyList(),
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val isEdit: Boolean = false,
    val error: String? = null,
)
