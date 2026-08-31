package fi.nikosavola.immichwear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fi.nikosavola.immichwear.data.ImmichRepository
import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.SettingsStore
import fi.nikosavola.immichwear.data.api.dto.MemoryDto
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Derived from [SettingsStore.settings] (not a one-shot read) so returning to Home after connecting
 * or signing out in Settings reflects the change immediately, with no manual refresh. Once
 * connected, also fetches the most recent photo and today's memories for Home's preview cards -
 * collectLatest cancels both fetches if settings change again before they complete.
 */
class HomeViewModel(
  private val settingsStore: SettingsStore,
  private val repository: ImmichRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
  val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

  init {
    viewModelScope.launch {
      settingsStore.settings.collectLatest { settings ->
        if (settings.serverUrl != null && settings.apiKey != null) {
          mutableUiState.value = HomeUiState.Connected()
          mutableUiState.value =
            HomeUiState.Connected(heroAssetId = fetchHeroAssetId(), memory = fetchMemoryPreview())
        } else {
          mutableUiState.value = HomeUiState.NotConnected
        }
      }
    }
  }

  private suspend fun fetchHeroAssetId(): String? =
    when (val result = repository.timeline()) {
      is ImmichResult.Success -> result.value.items.firstOrNull()?.id
      is ImmichResult.Failure -> null
    }

  private suspend fun fetchMemoryPreview(): HomeMemoryPreview? =
    when (val result = repository.memories()) {
      is ImmichResult.Success -> toMemoryPreview(result.value)
      is ImmichResult.Failure -> null
    }

  private fun toMemoryPreview(memories: List<MemoryDto>): HomeMemoryPreview? {
    val memory = memories.firstOrNull { it.assets.isNotEmpty() }
    val asset = memory?.assets?.firstOrNull()
    val yearsAgo = memory?.let { LocalDate.now().year - it.data.year }
    return if (asset != null && yearsAgo != null && yearsAgo > 0) {
      HomeMemoryPreview(assetId = asset.id, yearsAgo = yearsAgo)
    } else {
      null
    }
  }
}
