import {
  Settings,
  Search,
  Send,
  Smartphone,
  Calendar,
  ClipboardList,
  QrCode,
  Users,
  Wallet,
  Landmark,
  CirclePlus,
  EyeOff,
  ChevronRight,
} from "lucide-react";
import { MobileScreen } from "@/components/mobile-screen";
import { BottomTabBar } from "@/components/bottom-tab-bar";

// ponytail: 전체 메뉴 목록은 아직 도메인·API가 없어 피그마 시안 고정 텍스트로 채운다.
const MENU_SECTIONS = [
  {
    title: "이체",
    items: [
      { key: "account-transfer", name: "계좌송금", desc: "내 계좌·타행 이체", Icon: Send, bg: "bg-[#4262ff]" },
      { key: "simple-transfer", name: "간편송금", desc: "연락처로 바로 보내기", Icon: Smartphone, bg: "bg-[#5b7bff]" },
      { key: "scheduled-transfer", name: "예약·자동이체", desc: null, Icon: Calendar, bg: "bg-[#7c95ff]" },
      { key: "transfer-history", name: "이체결과·내역 조회", desc: null, Icon: ClipboardList, bg: "bg-[#9daeff]" },
    ],
  },
  {
    title: "출금",
    items: [
      { key: "atm-withdraw", name: "ATM 출금", desc: "카드 없이 QR 출금", Icon: QrCode, bg: "bg-[#00a66c]" },
      { key: "group-settle", name: "모임비 정산", desc: null, Icon: Users, bg: "bg-[#2bbe86]" },
    ],
  },
  {
    title: "계좌",
    items: [
      { key: "deposit-account", name: "입출금 계좌", desc: "생활통장·J박스", Icon: Wallet, bg: "bg-[#111114]" },
      { key: "savings-account", name: "예적금 계좌", desc: null, Icon: Landmark, bg: "bg-[#333338]" },
      { key: "open-account", name: "계좌 개설", desc: "새 통장 만들기", Icon: CirclePlus, bg: "bg-[#55555c]" },
      { key: "hidden-account", name: "숨김 계좌 관리", desc: null, Icon: EyeOff, bg: "bg-[#77777e]" },
    ],
  },
] as const;

export default function MenuPage() {
  return (
    <MobileScreen className="bg-[#f7f9fd]">
      <div className="flex w-full items-center justify-between bg-[#f7f9fd] px-[24px] pb-[16px] pt-[20px]">
        <p className="text-[24px] font-bold text-[#191f28]">전체</p>
        <Settings className="size-[24px] text-[#191f28]" strokeWidth={1.8} />
      </div>

      <div className="flex w-full items-start bg-white px-[16px] pb-[16px]">
        <div className="flex flex-1 items-center gap-[8px] rounded-[12px] bg-white px-[16px] py-[13px] shadow-[0_0_0_1px_#edf1f7]">
          <Search className="size-[18px] text-[#b0b8c4]" strokeWidth={1.8} />
          <p className="text-[15px] text-[#b0b8c4]">메뉴 검색</p>
        </div>
      </div>

      {MENU_SECTIONS.map((section) => (
        <div key={section.title} className="flex w-full flex-col bg-white px-[16px] pb-[12px]">
          <div className="flex items-start px-[8px] pb-[8px] pt-[4px]">
            <p className="text-[16px] font-bold text-[#191f28]">{section.title}</p>
          </div>
          <div className="flex w-full flex-col rounded-[16px] bg-white px-[18px] py-[6px] shadow-[0_0_0_1px_#edf1f7]">
            {section.items.map((item, i) => (
              <div
                key={item.key}
                className={
                  i > 0
                    ? "flex w-full items-center gap-[14px] border-t border-[#edf1f7] py-[15px]"
                    : "flex w-full items-center gap-[14px] py-[15px]"
                }
              >
                <div className={`flex size-[36px] shrink-0 items-center justify-center rounded-[10px] ${item.bg}`}>
                  <item.Icon className="size-[24px] text-white" strokeWidth={1.8} />
                </div>
                <div className="flex flex-1 flex-col gap-[2px]">
                  <p className="text-[15px] font-medium text-[#191f28]">{item.name}</p>
                  {item.desc ? <p className="text-[12px] text-[#8b95a5]">{item.desc}</p> : null}
                </div>
                <ChevronRight className="size-[18px] shrink-0 text-[#b0b8c4]" strokeWidth={2} />
              </div>
            ))}
          </div>
        </div>
      ))}

      <div className="h-[16px] w-full bg-white" />

      <div className="flex-1" />
      <BottomTabBar active="전체" />
    </MobileScreen>
  );
}
