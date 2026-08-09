import { create } from "zustand";
import { persist } from "zustand/middleware";

/**
 * 로그인 응답에는 있지만 별도 조회 API가 없는 customerId/name을 들고 있는다.
 * 인증 자체는 httpOnly 쿠키가 하므로, 여기 값은 화면 표시와 계좌 목록 조회 경로
 * 구성에만 쓰인다 — 탈취돼도 인증이 뚫리지 않는다.
 */
type AuthState = {
  customerId: string | null;
  name: string | null;
  login: (customerId: string, name: string) => void;
  logout: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      customerId: null,
      name: null,
      login: (customerId, name) => set({ customerId, name }),
      logout: () => set({ customerId: null, name: null }),
    }),
    { name: "jbank-auth" },
  ),
);
