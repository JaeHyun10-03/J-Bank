import { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { apiClient } from "./api-client";
import { useServerStatus } from "./server-status";

function failWith(status: number | undefined) {
  apiClient.defaults.adapter = async (config: InternalAxiosRequestConfig) => {
    const response =
      status === undefined ? undefined : { data: {}, status, statusText: "", headers: {}, config };
    throw new AxiosError("fail", status === undefined ? "ERR_NETWORK" : "ERR_BAD_RESPONSE", config, null, response);
  };
}

let check: jest.Mock;
beforeEach(() => {
  check = jest.fn().mockResolvedValue(undefined);
  useServerStatus.setState({ check });
});

describe("API 실패 뒤 서버 상태 확인", () => {
  it.each([undefined, 502, 503, 504])("실패(%s)면 상태를 확인하고 오류는 그대로 전달한다", async (status) => {
    failWith(status);
    await expect(apiClient.get("/accounts")).rejects.toBeInstanceOf(AxiosError);
    expect(check).toHaveBeenCalledTimes(1);
  });

  it.each([400, 404, 500])("백엔드가 응답한 %i는 상태 확인을 하지 않는다", async (status) => {
    failWith(status);
    await expect(apiClient.get("/accounts")).rejects.toBeInstanceOf(AxiosError);
    expect(check).not.toHaveBeenCalled();
  });
});
