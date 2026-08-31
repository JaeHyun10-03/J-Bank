package com.jbank.product.domain;

import com.jbank.global.exception.DomainException;
import com.jbank.global.exception.ErrorCode;

public class ProductException extends DomainException {

  public ProductException(ErrorCode errorCode) {
    super(errorCode);
  }

  public ProductException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
