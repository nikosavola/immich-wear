package fi.nikosavola.immichwear.tile

import android.content.Context
import android.graphics.Bitmap
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import fi.nikosavola.immichwear.ui.MainActivity
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

fun messageLayout(context: Context, message: String): LayoutElementBuilders.LayoutElement =
  LayoutElementBuilders.Box.Builder()
    .setWidth(DimensionBuilders.expand())
    .setHeight(DimensionBuilders.expand())
    .setModifiers(
      ModifiersBuilders.Modifiers.Builder().setClickable(openAppClickable(context)).build()
    )
    .addContent(LayoutElementBuilders.Text.Builder().setText(message).build())
    .build()

private fun openAppClickable(context: Context): ModifiersBuilders.Clickable =
  ModifiersBuilders.Clickable.Builder()
    .setId(OPEN_APP_CLICKABLE_ID)
    .setOnClick(
      ActionBuilders.LaunchAction.Builder()
        .setAndroidActivity(
          ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName(MainActivity::class.java.name)
            .build()
        )
        .build()
    )
    .build()
