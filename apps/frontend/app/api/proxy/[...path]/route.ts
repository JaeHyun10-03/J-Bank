import { NextRequest, NextResponse } from "next/server";
import { isBackendConnectError } from "@/lib/backend-offline";

/**
 * 프론트(Vercel)와 백엔드(AWS)가 다른 오리진이라 쿠키 기반 인증이 그대로는 안 먹힌다.
 * 브라우저는 항상 이 same-site 프록시만 호출하고, 여기서 서버 대 서버로 백엔드를 호출한다.
 * 인프라아키텍처 문서의 same-site 프록시 패턴.
 */
const BACKEND_URL = process.env.BACKEND_API_URL ?? "http://localhost:8080";

async function proxy(request: NextRequest, { params }: { params: { path: string[] } }) {
  const targetUrl = `${BACKEND_URL}/${params.path.join("/")}${request.nextUrl.search}`;

  const headers = new Headers(request.headers);
  headers.delete("host");
  headers.delete("content-length");

  const hasBody = !["GET", "HEAD"].includes(request.method);
  let backendResponse: Response;
  try {
    backendResponse = await fetch(targetUrl, {
      method: request.method,
      headers,
      body: hasBody ? await request.arrayBuffer() : undefined,
      redirect: "manual",
    });
  } catch (error) {
    // 운영 인스턴스가 꺼진 시간(평일 09~18시 외)에는 연결 단계에서 실패한다. 화면이 운영 시간
    // 안내를 띄울 수 있게 구분된 코드로 돌려준다. 그 밖의 오류는 그대로 던진다.
    if (isBackendConnectError(error)) {
      return NextResponse.json({ code: "SERVER_OFFLINE" }, { status: 503 });
    }
    throw error;
  }

  const responseHeaders = new Headers(backendResponse.headers);
  responseHeaders.delete("set-cookie");
  responseHeaders.delete("content-encoding");
  responseHeaders.delete("content-length");

  const response = new NextResponse(backendResponse.body, {
    status: backendResponse.status,
    headers: responseHeaders,
  });

  for (const cookie of backendResponse.headers.getSetCookie()) {
    response.headers.append("set-cookie", normalizeCookie(cookie));
  }

  return response;
}

// 백엔드가 Domain을 지정하면 프론트 오리진에서는 그 쿠키를 못 받으므로 제거하고,
// Path는 백엔드의 실제 경로가 아니라 프록시 경로 전체(`/`)에 적용되도록 맞춘다.
function normalizeCookie(cookie: string): string {
  return cookie.replace(/;\s*Domain=[^;]*/i, "").replace(/;\s*Path=[^;]*/i, "; Path=/");
}

export {
  proxy as GET,
  proxy as POST,
  proxy as PUT,
  proxy as PATCH,
  proxy as DELETE,
};
