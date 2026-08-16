import { create } from "zustand";

/**
 * 이체 입력 → 확인 → (OTP) 화면 간 공유 상태. accountId 라우트 파라미터로 이미
 * 화면이 구분되므로 계좌별로 따로 스코프하지 않는다(회원가입 위저드와 같은 한계).
 */
type TransferWizardState = {
  fromAccountNumber: string;
  toAccountNumber: string;
  amount: number;
  memo: string;
  transactionId: string;
  /** API-008 응답에는 만료 시각이 없다 — 202를 받는 시점에 클라이언트가 3분 뒤로 직접 계산해 넣는다. */
  otpExpiresAt: string;
  setInput: (input: {
    fromAccountNumber: string;
    toAccountNumber: string;
    amount: number;
    memo: string;
  }) => void;
  setPendingOtp: (otp: { transactionId: string; otpExpiresAt: string }) => void;
  reset: () => void;
};

const initialState = {
  fromAccountNumber: "",
  toAccountNumber: "",
  amount: 0,
  memo: "",
  transactionId: "",
  otpExpiresAt: "",
};

export const useTransferWizardStore = create<TransferWizardState>()((set) => ({
  ...initialState,
  setInput: (input) => set(input),
  setPendingOtp: (otp) => set(otp),
  reset: () => set(initialState),
}));
