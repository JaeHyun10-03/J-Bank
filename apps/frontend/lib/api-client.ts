import axios, { AxiosError, InternalAxiosRequestConfig } from "axios";

/**
 * 모든 API 호출은 same-site 프록시(`/api/proxy`)를 거친다 — 백엔드 오리진과 직접
 * 통신하면 인증·갱신 쿠키를 브라우저가 받을 수 없다(인프라아키텍처 문서).
 */
export const apiClient = axios.create({
  baseURL: "/api/proxy/api/v1",
  withCredentials: true,
});

const MUTATING_METHODS = new Set(["post", "put", "patch", "delete"]);

function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : undefined;
}

apiClient.interceptors.request.use((config) => {
  const method = config.method?.toLowerCase();
  if (!method || !MUTATING_METHODS.has(method)) {
    return config;
  }

  // 화면플로우차트 문서: 폼 진입이 아니라 실제 제출 시점에 생성해야 한다. 재시도(401
  // 재발급 후 재전송)에서는 같은 설정 객체를 재사용하므로 이미 있으면 새로 만들지 않는다
  // — 그래야 재시도가 원래 요청과 같은 멱등키로 나간다.
  if (!config.headers.get("Idempotency-Key")) {
    config.headers.set("Idempotency-Key", crypto.randomUUID());
  }

  const csrfToken = readCookie("XSRF-TOKEN");
  if (csrfToken) {
    config.headers.set("X-CSRF-TOKEN", csrfToken);
  }

  return config;
});

let refreshPromise: Promise<unknown> | null = null;

function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = apiClient
      .post("/auth/refresh")
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

type RetryableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetryableConfig | undefined;
    // 로그인 자체가 401이면 자격증명이 틀린 것이지 인증 쿠키 만료가 아니다 — 재발급을
    // 시도하면 안 되고, 호출한 화면이 AUTH_001/AUTH_002로 직접 처리하게 그대로 흘려보낸다.
    const skipRefresh = config?.url === "/auth/refresh" || config?.url === "/auth/login";

    if (error.response?.status !== 401 || !config || config._retried || skipRefresh) {
      return Promise.reject(error);
    }

    // 여러 요청이 동시에 401을 받아도 재발급은 한 번만 나간다 — 갱신 토큰은 로테이션되므로
    // 각자 재발급을 시도하면 두 번째 이후는 이미 소비된 토큰을 제출해 탈취로 오인되어
    // 토큰 계열 전체가 폐기된다(화면플로우차트 문서 3절).
    config._retried = true;
    try {
      await refreshAccessToken();
      return apiClient(config);
    } catch (refreshError) {
      if (typeof window !== "undefined") {
        window.location.href = "/login";
      }
      return Promise.reject(refreshError);
    }
  },
);
