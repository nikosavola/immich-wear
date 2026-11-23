package fi.nikosavola.immichwear.tile

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImmichTileServiceTest {
  @Test
  fun `a landscape source is cropped to a square at the target size, not stretched`() {
    val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)

    val result = centerCropSquareScaled(source, 150)

    assertEquals(150, result.width)
    assertEquals(150, result.height)
  }

  @Test
  fun `a portrait source is cropped to a square at the target size, not stretched`() {
    val source = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)

    val result = centerCropSquareScaled(source, 150)

    assertEquals(150, result.width)
    assertEquals(150, result.height)
  }

  @Test
  fun `an already-square source is scaled to the target size`() {
    val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)

    val result = centerCropSquareScaled(source, 300)

    assertEquals(300, result.width)
    assertEquals(300, result.height)
  }

  @Test
  fun `the crop takes the centered square and drops the side margins`() {
    // Red margins outside the centered 300px square, blue inside.
    val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
    for (x in 0 until 400) {
      val color = if (x < 50 || x >= 350) Color.RED else Color.BLUE
      for (y in 0 until 300) source.setPixel(x, y, color)
    }

    val result = centerCropSquareScaled(source, 150)

    for (x in listOf(0, 75, 149)) {
      assertEquals(Color.BLUE, result.getPixel(x, 75))
    }
  }
}
