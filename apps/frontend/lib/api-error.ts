import { AxiosError } from "axios";
import type { components } from "@/types/api";

type ErrorDetail = components["schemas"]["ErrorDetail"];

/** API설계 2.7절 공통 응답의 error.code/message를 axios 에러에서 꺼낸다. */
export function getApiError(error: unknown): ErrorDetail | undefined {
  if (error instanceof AxiosError) {
    return error.response?.data?.error;
  }
  return undefined;
}
