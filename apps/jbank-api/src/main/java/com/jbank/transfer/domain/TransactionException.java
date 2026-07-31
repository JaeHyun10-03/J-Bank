package com.jbank.transfer.domain;

import com.jbank.global.exception.DomainException;
import com.jbank.global.exception.ErrorCode;

public class TransactionException extends DomainException {

  public TransactionException(ErrorCode errorCode) {
    super(errorCode);
  }

  public TransactionException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
