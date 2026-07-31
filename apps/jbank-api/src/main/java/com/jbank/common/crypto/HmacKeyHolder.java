package com.jbank.common.crypto;

import jakarta.annotation.PostConstruct;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 실명번호 중복 확인용 해시 키 보관소. 암호화 키(PiiEncryptionKeyHolder)와 별도로 관리한다 — 해시 키를 로테이션하면 기존 색인이 전부 무효가 되므로
 * 로테이션 대상에서 제외한다(ERD 8절).
 */
@Component
public class HmacKeyHolder {

  private static volatile SecretKey key;

  @Value("${jbank.crypto.hash-key}")
  private String base64Key;

  @PostConstruct
  void init() {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    key = new SecretKeySpec(keyBytes, "HmacSHA256");
  }

  public static SecretKey getKey() {
    if (key == null) {
      throw new IllegalStateException("실명번호 해시 키가 초기화되지 않았습니다");
    }
    return key;
  }
}
