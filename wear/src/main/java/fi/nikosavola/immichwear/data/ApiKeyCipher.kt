package fi.nikosavola.immichwear.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "immich_api_key"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val GCM_IV_LENGTH_BYTES = 12

/** Encrypts/decrypts the Immich API key for at-rest storage. */
interface ApiKeyCipher {
  fun encrypt(plaintext: String): String

  /**
   * Null if [ciphertext] can't be decrypted: corrupted data, or a keystore wiped since it was
   * written.
   */
  fun decrypt(ciphertext: String): String?
}

/**
 * AES-256-GCM, keyed by a key held in the Android Keystore: the key material never leaves the
 * keystore, only ciphertext is ever persisted. `androidx.security.crypto` is deprecated in favor of
 * direct Keystore use (AndroidX Security release notes, security-crypto 1.1.0-beta01), which is
 * exactly what this does; no new dependency needed.
 */
class AndroidKeystoreApiKeyCipher : ApiKeyCipher {
  private val secretKey: SecretKey by lazy { getOrCreateKey() }

  override fun encrypt(plaintext: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey) }
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
  }

  override fun decrypt(ciphertext: String): String? {
    val combined =
      try {
        Base64.decode(ciphertext, Base64.NO_WRAP)
      } catch (e: IllegalArgumentException) {
        null
      }
    // The size check guards the copyOfRange calls below against corrupted/truncated input
    // shorter than one IV.
    return combined
      ?.takeIf { it.size > GCM_IV_LENGTH_BYTES }
      ?.let { bytes ->
        val iv = bytes.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encrypted = bytes.copyOfRange(GCM_IV_LENGTH_BYTES, bytes.size)
        try {
          val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
              init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
          String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
          null
        }
      }
  }

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
      return it
    }

    val spec =
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
      .apply { init(spec) }
      .generateKey()
  }
}
