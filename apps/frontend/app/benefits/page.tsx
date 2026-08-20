import { HelpCircle, Gift, Ticket, Percent, Globe, ChevronRight } from "lucide-react";
import { MobileScreen } from "@/components/mobile-screen";
import { BottomTabBar } from "@/components/bottom-tab-bar";

// ponytail: 리워드/인기혜택 목록은 아직 도메인·API가 없어 피그마 시안 고정 텍스트로 채운다.
const REWARD_CARDS = [
  {
    key: "deposit",
    name: "입출금리워드",
    desc: "최대 현금 1천원",
    cta: "시작하기",
    from: "from-[#c7b9f5]",
    to: "to-[#8e6fe8]",
    chip: "bg-[#4a7dff]",
  },
  {
    key: "money-tree",
    name: "돈나무키우기",
    desc: "매일 현금이 자라나요",
    cta: "시작하기",
    from: "from-[#b9e8b0]",
    to: "to-[#4cb86b]",
    chip: "bg-[#2e7d46]",
  },
  {
    key: "weekly-invest",
    name: "주간 투자왕",
    desc: "주식 뽑고 상금 도전",
    cta: "오늘의 투자왕",
    from: "from-[#f5b9dc]",
    to: "to-[#d958a8]",
    chip: "bg-[#8e2e6b]",
  },
] as const;

const POPULAR_BENEFITS = [
  {
    key: "quiz",
    title: "정답 맞히면 총 상금 최대 1천만원",
    desc: "오늘의 픽 여행지 퀴즈",
    Icon: HelpCircle,
    color: "text-[#00b3a6]",
  },
  {
    key: "capsule",
    title: "캡슐 뽑기 이벤트",
    desc: "황금 캡슐 뽑으면 최대 50,000원 받아요",
    Icon: Gift,
    color: "text-[#ff9f43]",
  },
  {
    key: "coupon",
    title: "컬리 최대 1만 2천원 쿠폰 받기",
    desc: "지금 바로 쿠폰을 받을 수 있어요",
    Icon: Ticket,
    color: "text-[#4262ff]",
  },
  {
    key: "rate-coupon",
    title: "금리쿠폰 받고 100만원의 주인공이…",
    desc: "코드K 정기예금 금리쿠폰 이벤트",
    Icon: Percent,
    color: "text-[#d958a8]",
  },
  {
    key: "remit",
    title: "해외송금 수수료 없이 보내고",
    desc: "최대 12만원의 혜택도 받으세요",
    Icon: Globe,
    color: "text-[#14b8a6]",
  },
] as const;

export default function BenefitsPage() {
  return (
    <MobileScreen className="bg-[#edf1f7]">
      <div className="flex w-full items-center justify-between bg-white px-[24px] pb-[16px] pt-[20px]">
        <p className="text-[24px] font-bold text-[#191f28]">혜택</p>
        <div className="flex items-center gap-[14px]">
          <div className="flex items-center gap-[5px]">
            <span className="size-[20px] rounded-full bg-[#4262ff]" />
            <p className="text-[16px] font-bold text-[#191f28]">1원</p>
          </div>
          <div className="flex items-center gap-[5px]">
            <span className="h-[16px] w-[20px] rounded-[4px] bg-[#f5c542]" />
            <p className="text-[16px] font-bold text-[#191f28]">0개</p>
          </div>
        </div>
      </div>

      <div className="flex w-full flex-col gap-[3px] bg-white px-[24px] pb-[16px] pt-[8px]">
        <p className="text-[15px] font-semibold text-[#7b80f0]">매일매일 돈 되는 습관</p>
        <p className="text-[22px] font-bold text-[#191f28]">다양한 리워드를 만나보세요</p>
      </div>

      <div className="flex w-full gap-[14px] overflow-x-auto bg-white px-[24px] pb-[24px]">
        {REWARD_CARDS.map((card) => (
          <div
            key={card.key}
            className="flex w-[185px] shrink-0 flex-col overflow-hidden rounded-[20px]"
          >
            <div className={`relative h-[120px] w-full bg-gradient-to-b ${card.from} ${card.to}`}>
              <div className="absolute left-[48px] top-[22px] size-[58px] rotate-12 rounded-[8px] bg-white opacity-85" />
              <div className={`absolute left-[82px] top-[22px] size-[58px] -rotate-8 rounded-[8px] ${card.chip}`} />
            </div>
            <div className="flex flex-col items-center gap-[5px] px-[14px] py-[16px]">
              <p className="text-[17px] font-bold text-[#191f28]">{card.name}</p>
              <p className="text-[13px] text-[#8b95a5]">{card.desc}</p>
              <div className="mt-[6px] flex w-full items-center justify-center rounded-[10px] bg-[#eaf0fe] py-[11px]">
                <p className="text-[14px] font-semibold text-[#4262ff]">{card.cta}</p>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="flex w-full flex-col gap-[3px] bg-white px-[24px] pb-[12px] pt-[8px]">
        <p className="text-[15px] font-semibold text-[#7b80f0]">제이뱅크 인기혜택</p>
        <p className="text-[22px] font-bold text-[#191f28]">많은 사람들이 보고 있어요</p>
      </div>

      <div className="flex w-full flex-col bg-white px-[16px] pb-[24px]">
        <div className="flex w-full flex-col rounded-[20px] bg-white px-[20px] py-[8px]">
          {POPULAR_BENEFITS.map((item, i) => (
            <div
              key={item.key}
              className={
                i > 0
                  ? "flex w-full items-center gap-[14px] border-t border-[#edf1f7] py-[16px]"
                  : "flex w-full items-center gap-[14px] py-[16px]"
              }
            >
              <div className="flex size-[44px] shrink-0 items-center justify-center rounded-full bg-[#f2f3f5]">
                <item.Icon className={`size-[24px] ${item.color}`} strokeWidth={1.8} />
              </div>
              <div className="flex flex-1 flex-col gap-[3px]">
                <p className="text-[16px] font-bold text-[#191f28]">{item.title}</p>
                <p className="text-[14px] text-[#8b95a5]">{item.desc}</p>
              </div>
              <ChevronRight className="size-[18px] shrink-0 text-[#b0b8c4]" strokeWidth={2} />
            </div>
          ))}
        </div>
      </div>

      <div className="flex-1" />
      <BottomTabBar active="혜택" />
    </MobileScreen>
  );
}
