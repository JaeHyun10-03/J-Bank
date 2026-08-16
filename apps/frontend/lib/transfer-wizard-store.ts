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
  otpSentTo: string;
  otpExpiresAt: string;
  holdAmount: number;
  setInput: (input: {
    fromAccountNumber: string;
    toAccountNumber: string;
    amount: number;
    memo: string;
  }) => void;
  setPendingOtp: (otp: {
    transactionId: string;
    otpSentTo: string;
    otpExpiresAt: string;
    holdAmount: number;
  }) => void;
  reset: () => void;
};

const initialState = {
  fromAccountNumber: "",
  toAccountNumber: "",
  amount: 0,
  memo: "",
  transactionId: "",
  otpSentTo: "",
  otpExpiresAt: "",
  holdAmount: 0,
};

export const useTransferWizardStore = create<TransferWizardState>()((set) => ({
  ...initialState,
  setInput: (input) => set(input),
  setPendingOtp: (otp) => set(otp),
  reset: () => set(initialState),
}));
