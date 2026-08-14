package fi.nikosavola.immichwear.ui.detail

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.EdgeButton
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
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val REVEAL_COMMIT_THRESHOLD = 0.5f
private val SWIPE_COMMIT_THRESHOLD = 48.dp
private val DIRECTION_DEADZONE = 12.dp

// Reserves room at the bottom of the scrollable metadata column so its last lines can clear the
// EdgeButton floating on top of it instead of staying permanently hidden behind it.
private val DETAILS_BOTTOM_INSET = 72.dp

private enum class PhotoGestureMode {
  UNDECIDED,
  ZOOM_PAN,
  VERTICAL_SWIPE,
  HORIZONTAL_REVEAL,
  IGNORED,
}

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
  // Swiping left over the photo reveals this, live-tracking the drag (see ZoomableAssetPhoto);
  // swiping right within it (via SwipeToDismissBox) dismisses it - a nested, in-screen dismiss
  // distinct from the NavHost's own back-swipe.
  var showDetails by rememberSaveable(state.asset.id) { mutableStateOf(false) }
  val revealProgress = remember(state.asset.id) { mutableFloatStateOf(0f) }

  if (showDetails) {
    SwipeToDismissBox(
      onDismissed = {
        showDetails = false
        revealProgress.floatValue = 0f
      }
    ) { isBackground ->
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
      state = state,
      contentPadding = contentPadding,
      revealProgress = revealProgress,
      onToggleFavorite = onToggleFavorite,
      onSwipeUp = { if (state.hasNext) onNext() },
      onSwipeDown = { if (state.hasPrevious) onPrevious() },
      onRevealCommitted = { showDetails = true },
    )
  }
}

// Renders the photo plus a details-panel preview stacked underneath it, both live-translated
// horizontally by revealProgress so the swipe-left-to-reveal gesture tracks the finger instead of
// only animating after the gesture ends. Also handles pinch/double-tap zoom, pan while zoomed, and
// vertical swipe to the next/previous photo.
@Composable
private fun BoxScope.ZoomableAssetPhoto(
  state: AssetDetailUiState.Loaded,
  contentPadding: PaddingValues,
  revealProgress: MutableFloatState,
  onToggleFavorite: () -> Unit,
  onSwipeUp: () -> Unit,
  onSwipeDown: () -> Unit,
  onRevealCommitted: () -> Unit,
) {
  val asset = state.asset
  val zoomState = remember(asset.id) { mutableFloatStateOf(MIN_ZOOM) }
  val offsetState = remember(asset.id) { mutableStateOf(Offset.Zero) }
  val coroutineScope = rememberCoroutineScope()

  Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
    Box(
      modifier =
        Modifier.fillMaxSize().graphicsLayer {
          translationX = (1f - revealProgress.floatValue) * size.width
        }
    ) {
      AssetDetailsPanel(
        state = state,
        contentPadding = contentPadding,
        onToggleFavorite = onToggleFavorite,
      )
    }

    AssetPhoto(
      asset = asset,
      modifier =
        Modifier.graphicsLayer {
            translationX = -revealProgress.floatValue * size.width + offsetState.value.x
            translationY = offsetState.value.y
            scaleX = zoomState.floatValue
            scaleY = zoomState.floatValue
          }
          .pointerInput(asset.id) {
            awaitEachGesture {
              detectPhotoGesture(zoomState, offsetState, revealProgress) {
                mode,
                totalDrag,
                swipeThresholdPx ->
                handleGestureEnd(
                  mode,
                  totalDrag,
                  swipeThresholdPx,
                  revealProgress,
                  coroutineScope,
                  onSwipeUp,
                  onSwipeDown,
                  onRevealCommitted,
                )
              }
            }
          }
          .pointerInput(asset.id) {
            detectTapGestures(
              onDoubleTap = {
                coroutineScope.launch { toggleDoubleTapZoom(zoomState, offsetState) }
              }
            )
          },
    )
  }
}

// Runs once the raw drag/pinch tracking in detectPhotoGesture ends. detectPhotoGesture calls this
// from within AwaitPointerEventScope's restricted-suspension context, but since this itself is a
// plain (non-suspend) function, that call is unrestricted - launching a fresh coroutine here is how
// the smooth settle animation below escapes back out into an unrestricted context.
private fun handleGestureEnd(
  mode: PhotoGestureMode,
  totalDrag: Offset,
  swipeThresholdPx: Float,
  revealProgress: MutableFloatState,
  coroutineScope: CoroutineScope,
  onSwipeUp: () -> Unit,
  onSwipeDown: () -> Unit,
  onRevealCommitted: () -> Unit,
) {
  when (mode) {
    PhotoGestureMode.VERTICAL_SWIPE -> {
      when {
        totalDrag.y <= -swipeThresholdPx -> onSwipeUp()
        totalDrag.y >= swipeThresholdPx -> onSwipeDown()
      }
    }
    PhotoGestureMode.HORIZONTAL_REVEAL -> {
      val target = if (revealProgress.floatValue > REVEAL_COMMIT_THRESHOLD) 1f else 0f
      coroutineScope.launch {
        animate(revealProgress.floatValue, target) { value, _ -> revealProgress.floatValue = value }
        if (target == 1f) onRevealCommitted()
      }
    }
    PhotoGestureMode.UNDECIDED,
    PhotoGestureMode.ZOOM_PAN,
    PhotoGestureMode.IGNORED -> {}
  }
}

