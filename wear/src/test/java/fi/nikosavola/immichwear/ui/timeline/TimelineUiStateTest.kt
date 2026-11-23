package fi.nikosavola.immichwear.ui.timeline

import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.AssetTypeEnum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

private fun asset(id: String) =
  AssetDto(
    id = id,
    type = AssetTypeEnum.IMAGE,
    originalFileName = "$id.jpg",
    localDateTime = "2026-01-01T00:00:00Z",
  )

class TimelineUiStateTest {
  @Test
  fun `rows group items by grid column count and keep a short last row`() {
    val loaded = TimelineUiState.Loaded(items = (1..7).map { asset("a$it") }, nextPage = 2)

    assertEquals(
      listOf(listOf("a1", "a2", "a3"), listOf("a4", "a5", "a6"), listOf("a7")),
      loaded.rows.map { row -> row.map { it.id } },
    )
  }

  @Test
  fun `flipping isLoadingMore keeps the same row instances`() {
    val loaded = TimelineUiState.Loaded(items = (1..7).map { asset("a$it") }, nextPage = 2)

    val loading = loaded.copy(isLoadingMore = true)

    assertSame(loaded.rows, loading.rows)
    loaded.rows.indices.forEach { assertSame(loaded.rows[it], loading.rows[it]) }
  }
}
