package fi.nikosavola.immichwear.ui.timeline

import fi.nikosavola.immichwear.data.ImmichResult
import fi.nikosavola.immichwear.data.TimelinePage
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.AssetTypeEnum
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private fun asset(id: String) =
  AssetDto(
    id = id,
    type = AssetTypeEnum.IMAGE,
    originalFileName = "$id.jpg",
    localDateTime = "2026-01-01T00:00:00Z",
  )

class PagedAssetsTest {
  @Test
  fun `a page overlapping the previous one does not produce duplicate items`() = runTest {
    // A page fetched by number over a live, newest-first list can re-return items already seen
    // if new assets landed on the server in between - see loadPagedAssets' kdoc.
    val existing = listOf(asset("a1"), asset("a2"), asset("a3"))
    val overlappingPage = TimelinePage(items = listOf(asset("a3"), asset("a4")), nextPage = null)

    val state =
      loadPagedAssets(page = 2, existing = existing) { ImmichResult.Success(overlappingPage) }

    val loaded = state as TimelineUiState.Loaded
    assertEquals(listOf("a1", "a2", "a3", "a4"), loaded.items.map { it.id })
  }

  @Test
  fun `a successful page appends its items and carries the new nextPage`() = runTest {
    val page = TimelinePage(items = listOf(asset("a2")), nextPage = 3)

    val state =
      loadPagedAssets(page = 2, existing = listOf(asset("a1"))) { ImmichResult.Success(page) }

    val loaded = state as TimelineUiState.Loaded
    assertEquals(listOf("a1", "a2"), loaded.items.map { it.id })
    assertEquals(3, loaded.nextPage)
  }

  @Test
  fun `a result served from the offline cache carries isFromCache through to the UI state`() =
    runTest {
      val page = TimelinePage(items = listOf(asset("a1")), nextPage = null)

      val state =
        loadPagedAssets(page = null, existing = emptyList()) {
          ImmichResult.Success(page, fromCache = true)
        }

      assertEquals(true, (state as TimelineUiState.Loaded).isFromCache)
    }
}