private suspend fun toggleDoubleTapZoom(
  zoomState: MutableFloatState,
  offsetState: MutableState<Offset>,
) {
  if (zoomState.floatValue > MIN_ZOOM) {
    animate(zoomState.floatValue, MIN_ZOOM) { value, _ -> zoomState.floatValue = value }
    offsetState.value = Offset.Zero
  } else {
    animate(zoomState.floatValue, DOUBLE_TAP_ZOOM) { value, _ -> zoomState.floatValue = value }
  }
}

// Tracks a single gesture from first touch to all-fingers-up. Two fingers down, or one finger
// down after zoom was already above MIN_ZOOM, means zoom/pan for the rest of the gesture. A
// single-finger drag otherwise picks its axis once it clears a small deadzone: vertical becomes a
// swipe candidate (resolved on release against SWIPE_COMMIT_THRESHOLD); leftward-horizontal
// live-tracks revealProgress so the details panel follows the finger; rightward-horizontal at
// revealProgress 0 is left unconsumed so the NavHost's own back-swipe can still claim it.
//
// Only awaitFirstDown/awaitPointerEvent (AwaitPointerEventScope's own members) and plain state
// writes happen in this function's body - AwaitPointerEventScope carries @RestrictsSuspension,
// which bans calling any other suspend function from within it, Animatable.snapTo/animateTo
// included. onGestureEnd is a plain (non-suspend) callback, so invoking it here is unrestricted.
private suspend fun AwaitPointerEventScope.detectPhotoGesture(
  zoomState: MutableFloatState,
  offsetState: MutableState<Offset>,
  revealProgress: MutableFloatState,
  onGestureEnd: (mode: PhotoGestureMode, totalDrag: Offset, swipeThresholdPx: Float) -> Unit,
) {
  awaitFirstDown(requireUnconsumed = false)
  val widthPx = size.width.toFloat()
  val deadzonePx = DIRECTION_DEADZONE.toPx()
  var step = GestureStep(PhotoGestureMode.UNDECIDED, Offset.Zero)
  var pressed: Boolean
  do {
    val event = awaitPointerEvent()
    step =
      handleGestureEvent(event, step, zoomState, offsetState, revealProgress, widthPx, deadzonePx)
    pressed = event.changes.any { it.pressed }
  } while (pressed)

  onGestureEnd(step.mode, step.totalDrag, SWIPE_COMMIT_THRESHOLD.toPx())
}

private data class GestureStep(val mode: PhotoGestureMode, val totalDrag: Offset)

private fun handleGestureEvent(
  event: PointerEvent,
  step: GestureStep,
  zoomState: MutableFloatState,
  offsetState: MutableState<Offset>,
  revealProgress: MutableFloatState,
  widthPx: Float,
  deadzonePx: Float,
): GestureStep {
  val zoomChange = event.calculateZoom()
  val panChange = event.calculatePan()
  if (event.changes.count { it.pressed } > 1 || zoomState.floatValue > MIN_ZOOM) {
    val newZoom = (zoomState.floatValue * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
    zoomState.floatValue = newZoom
    offsetState.value = if (newZoom <= MIN_ZOOM) Offset.Zero else offsetState.value + panChange
    event.changes.forEach { if (it.positionChanged()) it.consume() }
    return GestureStep(PhotoGestureMode.ZOOM_PAN, step.totalDrag)
  }

  val totalDrag = step.totalDrag + panChange
  val mode =
    if (step.mode == PhotoGestureMode.UNDECIDED) decideAxis(totalDrag, deadzonePx) else step.mode
  if (mode == PhotoGestureMode.HORIZONTAL_REVEAL) {
    val deltaProgress = -panChange.x / widthPx
    revealProgress.floatValue = (revealProgress.floatValue + deltaProgress).coerceIn(0f, 1f)
    event.changes.forEach { if (it.positionChanged()) it.consume() }
  }
  return GestureStep(mode, totalDrag)
}

private fun decideAxis(totalDrag: Offset, deadzonePx: Float): PhotoGestureMode {
  if (abs(totalDrag.x) <= deadzonePx && abs(totalDrag.y) <= deadzonePx) {
    return PhotoGestureMode.UNDECIDED
  }
  return when {
    abs(totalDrag.y) >= abs(totalDrag.x) -> PhotoGestureMode.VERTICAL_SWIPE
    totalDrag.x < 0 -> PhotoGestureMode.HORIZONTAL_REVEAL
    else -> PhotoGestureMode.IGNORED
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
private fun BoxScope.AssetDetailsPanel(
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
        .padding(horizontal = 24.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(text = asset.originalFileName, textAlign = TextAlign.Center)
    Text(text = formatLocalDateTime(asset.localDateTime), textAlign = TextAlign.Center)
    asset.exifInfo?.let { exif -> ExifDetails(exif) }
    Spacer(modifier = Modifier.height(DETAILS_BOTTOM_INSET))
  }
  EdgeButton(
    onClick = onToggleFavorite,
    modifier = Modifier.align(Alignment.BottomCenter),
    enabled = !state.isTogglingFavorite,
  ) {
    Text(text = if (asset.isFavorite) "♥" else "♡")
  }
}
