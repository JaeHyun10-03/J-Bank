import { NextRequest, NextResponse } from "next/server";

/**
 * 화면플로우차트 3절의 "인증 쿠키 있음?" 분기를 라우팅 단계에서 처리한다. access_token은
 * JWT TTL과 같은 maxAge로 발급되므로, 만료되면 브라우저가 쿠키 자체를 지운다 — 존재 여부만
 * 봐도 "있음/없음"은 구분되고 "만료"는 자연스럽게 "없음"과 같은 신호가 된다. access_token만
 * 없고 refresh_token은 남아 있으면 그대로 통과시킨다 — apiClient의 401 인터셉터가 첫 API
 * 호출에서 조용히 재발급을 시도한다(이중으로 처리할 필요 없음). 둘 다 없을 때만 로그인으로
 * 보낸다.
 */
const PUBLIC_PATHS = ["/welcome", "/login", "/signup"];

export function middleware(request: NextRequest) {
  if (PUBLIC_PATHS.includes(request.nextUrl.pathname)) {
    return NextResponse.next();
  }

  const hasAuthCookie =
    request.cookies.has("access_token") || request.cookies.has("refresh_token");
  if (!hasAuthCookie) {
    return NextResponse.redirect(new URL("/welcome", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico).*)"],
};
