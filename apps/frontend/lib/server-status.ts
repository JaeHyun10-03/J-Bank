import { create } from "zustand";

/**
 * 운영 서버는 평일 09~18시(KST)에만 켜진다(docs/adr/0011). 꺼져 있는지는 시계가 아니라
 * `/api/server-status`(백엔드 health에 실제로 닿는지)로 판단해 공휴일·수동 기동과 어긋나지 않는다.
 */
type ServerStatusState = {
  offline: boolean;
  check: () => Promise<void>;
};

export const useServerStatus = create<ServerStatusState>((set) => ({
  offline: false,
  check: async () => {
    try {
      const res = await fetch("/api/server-status", { cache: "no-store" });
      const body = (await res.json()) as { online: boolean };
      set({ offline: !body.online });
    } catch {
      // 상태 확인 자체가 실패하면(프론트 배포 문제 등) 안내를 띄우지 않고 기존 오류 처리에 맡긴다.
    }
  },
}));

/** 서버가 꺼졌을 때 나올 수 있는 실패: 응답 없음(네트워크)·게이트웨이 오류·프록시의 SERVER_OFFLINE. */
export function mayBeOffline(status: number | undefined): boolean {
  return status === undefined || status === 502 || status === 503 || status === 504;
}
