package fi.nikosavola.immichwear.data.api.dto

import fi.nikosavola.immichwear.data.api.immichJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-format coverage for the DTOs this client publishes. They used to be reached only indirectly,
 * through ImmichRepository's tests in :wear; now that they are a standalone artifact, the contract
 * a server has to satisfy is worth pinning directly. The JSON below is shaped like real Immich
 * responses, trimmed to the fields this client reads.
 */
class ImmichDtoSerializationTest {
  @Test
  fun `asset decodes every field this client reads, ignoring the ones it does not`() {
    val asset =
      immichJson.decodeFromString(
        AssetDto.serializer(),
        """
        {
          "id": "0f4d3c1e-8f2b-4a6d-9c1e-2b3a4c5d6e7f",
          "type": "IMAGE",
          "originalFileName": "IMG_1234.HEIC",
          "isFavorite": true,
          "localDateTime": "2024-06-01T12:34:56.000Z",
          "duration": null,
          "thumbhash": "1QcSHQRnh493V4dIh4eXh1h4kJUI",
          "ownerId": "server-side field this client never reads",
          "exifInfo": {
            "make": "Apple",
            "model": "iPhone 15 Pro",
            "lensModel": "iPhone 15 Pro back triple camera 6.86mm f/1.78",
            "exifImageWidth": 4032,
            "exifImageHeight": 3024,
            "city": "Helsinki",
            "country": "Finland",
            "fileSizeInByte": 2793214,
            "fNumber": 1.78,
            "exposureTime": "1/250",
            "iso": 64,
            "focalLength": 6.86,
            "futureServerField": "ignored"
          },
          "people": [{ "name": "ignored" }]
        }
        """
          .trimIndent(),
      )

    assertEquals("0f4d3c1e-8f2b-4a6d-9c1e-2b3a4c5d6e7f", asset.id)
    assertEquals(AssetTypeEnum.IMAGE, asset.type)
    assertEquals("IMG_1234.HEIC", asset.originalFileName)
    assertTrue(asset.isFavorite)
    assertEquals("2024-06-01T12:34:56.000Z", asset.localDateTime)
    assertEquals("1QcSHQRnh493V4dIh4eXh1h4kJUI", asset.thumbhash)
    assertNull(asset.duration)

    val exif = requireNotNull(asset.exifInfo)
    assertEquals("Apple", exif.make)
    assertEquals("iPhone 15 Pro", exif.model)
    assertEquals("iPhone 15 Pro back triple camera 6.86mm f/1.78", exif.lensModel)
    assertEquals(4032, exif.exifImageWidth!!)
    assertEquals(3024, exif.exifImageHeight!!)
    assertEquals("Helsinki", exif.city)
    assertEquals("Finland", exif.country)
    assertEquals(2_793_214L, exif.fileSizeInByte!!)
    assertEquals(1.78, exif.fNumber!!, 0.0)
    assertEquals("1/250", exif.exposureTime)
    assertEquals(64, exif.iso!!)
    assertEquals(6.86, exif.focalLength!!, 0.0)
  }

  @Test
  fun `video asset keeps its duration and needs no exif block`() {
    val asset =
      immichJson.decodeFromString(
        AssetDto.serializer(),
        """
        {
          "id": "vid-1",
          "type": "VIDEO",
          "originalFileName": "clip.mp4",
          "localDateTime": "2024-06-02T08:00:00.000Z",
          "duration": 12000
        }
        """
          .trimIndent(),
      )

    assertEquals(AssetTypeEnum.VIDEO, asset.type)
    assertEquals(12_000, asset.duration!!)
    // Defaults, not decode failures, when the server omits these.
    assertFalse(asset.isFavorite)
    assertNull(asset.thumbhash)
    assertNull(asset.exifInfo)
  }

  @Test
  fun `album decodes its thumbnail id and defaults it to null when absent`() {
    val album =
      immichJson.decodeFromString(
        AlbumDto.serializer(),
        """
        {
          "id": "album-1",
          "albumName": "Iceland 2024",
          "assetCount": 42,
          "albumThumbnailAssetId": "asset-9",
          "shared": true
        }
        """
          .trimIndent(),
      )
    assertEquals("album-1", album.id)
    assertEquals("Iceland 2024", album.albumName)
    assertEquals(42, album.assetCount)
    assertEquals("asset-9", album.albumThumbnailAssetId)

    val blank =
      immichJson.decodeFromString(
        AlbumDto.serializer(),
        """{"id": "album-2", "albumName": "Empty", "assetCount": 0}""",
      )
    assertNull(blank.albumThumbnailAssetId)
  }

