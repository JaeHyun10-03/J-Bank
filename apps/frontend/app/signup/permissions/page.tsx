"use client";

import { useRouter } from "next/navigation";
import {
  Contact,
  Camera,
  Image as ImageIcon,
  ScanFace,
  Radar,
  Navigation,
  Mic,
  AudioLines,
} from "lucide-react";
import { MobileScreen } from "@/components/mobile-screen";

const PERMISSIONS = [
  {
    Icon: Contact,
    title: "연락처",
    desc: "기기에 저장된 주소록의 연락처로 송금 시 사용",
  },
  {
    Icon: Camera,
    title: "카메라",
    desc: "비대면 실명인증 시 신분증 촬영, 필수 서류 제출을 위해 사용",
  },
  {
    Icon: ImageIcon,
    title: "사진앨범",
    desc: "프로필에 사진 등록 및 필수 서류 제출, 이미지/사진 저장 시 사용",
  },
  {
    Icon: ScanFace,
    title: "Face ID",
    desc: "로그인, 이체 등 인증시 사용",
  },
  {
    Icon: Radar,
    title: "추적",
    desc: "맞춤 서비스/혜택 제공시 사용",
  },
  {
    Icon: Navigation,
    title: "위치",
    desc: "모바일 현장실사 서비스 제공 시 사용",
  },
  {
    Icon: Mic,
    title: "마이크",
    desc: "AI 어시스턴트 시 사용자의 음성을 녹음하기 위해 사용",
  },
  {
    Icon: AudioLines,
    title: "음성 인식",
    desc: "AI 어시스턴트 시 녹음된 음성을 텍스트로 변환하기 위해 사용",
  },
];

export default function SignupPermissionsPage() {
  const router = useRouter();

  return (
    <MobileScreen className="items-start pb-[28px] pt-[44px]">
      <div className="w-full px-[24px] text-[21px] font-bold leading-[31px] text-[#191f28]">
        <p>권한을 허용하여 더 안전하고,</p>
        <p>편리하게 제이뱅크를 이용하세요</p>
      </div>
      <div className="flex w-full flex-col gap-[18px] px-[24px] pt-[32px]">
        {PERMISSIONS.map(({ Icon, title, desc }) => (
          <div key={title} className="flex w-full items-start gap-[14px]">
            <div className="flex size-[44px] shrink-0 items-center justify-center rounded-[12px] bg-[#f7f8fb]">
              <div className="flex size-[30px] items-center justify-center rounded-[8px] bg-[#3350f5]">
                <Icon className="size-[18px] text-white" strokeWidth={2} />
              </div>
            </div>
            <div className="flex flex-1 flex-col gap-[3px] pt-[1px]">
              <div className="flex items-center gap-[6px]">
                <p className="text-[15px] font-bold text-[#191f28]">{title}</p>
                <p className="text-[12px] font-medium text-[#b0b8c1]">[선택]</p>
              </div>
              <p className="text-[13px] leading-[19px] text-[#8b95a1]">{desc}</p>
            </div>
          </div>
        ))}
        <div className="flex w-full items-start gap-[8px] pl-[2px] pt-[10px]">
          <span className="mt-[6px] size-[3px] shrink-0 rounded-full bg-[#b0b8c1]" />
          <p className="flex-1 text-[12px] leading-[19px] text-[#b0b8c1]">
            선택 항목은 필요한 시점에 동의 받고 있으며 동의하지 않아도 앱을 사용할 수 있습니다
          </p>
        </div>
      </div>
      <div className="min-h-px w-full flex-1" />
      <div className="w-full px-[24px] pt-[12px]">
        <button
          type="button"
          onClick={() => router.push("/signup/onboard")}
          className="flex w-full items-center justify-center rounded-[14px] bg-[#0414a7] py-[16px]"
        >
          <p className="text-[17px] font-bold text-white">확인</p>
        </button>
      </div>
    </MobileScreen>
  );
}
