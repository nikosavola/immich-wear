package fi.nikosavola.immichwear.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import fi.nikosavola.immichwear.data.api.AssetThumbnailSize
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.AssetTypeEnum
import fi.nikosavola.immichwear.data.api.thumbnailUrl

private val THUMBNAIL_CORNER_RADIUS = 8.dp
private val VIDEO_BADGE_PADDING = 4.dp

/**
 * One square grid cell: the asset's thumbnail, cropped to fill, with a small play badge for videos.
 */
@Composable
fun AssetThumbnail(asset: AssetDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(THUMBNAIL_CORNER_RADIUS))
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(onClick = onClick)
  ) {
    AsyncImage(
      model = thumbnailUrl(asset.id, AssetThumbnailSize.THUMBNAIL),
      contentDescription = asset.originalFileName,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    if (asset.type == AssetTypeEnum.VIDEO) {
      VideoBadge(Modifier.align(Alignment.BottomEnd).padding(VIDEO_BADGE_PADDING))
    }
  }
}

@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
  Box(modifier = modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))) {
    Text(text = "▶", color = Color.White, modifier = Modifier.padding(horizontal = 3.dp))
  }
}
