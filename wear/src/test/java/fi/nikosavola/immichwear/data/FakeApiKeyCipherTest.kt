package fi.nikosavola.immichwear.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeApiKeyCipherTest {
  @Test
  fun `encrypt then decrypt round-trips the plaintext`() {
    val cipher = FakeApiKeyCipher()

    val ciphertext = cipher.encrypt("secret-key")

    assertEquals("secret-key", cipher.decrypt(ciphertext))
  }

  @Test
  fun `decrypt returns null for data not produced by encrypt`() {
    assertNull(FakeApiKeyCipher().decrypt("not-encrypted-by-this-cipher"))
  }
}
