"use client";

import { useRouter } from "next/navigation";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/lib/auth-store";
import { Button } from "@/components/ui/button";

/** API-015 로그아웃. 서버가 쿠키 3종을 만료시키고, 클라이언트는 로컬 인증 상태만 비운다. */
export function AppHeader() {
  const router = useRouter();
  const name = useAuthStore((state) => state.name);
  const logout = useAuthStore((state) => state.logout);

  async function handleLogout() {
    try {
      await apiClient.post("/auth/logout");
    } finally {
      logout();
      router.push("/login");
    }
  }

  return (
    <header className="flex items-center justify-between border-b pb-3">
      <span className="text-sm text-muted-foreground">{name ? `${name}님` : ""}</span>
      <Button variant="outline" size="sm" onClick={handleLogout}>
        로그아웃
      </Button>
    </header>
  );
}
