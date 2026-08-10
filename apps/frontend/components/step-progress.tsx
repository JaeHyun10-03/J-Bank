/** 회원가입 단계 화면 상단에 붙는 진행률 바. step은 1부터 시작하는 현재 단계. */
export function StepProgress({ step, total }: { step: number; total: number }) {
  return (
    <div className="flex w-full gap-[4px] px-[24px] pt-[14px]">
      {Array.from({ length: total }).map((_, i) => (
        <div key={i} className="h-[4px] flex-1 overflow-hidden rounded-full bg-[#e5e8ef]">
          <div
            className="h-full rounded-full bg-[#0414a7] transition-all"
            style={{ width: i < step ? "100%" : "0%" }}
          />
        </div>
      ))}
    </div>
  );
}
