package com.jbank.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-256-GCM 대칭키 암복호화. IV(12바이트)를 암호문 앞에 붙여 Base64 문자열 하나로 저장한다. */
public final class AesGcmCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private AesGcmCipher() {}

  public static String encrypt(String plainText, SecretKey key) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      new SecureRandom().nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      byte[] ivAndCipherText = new byte[iv.length + cipherBytes.length];
      System.arraycopy(iv, 0, ivAndCipherText, 0, iv.length);
      System.arraycopy(cipherBytes, 0, ivAndCipherText, iv.length, cipherBytes.length);

      return Base64.getEncoder().encodeToString(ivAndCipherText);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM 암호화에 실패했습니다", e);
    }
  }

  public static String decrypt(String cipherText, SecretKey key) {
    try {
      byte[] ivAndCipherText = Base64.getDecoder().decode(cipherText);
      byte[] iv = new byte[IV_LENGTH_BYTES];
      byte[] cipherBytes = new byte[ivAndCipherText.length - IV_LENGTH_BYTES];
      System.arraycopy(ivAndCipherText, 0, iv, 0, iv.length);
      System.arraycopy(ivAndCipherText, iv.length, cipherBytes, 0, cipherBytes.length);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] plainBytes = cipher.doFinal(cipherBytes);

      return new String(plainBytes, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("AES-GCM 복호화에 실패했습니다(데이터 변조 가능성)", e);
    }
  }
}
