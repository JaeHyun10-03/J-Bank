package com.jbank.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** 실명번호·연락처·주소처럼 컬럼 레벨 암호화가 필요한 String 속성에 붙이는 JPA 컨버터. */
@Converter
public class AesGcmAttributeConverter implements AttributeConverter<String, String> {

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      return null;
    }
    return AesGcmCipher.encrypt(attribute, PiiEncryptionKeyHolder.getKey());
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return AesGcmCipher.decrypt(dbData, PiiEncryptionKeyHolder.getKey());
  }
}
