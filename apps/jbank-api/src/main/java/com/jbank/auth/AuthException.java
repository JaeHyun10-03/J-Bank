package com.jbank.auth;

import com.jbank.global.exception.DomainException;
import com.jbank.global.exception.ErrorCode;

public class AuthException extends DomainException {

  public AuthException(ErrorCode errorCode) {
    super(errorCode);
  }

  public AuthException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
