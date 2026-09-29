"use client";

import { useEffect } from "react";
import { useServerStatus } from "@/lib/server-status";

export const OFFLINE_MESSAGE =
  "지금은 서버가 꺼져 있습니다. 데모 서버는 평일 09:00~18:00(KST)에만 운영합니다.";

/** 첫 진입 때 서버 상태를 확인하고, 꺼져 있으면(또는 API 실패 뒤 꺼짐이 확인되면) 안내한다. */
export function ServerOfflineNotice() {
  const offline = useServerStatus((s) => s.offline);
  const check = useServerStatus((s) => s.check);

  useEffect(() => {
    void check();
  }, [check]);

  if (!offline) return null;
  return (
    <div role="alert" className="w-full border-b border-slate-300 bg-slate-900 text-white">
      <p className="mx-auto max-w-5xl px-4 py-2 text-center text-sm leading-snug">{OFFLINE_MESSAGE}</p>
    </div>
  );
}
