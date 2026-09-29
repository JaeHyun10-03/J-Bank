import { render, screen, waitFor } from "@testing-library/react";
import { OFFLINE_MESSAGE, ServerOfflineNotice } from "./server-offline-notice";
import { useServerStatus } from "@/lib/server-status";

function mockStatus(online: boolean) {
  global.fetch = jest.fn().mockResolvedValue({ json: async () => ({ online }) }) as unknown as typeof fetch;
}

beforeEach(() => useServerStatus.setState({ offline: false }));

describe("서버 꺼짐 안내", () => {
  it("첫 진입 때 서버가 꺼져 있으면 운영 시간 안내를 alert로 보인다", async () => {
    mockStatus(false);
    render(<ServerOfflineNotice />);
    const alert = await screen.findByRole("alert");
    expect(alert.textContent).toBe(OFFLINE_MESSAGE);
    expect(global.fetch).toHaveBeenCalledWith("/api/server-status", { cache: "no-store" });
  });

  it("서버가 켜져 있으면 아무것도 보이지 않는다", async () => {
    mockStatus(true);
    render(<ServerOfflineNotice />);
    await waitFor(() => expect(global.fetch).toHaveBeenCalled());
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("상태 확인 자체가 실패하면 안내를 띄우지 않는다", async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error("down")) as unknown as typeof fetch;
    render(<ServerOfflineNotice />);
    await waitFor(() => expect(global.fetch).toHaveBeenCalled());
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
