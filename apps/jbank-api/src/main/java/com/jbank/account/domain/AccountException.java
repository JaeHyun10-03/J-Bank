package com.jbank.account.domain;

import com.jbank.global.exception.DomainException;
import com.jbank.global.exception.ErrorCode;

public class AccountException extends DomainException {

  public AccountException(ErrorCode errorCode) {
    super(errorCode);
  }

  public AccountException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
