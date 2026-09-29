/** @jest-environment node */
import { GET } from "./route";

afterEach(() => jest.restoreAllMocks());

describe("서버 상태 확인", () => {
  it.each([200, 502, 503])("백엔드가 %i로라도 응답하면 online", async (status) => {
    jest.spyOn(global, "fetch").mockResolvedValue(new Response("", { status }));
    expect(await (await GET()).json()).toEqual({ online: true });
  });

  it("연결 실패면 offline", async () => {
    jest.spyOn(global, "fetch").mockRejectedValue(new TypeError("fetch failed"));
    expect(await (await GET()).json()).toEqual({ online: false });
  });

  it("3초 안에 응답이 없으면 offline", async () => {
    jest.spyOn(global, "fetch").mockImplementation(
      (_url, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener("abort", () => reject(new DOMException("timeout", "TimeoutError")));
        }),
    );
    const started = Date.now();
    expect(await (await GET()).json()).toEqual({ online: false });
    const elapsed = Date.now() - started;
    expect(elapsed).toBeGreaterThanOrEqual(2900);
    expect(elapsed).toBeLessThan(5000);
  }, 10000);
});
