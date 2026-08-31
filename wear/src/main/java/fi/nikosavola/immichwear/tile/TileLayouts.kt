package fi.nikosavola.immichwear.tile

import android.content.Context
import android.graphics.Bitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import fi.nikosavola.immichwear.ui.MainActivity
import fi.nikosavola.immichwear.ui.navigation.ImmichRoutes
import java.nio.ByteBuffer

private const val OPEN_APP_CLICKABLE_ID = "open_immich"

fun inlineImage(rgb565: ByteArray, sizePx: Int): ResourceBuilders.ImageResource =
  ResourceBuilders.ImageResource.Builder()
    .setInlineResource(
      ResourceBuilders.InlineImageResource.Builder()
        .setData(rgb565)
        .setWidthPx(sizePx)
        .setHeightPx(sizePx)
        .setFormat(ResourceBuilders.IMAGE_FORMAT_RGB_565)
        .build()
    )
    .build()

fun toRgb565Bytes(bitmap: Bitmap): ByteArray {
  val rgb565 = bitmap.copy(Bitmap.Config.RGB_565, false)
  val buffer = ByteBuffer.allocate(rgb565.byteCount)
  rgb565.copyPixelsToBuffer(buffer)
  return buffer.array()
}

fun photoLayout(context: Context, resourceId: String): LayoutElementBuilders.LayoutElement =
  LayoutElementBuilders.Box.Builder()
    .setWidth(DimensionBuilders.expand())
    .setHeight(DimensionBuilders.expand())
    .setModifiers(
      ModifiersBuilders.Modifiers.Builder().setClickable(openAppClickable(context)).build()
    )
    .addContent(
      LayoutElementBuilders.Image.Builder()
        .setResourceId(resourceId)
        .setWidth(DimensionBuilders.expand())
        .setHeight(DimensionBuilders.expand())
        .setContentScaleMode(LayoutElementBuilders.CONTENT_SCALE_MODE_CROP)
        .build()
    )
    .build()

// startDestination, when non-null, opens straight to that NavHost route instead of Home - used to
// send the signed-out tile's tap directly to Settings rather than making the user tap through Home
// first.
fun messageLayout(
  context: Context,
  message: String,
  startDestination: String? = null,
): LayoutElementBuilders.LayoutElement =
  LayoutElementBuilders.Box.Builder()
    .setWidth(DimensionBuilders.expand())
    .setHeight(DimensionBuilders.expand())
    .setModifiers(
      ModifiersBuilders.Modifiers.Builder()
        .setClickable(openAppClickable(context, startDestination))
        .build()
    )
    .addContent(LayoutElementBuilders.Text.Builder().setText(message).build())
    .build()

private fun openAppClickable(
  context: Context,
  startDestination: String? = null,
): ModifiersBuilders.Clickable {
  val activityBuilder =
    ActionBuilders.AndroidActivity.Builder()
      .setPackageName(context.packageName)
      .setClassName(MainActivity::class.java.name)
  if (startDestination != null) {
    activityBuilder.addKeyToExtraMapping(
      ImmichRoutes.EXTRA_START_DESTINATION,
      ActionBuilders.AndroidStringExtra.Builder().setValue(startDestination).build(),
    )
  }
  return ModifiersBuilders.Clickable.Builder()
    .setId(OPEN_APP_CLICKABLE_ID)
    .setOnClick(
      ActionBuilders.LaunchAction.Builder().setAndroidActivity(activityBuilder.build()).build()
    )
    .build()
}
