import type { InternalAxiosRequestConfig } from "axios";
import { apiClient } from "./api-client";

function captureRequestConfig(): Promise<InternalAxiosRequestConfig> {
  return new Promise((resolve) => {
    apiClient.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
      resolve(config);
      return { data: null, status: 200, statusText: "OK", headers: {}, config };
    };
  });
}

describe("apiClient 요청 인터셉터", () => {
  afterEach(() => {
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("변경 요청에는 Idempotency-Key를 자동 생성해 붙인다", async () => {
    const captured = captureRequestConfig();
    await apiClient.post("/accounts/1/deposit", {});
    const config = await captured;
    expect(config.headers.get("Idempotency-Key")).toBeTruthy();
  });

  it("이미 Idempotency-Key가 있으면 덮어쓰지 않는다 — 재시도가 같은 키를 유지해야 한다", async () => {
    const captured = captureRequestConfig();
    await apiClient.post(
      "/transfers",
      {},
      { headers: { "Idempotency-Key": "fixed-key" } },
    );
    const config = await captured;
    expect(config.headers.get("Idempotency-Key")).toBe("fixed-key");
  });

  it("GET 요청에는 Idempotency-Key를 붙이지 않는다", async () => {
    const captured = captureRequestConfig();
    await apiClient.get("/products");
    const config = await captured;
    expect(config.headers.get("Idempotency-Key")).toBeFalsy();
  });

  it("XSRF-TOKEN 쿠키가 있으면 변경 요청에 X-CSRF-TOKEN 헤더로 복사한다", async () => {
    document.cookie = "XSRF-TOKEN=test-csrf-value";
    const captured = captureRequestConfig();
    await apiClient.post("/accounts", {});
    const config = await captured;
    expect(config.headers.get("X-CSRF-TOKEN")).toBe("test-csrf-value");
  });
});
