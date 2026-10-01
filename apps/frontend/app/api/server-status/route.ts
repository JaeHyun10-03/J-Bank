import { NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_API_URL ?? "http://localhost:8080";
const TIMEOUT_MS = 3000;

/**
 * 백엔드가 켜져 있는지. 본 포트의 /readyz를 부른다(actuator는 관리 포트로 분리되어 외부에 없다).
 * HTTP 응답을 받기만 하면 online이다 — readyz가 503(일부 구성 요소 DOWN)이거나
 * caddy가 502(api 기동 중)를 줘도 서버는 켜져 있으므로 "꺼짐" 안내를 띄우지 않는다.
 * 연결 실패·3초 초과만 offline. GET 한 번이라 시간 제한을 걸어도 부작용이 없다.
 */
export async function GET() {
  try {
    await fetch(`${BACKEND_URL}/readyz`, {
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    return NextResponse.json({ online: true });
  } catch {
    return NextResponse.json({ online: false });
  }
}

export const dynamic = "force-dynamic";