  @Test
  fun `memories carry the year and the assets for that day`() {
    val memories =
      immichJson.decodeFromString(
        ListSerializer(MemoryDto.serializer()),
        """
        [
          {
            "id": "memory-1",
            "type": "on_this_day",
            "data": { "year": 2021 },
            "assets": [
              {
                "id": "asset-1",
                "type": "IMAGE",
                "originalFileName": "old.jpg",
                "localDateTime": "2021-06-01T10:00:00.000Z"
              }
            ]
          }
        ]
        """
          .trimIndent(),
      )

    assertEquals(1, memories.size)
    assertEquals(2021, memories.single().data.year)
    assertEquals("asset-1", memories.single().assets.single().id)
  }

  @Test
  fun `metadata search response decodes a page with and without a next page`() {
    val page =
      immichJson.decodeFromString(
        SearchMetadataResponse.serializer(),
        """
        {
          "assets": {
            "total": 137,
            "count": 1,
            "items": [
              {
                "id": "asset-1",
                "type": "IMAGE",
                "originalFileName": "a.jpg",
                "localDateTime": "2024-01-01T00:00:00.000Z"
              }
            ],
            "nextPage": "2"
          }
        }
        """
          .trimIndent(),
      )
    assertEquals("asset-1", page.assets.items.single().id)
    assertEquals("2", page.assets.nextPage)

    val lastPage =
      immichJson.decodeFromString(
        SearchMetadataResponse.serializer(),
        """{"assets": {"items": [], "nextPage": null}}""",
      )
    assertTrue(lastPage.assets.items.isEmpty())
    assertNull(lastPage.assets.nextPage)
  }

  @Test
  fun `metadata search request drops null filters and keeps the explicit order default`() {
    val encoded =
      immichJson.encodeToString(
        MetadataSearchRequest.serializer(),
        MetadataSearchRequest(size = 100, albumIds = listOf("album-1"), page = 3),
      )
    val fields = immichJson.parseToJsonElement(encoded).jsonObject

    assertEquals(100, fields.getValue("size").jsonPrimitive.int)
    assertEquals(3, fields.getValue("page").jsonPrimitive.int)
    // encodeDefaults: "desc" is the DTO's own default but must still reach the server, otherwise
    // pagination ordering silently falls back to the server's undocumented one (see JsonConfig).
    assertEquals("desc", fields.getValue("order").jsonPrimitive.content)
    assertTrue("albumIds" in fields)
    // explicitNulls = false: a null filter has to be omitted, not sent as JSON null.
    assertFalse("isFavorite" in fields)
  }

  @Test
  fun `simple response dtos decode`() {
    val ping = immichJson.decodeFromString(ServerPingResponse.serializer(), """{"res": "pong"}""")
    assertEquals("pong", ping.res)

    val stats =
      immichJson.decodeFromString(
        AssetStatsResponseDto.serializer(),
        """{"total": 1204, "images": 1100, "videos": 104}""",
      )
    assertEquals(1204, stats.total)
    assertEquals(1100, stats.images)
    assertEquals(104, stats.videos)

    val user =
      immichJson.decodeFromString(
        UserDto.serializer(),
        """{"id": "user-1", "email": "niko@example.com", "name": "Niko"}""",
      )
    assertEquals("user-1", user.id)
    assertEquals("niko@example.com", user.email)
    assertEquals("Niko", user.name)

    val bareUser = immichJson.decodeFromString(UserDto.serializer(), """{"id": "user-2"}""")
    assertNull(bareUser.email)
    assertNull(bareUser.name)
  }

  @Test
  fun `favorite update request round-trips as the server expects`() {
    val encoded =
      immichJson.encodeToString(
        UpdateAssetRequest.serializer(),
        UpdateAssetRequest(isFavorite = true),
      )
    assertEquals("""{"isFavorite":true}""", encoded)

    val decoded =
      immichJson.decodeFromString(UpdateAssetRequest.serializer(), """{"isFavorite": false}""")
    assertFalse(decoded.isFavorite)
  }
}
