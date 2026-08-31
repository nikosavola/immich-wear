package fi.nikosavola.immichwear.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImmichThemeTest {
  @Test
  fun `background is forced to black regardless of the scheme's own background`() {
    val scheme = ColorScheme(background = Color(0xFF112233))

    val result = blackBackground(scheme)

    assertEquals(Color.Black, result?.background)
  }

  @Test
  fun `every other color role is left untouched`() {
    val scheme = ColorScheme(primary = Color(0xFF112233), onSurface = Color(0xFF445566))

    val result = blackBackground(scheme)

    assertEquals(scheme.primary, result?.primary)
    assertEquals(scheme.onSurface, result?.onSurface)
  }

  @Test
  fun `a null scheme (no dynamic color available) stays null`() {
    assertNull(blackBackground(null))
  }
}
