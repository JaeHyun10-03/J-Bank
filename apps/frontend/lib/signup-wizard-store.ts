import { create } from "zustand";

/**
 * 회원가입 단계별 화면(Auth4~9) 간 공유 상태. 주민번호를 담기 때문에 localStorage에
 * 영속화하지 않는다 — 새로고침하면 처음부터 다시 입력한다(민감정보 브라우저 저장 회피).
 */
type SignupWizardState = {
  name: string;
  ssnFront: string;
  ssnBack: string;
  carrier: string;
  phone: string;
  otpVerified: boolean;
  setName: (name: string) => void;
  setSsn: (ssnFront: string, ssnBack: string) => void;
  setPhone: (carrier: string, phone: string) => void;
  setOtpVerified: () => void;
  reset: () => void;
};

const initialState = {
  name: "",
  ssnFront: "",
  ssnBack: "",
  carrier: "SKT",
  phone: "",
  otpVerified: false,
};

export const useSignupWizardStore = create<SignupWizardState>()((set) => ({
  ...initialState,
  setName: (name) => set({ name }),
  setSsn: (ssnFront, ssnBack) => set({ ssnFront, ssnBack }),
  setPhone: (carrier, phone) => set({ carrier, phone }),
  setOtpVerified: () => set({ otpVerified: true }),
  reset: () => set(initialState),
}));
