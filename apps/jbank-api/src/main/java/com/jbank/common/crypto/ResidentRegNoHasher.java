package com.jbank.common.crypto;

/** 실명번호 중복 확인용 조회 키를 만든다. 암호문은 복호화 없이 비교할 수 없어 이 해시로 색인한다. */
public final class ResidentRegNoHasher {

  private ResidentRegNoHasher() {}

  public static String hash(String residentRegNo) {
    return HmacSha256.hash(residentRegNo, HmacKeyHolder.getKey());
  }
}
