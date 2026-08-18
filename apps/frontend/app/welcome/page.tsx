import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { MobileScreen } from "@/components/mobile-screen";

export default function WelcomePage() {
  return (
    <MobileScreen className="items-start px-[24px] pb-[32px] pt-[170px]">
      <div className="flex w-full items-center justify-center">
        <p className="text-[34px] font-extrabold leading-[40px] tracking-[-1.02px] text-[#17008c]">
          J-Bank
        </p>
      </div>
      <div className="min-h-px w-full flex-1" />
      <Link
        href="/signup/permissions"
        className="flex w-full items-center justify-center rounded-[14px] bg-[#0414a7] py-[16px]"
      >
        <p className="text-[17px] font-bold leading-[24px] text-white">제이뱅크 시작하기</p>
      </Link>
      <Link
        href="/login"
        className="flex w-full items-center justify-center gap-[4px] pt-[20px]"
      >
        <p className="text-[14px] font-medium leading-[20px] text-[#6b7684]">
          이미 계좌가 있으신가요?
        </p>
        <ChevronRight className="size-[12px] text-[#6b7684]" strokeWidth={2.5} />
      </Link>
    </MobileScreen>
  );
}
