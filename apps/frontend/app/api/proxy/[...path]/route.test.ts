/** @jest-environment node */
import { NextRequest } from "next/server";
import { POST, GET } from "./route";

const ctx = { params: { path: ["api", "v1", "transfers"] } };

function connectError(code: string) {
  return Object.assign(new TypeError("fetch failed"), { cause: { code } });
}

afterEach(() => jest.restoreAllMocks());

describe("백엔드 프록시 — 서버 꺼짐 구분", () => {
  it.each(["ECONNREFUSED", "ETIMEDOUT", "EHOSTUNREACH", "UND_ERR_CONNECT_TIMEOUT"])(
    "연결 단계 오류(%s)는 503 SERVER_OFFLINE",
    async (code) => {
      jest.spyOn(global, "fetch").mockRejectedValue(connectError(code));
      const res = await GET(new NextRequest("http://front/api/proxy/api/v1/accounts"), ctx);
      expect(res.status).toBe(503);
      expect(await res.json()).toEqual({ code: "SERVER_OFFLINE" });
    },
  );

  it("연결 뒤 느린 POST(5초 초과)는 그대로 전달한다 — 처리된 이체를 꺼짐으로 바꾸지 않는다", async () => {
    jest.useFakeTimers();
    jest.spyOn(global, "fetch").mockImplementation(
      () => new Promise((resolve) => setTimeout(() => resolve(new Response("{}", { status: 201 })), 6000)),
    );
    const pending = POST(new NextRequest("http://front/api/proxy/api/v1/transfers", { method: "POST", body: "{}" }), ctx);
    await jest.advanceTimersByTimeAsync(6000);
    const res = await pending;
    jest.useRealTimers();
    expect(res.status).toBe(201);
  });

  it.each([400, 500, 502])("백엔드 응답 %i은 그대로 전달한다", async (status) => {
    jest.spyOn(global, "fetch").mockResolvedValue(new Response("{}", { status }));
    const res = await GET(new NextRequest("http://front/api/proxy/api/v1/accounts"), ctx);
    expect(res.status).toBe(status);
  });

  it("연결 오류가 아닌 예외는 삼키지 않는다", async () => {
    jest.spyOn(global, "fetch").mockRejectedValue(new TypeError("body used"));
    await expect(GET(new NextRequest("http://front/api/proxy/api/v1/accounts"), ctx)).rejects.toThrow("body used");
  });
});
