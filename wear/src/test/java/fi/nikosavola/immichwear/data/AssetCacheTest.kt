package fi.nikosavola.immichwear.data

import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.AssetTypeEnum
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun asset(id: String) =
  AssetDto(
    id = id,
    type = AssetTypeEnum.IMAGE,
    originalFileName = "$id.jpg",
    localDateTime = "2026-01-01T00:00:00Z",
  )

class AssetCacheTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private fun cache() = FileAssetCache(tempFolder.newFolder())

  @Test
  fun `load returns null when nothing was ever saved`() = runTest {
    assertNull(cache().load("timeline"))
  }

  @Test
  fun `save then load round-trips the items`() = runTest {
    val cache = cache()
    val items = listOf(asset("a1"), asset("a2"))

    cache.save("timeline", items)

    assertEquals(items, cache.load("timeline"))
  }

  @Test
  fun `different keys don't collide`() = runTest {
    val cache = cache()
    cache.save("timeline", listOf(asset("a1")))
    cache.save("favorites", listOf(asset("a2")))

    assertEquals(listOf(asset("a1")), cache.load("timeline"))
    assertEquals(listOf(asset("a2")), cache.load("favorites"))
  }

  @Test
  fun `save overwrites a previous value for the same key`() = runTest {
    val cache = cache()
    cache.save("timeline", listOf(asset("a1")))

    cache.save("timeline", listOf(asset("a2")))

    assertEquals(listOf(asset("a2")), cache.load("timeline"))
  }

  @Test
  fun `clear removes every cached key`() = runTest {
    val cache = cache()
    cache.save("timeline", listOf(asset("a1")))
    cache.save("favorites", listOf(asset("a2")))

    cache.clear()

    assertNull(cache.load("timeline"))
    assertNull(cache.load("favorites"))
  }

  @Test fun `clear on an empty cache does not throw`() = runTest { cache().clear() }

  @Test
  fun `remove clears only the given key`() = runTest {
    val cache = cache()
    cache.save("timeline", listOf(asset("a1")))
    cache.save("favorites", listOf(asset("a2")))

    cache.remove("favorites")

    assertEquals(listOf(asset("a1")), cache.load("timeline"))
    assertNull(cache.load("favorites"))
  }

  @Test
  fun `remove on a key that was never cached does not throw`() = runTest {
    cache().remove("timeline")
  }

  @Test
  fun `a corrupt cache file is treated as nothing cached, not a crash`() = runTest {
    val dir = tempFolder.newFolder()
    File(dir, "timeline.json").writeText("not valid json")

    assertNull(FileAssetCache(dir).load("timeline"))
  }

  @Test
  fun `NoOpAssetCache never stores anything`() = runTest {
    NoOpAssetCache.save("timeline", listOf(asset("a1")))

    assertTrue(NoOpAssetCache.load("timeline") == null)
  }

  @Test
  fun `a key containing path separators is rejected rather than escaping cacheDir`() = runTest {
    val cacheDir = tempFolder.newFolder()
    // A distinct marker per run, so a stray pre-existing file elsewhere in the temp tree can't
    // make this pass by accident.
    val marker = "escape-${UUID.randomUUID()}"
    var threw = false

    try {
      FileAssetCache(cacheDir).save("../../$marker", listOf(asset("a1")))
    } catch (e: IllegalArgumentException) {
      threw = true
    }

    assertTrue("expected save() to reject the key", threw)
    // The original bug wrote a temp file from the raw, unvalidated key before file(key) ever ran
    // its validation - so a thrown exception alone doesn't prove nothing escaped cacheDir.
    assertTrue(
      "no file should have been written outside cacheDir",
      cacheDir.parentFile?.parentFile?.listFiles().orEmpty().none { it.name.contains(marker) },
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun `an empty key is rejected`() = runTest { cache().load("") }
}
