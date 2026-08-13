package fi.nikosavola.immichwear.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import fi.nikosavola.immichwear.R
import fi.nikosavola.immichwear.data.api.AssetThumbnailSize
import fi.nikosavola.immichwear.data.api.dto.AssetDto
import fi.nikosavola.immichwear.data.api.dto.AssetTypeEnum
import fi.nikosavola.immichwear.data.api.thumbnailUrl
import fi.nikosavola.immichwear.ui.ErrorContent

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private val SWIPE_THRESHOLD = 48.dp

@Composable
fun AssetDetailScreen(viewModel: AssetDetailViewModel, onNavigateToSettings: () -> Unit) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  ScreenScaffold(scrollState = rememberScrollState()) { contentPadding ->
    Box(modifier = Modifier.fillMaxSize()) {
      when (val state = uiState) {
        is AssetDetailUiState.Loading -> {
          Text(text = stringResource(R.string.loading), modifier = Modifier.align(Alignment.Center))
        }
        is AssetDetailUiState.Error -> {
          Column(modifier = Modifier.align(Alignment.Center)) {
            ErrorContent(
              error = state.error,
              onRetry = viewModel::load,
              onGoToSettings = onNavigateToSettings,
            )
          }
        }
        is AssetDetailUiState.Loaded -> {
          AssetDetailContent(
            state = state,
            contentPadding = contentPadding,
            onToggleFavorite = viewModel::toggleFavorite,
            onNext = viewModel::next,
            onPrevious = viewModel::previous,
          )
        }
      }
    }
  }
}

@Composable
private fun BoxScope.AssetDetailContent(
  state: AssetDetailUiState.Loaded,
  contentPadding: PaddingValues,
  onToggleFavorite: () -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
) {
  // Swiping left over the photo reveals this; swiping right within it (via SwipeToDismissBox)
  // dismisses it - a nested, in-screen dismiss distinct from the NavHost's own back-swipe.
  var showDetails by rememberSaveable(state.asset.id) { mutableStateOf(false) }

  if (showDetails) {
    SwipeToDismissBox(onDismissed = { showDetails = false }) { isBackground ->
      if (isBackground) {
        AssetPhoto(asset = state.asset)
      } else {
        AssetDetailsPanel(
          state = state,
          contentPadding = contentPadding,
          onToggleFavorite = onToggleFavorite,
        )
      }
    }
  } else {
    ZoomableAssetPhoto(
      asset = state.asset,
      onSwipeUp = { if (state.hasNext) onNext() },
      onSwipeDown = { if (state.hasPrevious) onPrevious() },
      onSwipeLeft = { showDetails = true },
    )
  }
}

@Composable
private fun BoxScope.ZoomableAssetPhoto(
  asset: AssetDto,
  onSwipeUp: () -> Unit,
  onSwipeDown: () -> Unit,
  onSwipeLeft: () -> Unit,
) {
  val zoomState = remember(asset.id) { mutableFloatStateOf(MIN_ZOOM) }
  val offsetState = remember(asset.id) { mutableStateOf(Offset.Zero) }
  val swipeThresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }

  AssetPhoto(
    asset = asset,
    modifier =
      Modifier.graphicsLayer(
          scaleX = zoomState.value,
          scaleY = zoomState.value,
          translationX = offsetState.value.x,
          translationY = offsetState.value.y,
        )
        .pointerInput(asset.id) {
          awaitEachGesture {
            val totalDrag = detectZoomPanOrSwipeDrag(zoomState, offsetState)
            if (zoomState.value <= MIN_ZOOM) {
              resolveSwipe(totalDrag, swipeThresholdPx, onSwipeUp, onSwipeDown, onSwipeLeft)
            }
          }
        },
  )
}

// Tracks a single gesture from first touch to all-fingers-up. While two fingers are down, or one
// finger is down after zoom was already above MIN_ZOOM, every movement is treated as zoom/pan and
// consumed on zoomState/offsetState directly. Otherwise movement is left unconsumed and
// accumulated into the returned drag total, so callers can resolve it as a swipe once zoom is
// back at MIN_ZOOM.
private suspend fun AwaitPointerEventScope.detectZoomPanOrSwipeDrag(
  zoomState: MutableFloatState,
  offsetState: MutableState<Offset>,
): Offset {
  awaitFirstDown(requireUnconsumed = false)
  var totalDrag = Offset.Zero
  var pressed: Boolean
  do {
    val event = awaitPointerEvent()
    val zoomChange = event.calculateZoom()
    val panChange = event.calculatePan()
    if (event.changes.count { it.pressed } > 1 || zoomState.value > MIN_ZOOM) {
      zoomState.value = (zoomState.value * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
      offsetState.value =
        if (zoomState.value <= MIN_ZOOM) Offset.Zero else offsetState.value + panChange
      event.changes.forEach { if (it.positionChanged()) it.consume() }
    } else {
      totalDrag += panChange
    }
    pressed = event.changes.any { it.pressed }
  } while (pressed)
  return totalDrag
}

private fun resolveSwipe(
  totalDrag: Offset,
  thresholdPx: Float,
  onSwipeUp: () -> Unit,
  onSwipeDown: () -> Unit,
  onSwipeLeft: () -> Unit,
) {
  when {
    totalDrag.y <= -thresholdPx -> onSwipeUp()
    totalDrag.y >= thresholdPx -> onSwipeDown()
    totalDrag.x <= -thresholdPx -> onSwipeLeft()
  }
}

@Composable
private fun BoxScope.AssetPhoto(asset: AssetDto, modifier: Modifier = Modifier) {
  AsyncImage(
    model = thumbnailUrl(asset.id, AssetThumbnailSize.PREVIEW),
    contentDescription = asset.originalFileName,
    contentScale = ContentScale.Fit,
    modifier = modifier.fillMaxSize(),
  )
  if (asset.type == AssetTypeEnum.VIDEO) {
    Box(
      modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(text = "▶")
        Text(text = stringResource(R.string.asset_detail_video_unsupported))
      }
    }
  }
}

@Composable
private fun AssetDetailsPanel(
  state: AssetDetailUiState.Loaded,
  contentPadding: PaddingValues,
  onToggleFavorite: () -> Unit,
) {
  val asset = state.asset
  Column(
    modifier =
      Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(contentPadding)
        .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(text = asset.originalFileName, textAlign = TextAlign.Center)
    Text(text = formatLocalDateTime(asset.localDateTime), textAlign = TextAlign.Center)
    asset.exifInfo?.let { exif -> ExifDetails(exif) }
    FavoriteToggle(
      isFavorite = asset.isFavorite,
      enabled = !state.isTogglingFavorite,
      onClick = onToggleFavorite,
    )
  }
}

@Composable
private fun FavoriteToggle(isFavorite: Boolean, enabled: Boolean, onClick: () -> Unit) {
  Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
    Text(text = if (isFavorite) "♥" else "♡")
  }
}
