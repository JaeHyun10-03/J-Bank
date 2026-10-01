import { resolveDomainError } from "./domain-error-map";

describe("해지 거절 문구", () => {
  it("처리 중인 입금이 있으면 잠시 후 다시 시도하라고 안내한다", () => {
    expect(resolveDomainError({ code: "ACC_012_PENDING_CREDIT_EXISTS", message: "서버 문구" }).message).toBe(
      "처리 중인 입금이 있습니다. 잠시 후 다시 시도해주세요.",
    );
  });
});
