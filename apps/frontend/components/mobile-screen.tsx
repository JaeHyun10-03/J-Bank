import { cn } from "@/lib/utils";

/** 피그마 393px 모바일 프레임 재현용 공통 래퍼. 인증 플로우·홈 화면에서 공유한다. */
export function MobileScreen({
  className,
  children,
}: {
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen w-full bg-[#edf1f7]">
      <div className={cn("mx-auto flex min-h-screen w-full max-w-[430px] flex-col bg-white", className)}>
        {children}
      </div>
    </div>
  );
}
