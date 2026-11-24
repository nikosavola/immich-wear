package fi.nikosavola.immichwear.ui.detail

import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import fi.nikosavola.immichwear.data.api.AssetThumbnailSize
import fi.nikosavola.immichwear.data.api.thumbnailUrl

// Warming the disk cache needs no real decode, so keep it tiny.
private const val PREFETCH_DECODE_PX = 32

// The grid's AsyncImage has no transformations or custom cache key, so Coil keys its memory
// cache entry by the URL alone. Size only enters the key when transformations are present.
internal fun thumbnailMemoryCacheKey(assetId: String): MemoryCache.Key =
  MemoryCache.Key(thumbnailUrl(assetId, AssetThumbnailSize.THUMBNAIL))

internal fun previewRequest(context: PlatformContext, assetId: String): ImageRequest =
  ImageRequest.Builder(context)
    .data(thumbnailUrl(assetId, AssetThumbnailSize.PREVIEW))
    .placeholderMemoryCacheKey(thumbnailMemoryCacheKey(assetId))
    .build()

// Disk cache only: skipping the memory cache avoids pinning a decoded 1440px bitmap for a page
// that may never be opened.
internal fun prefetchPreviewRequest(context: PlatformContext, assetId: String): ImageRequest =
  ImageRequest.Builder(context)
    .data(thumbnailUrl(assetId, AssetThumbnailSize.PREVIEW))
    .memoryCachePolicy(CachePolicy.DISABLED)
    .size(PREFETCH_DECODE_PX)
    .build()
