package com.jbank.customer.domain;

import com.jbank.global.exception.DomainException;
import com.jbank.global.exception.ErrorCode;

public class CustomerException extends DomainException {

  public CustomerException(ErrorCode errorCode) {
    super(errorCode);
  }
}
