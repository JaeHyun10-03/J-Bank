package com.jbank.account.domain;

public final class AccountNumberChecker {

  private AccountNumberChecker() {}

  public static int calculateCheckDigit(String digits) {
    validateNumeric(digits);
    int sum = 0;
    boolean doubleDigit = true;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int digit = digits.charAt(i) - '0';
      if (doubleDigit) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      doubleDigit = !doubleDigit;
    }
    return (10 - (sum % 10)) % 10;
  }

  public static boolean isValid(String accountNumberDigits) {
    validateNumeric(accountNumberDigits);
    if (accountNumberDigits.length() < 2) {
      return false;
    }
    String body = accountNumberDigits.substring(0, accountNumberDigits.length() - 1);
    int checkDigit = accountNumberDigits.charAt(accountNumberDigits.length() - 1) - '0';
    return calculateCheckDigit(body) == checkDigit;
  }

  private static void validateNumeric(String digits) {
    if (digits == null || digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
      throw new IllegalArgumentException("계좌번호는 숫자만 포함해야 합니다: " + digits);
    }
  }
}
