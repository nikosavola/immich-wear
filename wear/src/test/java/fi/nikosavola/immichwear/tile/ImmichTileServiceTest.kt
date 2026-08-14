package fi.nikosavola.immichwear.tile

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImmichTileServiceTest {
  @Test
  fun `a landscape source is cropped to the largest centered square, not stretched`() {
    val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)

    val result = centerCropSquare(source)

    assertEquals(300, result.width)
    assertEquals(300, result.height)
  }

  @Test
  fun `a portrait source is cropped to the largest centered square, not stretched`() {
    val source = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)

    val result = centerCropSquare(source)

    assertEquals(300, result.width)
    assertEquals(300, result.height)
  }

  @Test
  fun `an already-square source is returned unchanged in size`() {
    val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)

    val result = centerCropSquare(source)

    assertEquals(200, result.width)
    assertEquals(200, result.height)
  }
}
