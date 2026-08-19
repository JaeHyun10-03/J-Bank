"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

const TERMS_LINKS = [
  "예금거래 기본약관",
  "적립식 예금약관",
  "J키즈 적금 특약",
  "계좌간 자동이체 약관",
  "비과세종합저축 특약",
  "J키즈적금 상품설명서",
];

function SectionHeading({ children }: { children: React.ReactNode }) {
  return (
    <p className="mt-[26px] w-full text-[17px] font-extrabold leading-[26px] text-[#191f28] first:mt-0">
      {children}
    </p>
  );
}

function Li({ children, dash }: { children: React.ReactNode; dash?: boolean }) {
  return (
    <div
      className={`flex w-full items-start gap-[6px] py-[4px] text-[15px] leading-[25px] text-[#4e5968] ${dash ? "pl-[18px]" : ""}`}
    >
      <span className="w-[10px] shrink-0 text-center">{dash ? "–" : "·"}</span>
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}

function InfoTable({
  head,
  rows,
  widths,
}: {
  head: (string | string[])[];
  rows: (string | string[])[][];
  widths: number[];
}) {
  return (
    <div className="mt-[10px] flex w-full flex-col border-y-[1.5px] border-[#333d4b]">
      <div className="flex w-full bg-[#eff2f6]">
        {head.map((cell, i) => (
          <div
            key={i}
            style={{ flexGrow: widths[i] }}
            className={`flex flex-col items-center justify-center px-[6px] py-[13px] text-center text-[14px] font-medium text-[#191f28] ${i > 0 ? "border-l border-[#e9edf3]" : ""}`}
          >
            {(Array.isArray(cell) ? cell : [cell]).map((line, j) => (
              <p key={j} className="leading-[22px]">
                {line}
              </p>
            ))}
          </div>
        ))}
      </div>
      {rows.map((row, ri) => (
        <div
          key={ri}
          className={`flex w-full bg-white ${ri < rows.length - 1 ? "border-b border-[#e9edf3]" : ""}`}
        >
          {row.map((cell, ci) => (
            <div
              key={ci}
              style={{ flexGrow: widths[ci] }}
              className={`flex flex-col items-center justify-center px-[6px] py-[13px] text-center text-[14px] text-[#4e5968] ${ci > 0 ? "border-l border-[#e9edf3]" : ""}`}
            >
              {(Array.isArray(cell) ? cell : [cell]).map((line, j) => (
                <p key={j} className="leading-[22px]">
                  {line}
                </p>
              ))}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

function AccordionSection({
  title,
  open,
  onToggle,
  children,
}: {
  title: string;
  open: boolean;
  onToggle: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="flex w-full flex-col items-start">
      <button type="button" onClick={onToggle} className="flex h-[56px] w-full items-center justify-between">
        <p className="text-[16px] font-bold text-[#191f28]">{title}</p>
        <img
          alt=""
          className="size-[22px]"
          src={`/products/j-kids/icon-chevron-${open ? "up" : "down"}.svg`}
        />
      </button>
      {open ? (
        <div className="-mx-[20px] flex w-[calc(100%+40px)] flex-col items-start bg-[#f7f8fb] px-[20px] pb-[28px] pt-[6px]">
          {children}
        </div>
      ) : null}
    </div>
  );
}

export function JKidsDetail() {
  const router = useRouter();
  const [openRows, setOpenRows] = useState<Record<string, boolean>>({});

  function toggleRow(row: string) {
    setOpenRows((prev) => ({ ...prev, [row]: !prev[row] }));
  }

  return (
    <div className="flex w-full flex-col items-center bg-white pb-[100px]">
      <div className="flex h-[56px] w-full items-center px-[12px]">
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="뒤로가기"
          className="block size-[26px]"
        >
          <img alt="" className="size-full" src="/products/j-kids/back.svg" />
        </button>
      </div>

      <div className="flex w-full flex-col items-center px-[20px] pt-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">우리 아이 미래 준비</p>
        <p className="mt-[14px] text-center text-[26px] font-extrabold leading-[36px] text-[#191f28]">
          든든하게 모아주는
        </p>
        <p className="text-center text-[26px] font-extrabold leading-[36px] text-[#4262ff]">
          J키즈 적금
        </p>

        <div className="relative mt-[34px] h-[190px] w-[240px]">
          <div className="absolute left-[18px] top-[22px] h-[64px] w-[96px] rounded-[12px] bg-[#7b9cff]" />
          <div className="absolute left-[30px] top-[66px] h-[7px] w-[26px] rounded-[4px] bg-[#d6e2ff]" />
          <img alt="" className="absolute left-[62px] top-[52px] h-[112px] w-[118px]" src="/products/j-kids/ellipse-1.svg" />
          <img alt="" className="absolute left-[76px] top-[44px] h-[58px] w-[90px]" src="/products/j-kids/ellipse-2.svg" />
          <img alt="" className="absolute left-[140px] top-[100px] size-[11px]" src="/products/j-kids/ellipse-3.svg" />
          <img alt="" className="absolute left-[104px] top-[118px] h-[24px] w-[30px]" src="/products/j-kids/ellipse-4.svg" />
          <img alt="" className="absolute left-[150px] top-[112px] h-[72px] w-[78px]" src="/products/j-kids/vector-1.svg" />
          <p className="absolute left-[176px] top-[126px] text-[30px] font-extrabold text-[#b57be8]">₩</p>
          <img alt="" className="absolute left-[24px] top-[118px] size-[11px]" src="/products/j-kids/ellipse-5.svg" />
          <img alt="" className="absolute left-[44px] top-[146px] size-[9px]" src="/products/j-kids/ellipse-6.svg" />
          <div className="absolute left-[198px] top-[56px] flex size-[16px] rotate-45 items-center justify-center">
            <div className="size-[11px] bg-[#f5a623]" />
          </div>
        </div>

        <div className="mt-[40px] flex w-full items-start">
          <div className="flex flex-1 flex-col items-center gap-[6px] text-center">
            <p className="text-[13px] text-[#8b95a1]">금리(5년,세전)</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">최고 연 8.50%</p>
            <p className="text-[13px] text-[#8b95a1]">기본 연 3.50%</p>
          </div>
          <div className="h-[58px] w-px shrink-0 bg-[#e9edf3]" />
          <div className="flex flex-1 flex-col items-center gap-[6px] text-center">
            <p className="text-[13px] text-[#8b95a1]">납입금</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">월 최대 30만원</p>
            <p className="text-[13px] text-[#8b95a1]">1~5년</p>
          </div>
        </div>
      </div>

      <div className="mt-[44px] flex w-full flex-col items-center bg-[#f7f8fb] px-[20px] py-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">만기 때마다 고민하지 않아도</p>
        <p className="mt-[12px] text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          만 17세 전까지
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          자동으로 재가입할 수 있어요
        </p>
        <div className="relative mt-[34px] h-[210px] w-[150px]">
          <div className="absolute left-[30px] top-0 h-[52px] w-[90px] rounded-[12px] bg-[#d8e3ff] opacity-50" />
          <div className="absolute left-[42px] top-[34px] h-[6px] w-[24px] rounded-[3px] bg-white opacity-[.43]" />
          <div className="absolute left-[18px] top-[52px] h-[66px] w-[114px] rounded-[12px] bg-[#7b9cff]" />
          <div className="absolute left-[30px] top-[100px] h-[6px] w-[24px] rounded-[3px] bg-white opacity-85" />
          <div className="absolute left-[22px] top-[124px] h-[62px] w-[106px] rounded-[12px] bg-[#a8bef5]" />
          <div className="absolute left-[34px] top-[168px] h-[6px] w-[24px] rounded-[3px] bg-white opacity-85" />
          <div className="absolute left-[34px] top-[196px] h-[48px] w-[82px] rounded-[12px] bg-[#dde7ff] opacity-45" />
          <div className="absolute left-[46px] top-[226px] h-[6px] w-[24px] rounded-[3px] bg-white opacity-[.38]" />
        </div>
      </div>

      <div className="flex w-full flex-col items-center px-[20px] py-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">쉽고 간편한 우대금리</p>
        <p className="mt-[12px] text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          매월 입금만
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          성공하면 우대금리 드려요
        </p>
        <div className="relative mt-[30px] h-[200px] w-[220px]">
          <div className="absolute left-[62px] top-[112px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <div className="absolute left-[66px] top-[129px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <div className="absolute left-[62px] top-[146px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <div className="absolute left-[66px] top-[163px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <div className="absolute left-[62px] top-[180px] h-[13px] w-[96px] rounded-[6px] bg-[#fce38a]" />
          <img alt="" className="absolute left-[76px] top-[40px] size-[74px]" src="/products/j-kids/ellipse-7.svg" />
          <p className="absolute left-[101px] top-[59px] text-[30px] font-extrabold text-[#e0a21b]">₩</p>
          <img alt="" className="absolute left-[24px] top-[88px] h-[52px] w-[22px]" src="/products/j-kids/vector-2.svg" />
          <img alt="" className="absolute left-0 top-[132px] h-[44px] w-[22px]" src="/products/j-kids/vector-3.svg" />
          <img alt="" className="absolute left-[186px] top-[120px] h-[44px] w-[22px]" src="/products/j-kids/vector-4.svg" />
        </div>
      </div>

      <div className="flex w-full flex-col items-center bg-[#f7f8fb] px-[20px] py-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">함께 관리하는 자녀 적금</p>
        <p className="mt-[12px] text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          엄마/아빠 한 명이 만들어도
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          같이 볼 수 있어요
        </p>
        <img alt="" className="mt-[34px] h-[190px] w-[230px]" src="/products/j-kids/illo-phones.svg" />
      </div>

      <div className="flex w-full flex-col items-start px-[20px] pt-[28px]">
        <p className="text-[14px] font-bold text-[#191f28]">상품 안내</p>
        <div className="mt-[10px] flex w-full flex-col">
          <AccordionSection
            title="상품 상세"
            open={!!openRows["상품 상세"]}
            onToggle={() => toggleRow("상품 상세")}
          >
            <SectionHeading>상품요약</SectionHeading>
            <Li>상품종류: 목돈모으기(적립식예금)</Li>
            <Li>가입대상: J키즈 서비스를 이용중인 만 17세 미만의 실명의 개인(1인 1계좌)</Li>
            <Li>
              <p>
                가입방법 : J키즈서비스 가입을 통해 법정대리인으로 확인된 부 또는 모가 J키즈 통장을
                근거계좌로 하여 예금주 본인을 대리하여 가입
              </p>
              <p>(J키즈 서비스는 J키즈 통장 가입 시 자동으로 가입되며, 통장을 가입하지 않은 부모는 자녀 연결을 통해 가입 가능)</p>
            </Li>
            <Li>가입기간: 1년, 2년, 3년, 4년, 5년</Li>
            <Li>가입금액: 0원</Li>
            <Li>적립한도: 월 최대 30만원(원단위, 자유적립식)</Li>

            <SectionHeading>세제혜택</SectionHeading>
            <Li>본인 한도 내 비과세종합저축으로 가입 가능</Li>

            <SectionHeading>이자지급방법</SectionHeading>
            <Li>만기일시지급식</Li>

            <SectionHeading>이자계산방법</SectionHeading>
            <Li>적립 건별로 입금일부터 해지일 전일까지의 입금 기간에 대하여 약정이율로 셈한 후 합산</Li>
            <div className="mt-[10px] w-full text-[14px] leading-[25px] text-[#191f28]">
              <p>※ 5년 동안 매월 30만원씩 납입 시, 세전 3,888,994원 이자 수령</p>
              <p>연 8.5%(기본금리 3.5% + 실적우대금리 4.0%+쿠폰금리 1.0%)적용 시(26.06.19.기준)</p>
            </div>

            <SectionHeading>부분인출</SectionHeading>
            <Li>
              <p>2회 가능, 후입선출법에 따라 중도해지금리 적용</p>
              <p>단, 최소 1만원이상 잔액 유지 필요하며 비과세종합저축으로 가입한 금액은 부분인출 불가능</p>
            </Li>

            <SectionHeading>만기 해지 및 자동 재가입</SectionHeading>
            <Li>만기일 전일까지 자동 해지, 직접해지, 자동 재가입 중 선택 가능</Li>
            <Li>자동 해지를 신청할 경우 만기일 당일(휴일 및 공휴일 포함)에 자동 해지되어 연결계좌로 입금</Li>
            <Li>
              <p>
                직접 해지를 신청할 경우 &apos;J키즈서비스&apos;에 가입되어 있는 예금주의 부모가 이 예금을
                직접 해지하며, 해지 원리금 입금 계좌는 예금주의 제이뱅크 통장만 선택 가능.
              </p>
              <p>단, 예금주의 &apos;J키즈 통장&apos;을 다른 입출금통장으로 교체했을 경우 예금주 본인만 이 예금 직접 해지 가능</p>
            </Li>
            <Li>
              자동 재가입을 신청할 경우 1회차 입금 금액과 동일한 금액으로 첫 입금하여 최초 가입 기간과
              동일한 기간으로 예금주가 만 17세가 되는 직전일까지 자동 재가입되며 자동 재가입 될 경우,
              자동 재가입 신규일 당일 제이뱅크 홈페이지 및 앱에 고시된 금리 적용
            </Li>
            <Li>
              자동 재가입 시 자동이체 연장을 신청한 경우 만기 직전 자동이체 주기(월, 주, 일) 이내 실행된
              자동이체 금액, 주기, 이체일과 동일한 조건으로 연장 (단, 자동이체 출금통장이 본인(예금주)명의의
              제이뱅크 입출금 통장일 경우에 한함)
            </Li>
          </AccordionSection>

          <AccordionSection
            title="금리안내"
            open={!!openRows["금리안내"]}
            onToggle={() => toggleRow("금리안내")}
          >
            <SectionHeading>적용금리</SectionHeading>
            <Li>기본금리 + 실적우대금리 + 금리쿠폰</Li>
            <p className="mt-[10px] w-full text-right text-[13px] leading-[20px] text-[#8b95a1]">
              (조회기준일: 2026.07.25, 현재, 세전, 연%)
            </p>
            <InfoTable
              widths={[96, 96, 161]}
              head={["가입기간", "기본금리", ["최고금리 1)", "(실적우대금리+금리우대쿠폰)"]]}
              rows={[
                ["1년", "3.00%", "8.0%"],
                ["2년", "3.00%", "8.0%"],
                ["3년", "3.30%", "8.3%"],
                ["4년", "3.30%", "8.3%"],
                ["5년", "3.50%", "8.5%"],
              ]}
            />
            <p className="mt-[12px] w-full text-[14px] leading-[25px] text-[#4e5968]">
              1) J키즈 출시 기념 이벤트를(26.06.18~07.31) 통해 지급 받은 금리 쿠폰을 적용한 최고금리
            </p>

            <SectionHeading>이벤트</SectionHeading>
            <Li>이벤트명 : J키즈 출시 기념 이벤트</Li>
            <Li>
              <p>이벤트 기간 : 2026.06.18(목) ~ 2026.07.31(금)</p>
              <p>단, 금리쿠폰은 선착순 50만좌 한정으로, 쿠폰 소진시 이벤트는 조기 종료 가능</p>
            </Li>
            <Li>이벤트 주요 내용 : 이벤트에 참여할 경우 J키즈적금 금리쿠폰 1% 제공</Li>
            <Li dash>대상고객 : 이벤트 페이지에서 쿠폰받기를 누른 고객</Li>
            <Li dash>적용방법 : 자녀 명의의 J키즈적금 가입시에 쿠폰을 선택하여 적용 가능</Li>
            <Li dash>적용기간 : 전 기간 (1년부터 5년까지)</Li>
            <Li dash>사용가능기간 : 금리쿠폰 발급일로부터 7일 이내에 가입 가능</Li>
            <Li>유의사항</Li>
            <Li dash>금리쿠폰은 1계좌당 1개만 적용 가능하며, 중도해지 후 재가입 시 쿠폰 추가 발급 및 재사용 불가</Li>
            <Li dash>자녀명의의 J키즈통장이 없는 경우, 금리우대쿠폰을 발급 받았더라도 상품 가입이 제한될 수 있음</Li>
            <Li dash>이미 사용한 쿠폰은 취소 불가</Li>
            <Li dash>이미 사용한 쿠폰은 다른 계좌로 적용 변경은 불가</Li>
            <Li dash>유효기간 내 쿠폰 미사용시 쿠폰 자동 소멸 및 유효기간 연장 불가</Li>
            <Li dash>금리쿠폰은 본인만 사용 가능하며, 타인에게 양도불가</Li>
            <Li dash>시스템 점검 시간에는 쿠폰 사용이 제한될 수 있음</Li>

            <SectionHeading>우대금리</SectionHeading>
            <Li>실적 우대금리 : 전체 계약 월 수의 2/3이상 해당되는 개월 수 동안 납입한 경우 만기 해지 계좌에 한해 연 4.00% 적용</Li>
            <Li>금리쿠폰 : 이벤트 등을 통해 금리쿠폰을 제공받아 적용한 경우에 한하여 만기 해지 시 적용</Li>
            <p className="mt-[10px] w-full text-[14px] leading-[25px] text-[#4e5968]">
              ※ 금리쿠폰의 자세한 내용은 쿠폰함 내 금리쿠폰 상세 정보에서 확인 필요
            </p>

            <SectionHeading>중도해지금리</SectionHeading>
            <InfoTable
              widths={[110, 243]}
              head={["보유기간", "적용금리"]}
              rows={[
                ["30일 미만", "연 0.10%"],
                ["30일 이상", "연 0.30%"],
                ["90일 이상", "연 0.50%"],
                ["180일 이상", ["기본금리 2) x 70% x 경과일수/계약일수", "(최저 연 0.5%)"]],
                ["270일 이상", ["기본금리 2) x 80% x 경과일수/계약일수", "(최저 연 0.5%)"]],
                ["330일 이상", ["기본금리 2) x 90% x 경과일수/계약일수", "(최저 연 0.5%)"]],
              ]}
            />
            <p className="mt-[12px] w-full text-[14px] leading-[25px] text-[#4e5968]">2) 기본금리: 신규 시 기본금리 적용</p>
            <p className="mt-[8px] w-full text-[14px] leading-[25px] text-[#4e5968]">
              ※ 중도해지 시 각 입금 건의 보유기간에 따라 가입일(또는 재가입일) 당시 제이뱅크 인터넷 홈페이지와
              모바일 앱에 고시한 중도해지 금리가 적용
            </p>

            <SectionHeading>만기 후 금리</SectionHeading>
            <InfoTable
              widths={[176, 177]}
              head={["만기 후 경과기간", "적용금리"]}
              rows={[
                ["1개월 이내", "만기시점 기본금리 x 50%"],
                [["만기 후 1개월 초과 ~", "6개월 이내"], "만기시점 기본금리 x 30%"],
                ["만기 후 6개월 초과", "연 0.20%"],
              ]}
            />
            <p className="mt-[12px] w-full text-[14px] leading-[25px] text-[#4e5968]">
              ※ 만기 후 해지 시 만기일 당시 제이뱅크 인터넷 홈페이지와 모바일 앱에 고시된 만기 후 금리가 적용
            </p>
          </AccordionSection>

          <AccordionSection
            title="이용안내"
            open={!!openRows["이용안내"]}
            onToggle={() => toggleRow("이용안내")}
          >
            <SectionHeading>예금자보호</SectionHeading>
            <div className="mt-[10px] flex items-center gap-[6px] rounded-[8px] border border-[#d8dee7] bg-white px-[12px] py-[8px]">
              <img alt="" className="size-[16px]" src="/products/j-kids/badge-dot.svg" />
              <p className="whitespace-nowrap text-[13px] font-bold leading-[18px] text-[#4262ff]">보호금융상품</p>
            </div>
            <p className="mt-[12px] w-full text-[15px] leading-[25px] text-[#4e5968]">
              이 예금은 예금자보호법에 따라 원금과 소정의 이자를 합하여 1인당 &quot;1억원까지&quot; (본 은행의
              여타 보호상품과 합산) 보호됩니다.
            </p>

            <SectionHeading>유의사항</SectionHeading>
            <Li>이 금융상품을 가입하시기 전에 상품설명서 및 약관을 읽어보시기 바랍니다.</Li>
            <Li>이 금융상품을 가입하시는 경우 금융소비자보호법 제 19조 1항에 따라 상품에 관한 중요한 사항을 설명 받으실 수 있습니다.</Li>
            <Li>이 안내는 법령 및 내부통제기준에 따른 절차를 거쳐 제공됩니다.</Li>
            <Li>예금잔액증명서 발급 당일에는 입금, 출금, 이체 등 잔액 변동이 불가합니다.</Li>
            <Li>압류, 가압류, 질권설정 등이 등록될 경우 예금의 원금 및 이자 지급이 제한됩니다.</Li>
            <Li>
              이 예금은 양도 및 상속에 의한 명의변경이 불가하며, 상속에 의한 해지만 가능합니다. 상속에
              의하여 예금계약이 해지된 경우에는 계약일 당시 약정한 기본금리로 셈한 이자를 원금에 더하여
              지급합니다. 단, 해지시점에 우대금리 조건이 충족된 경우엔 우대금리를 더하여 해지합니다.
            </Li>
          </AccordionSection>

          <AccordionSection
            title="약관 및 상품설명서"
            open={!!openRows["약관 및 상품설명서"]}
            onToggle={() => toggleRow("약관 및 상품설명서")}
          >
            {TERMS_LINKS.map((label) => (
              <div key={label} className="flex h-[52px] w-full items-center justify-between">
                <p className="text-[15px] text-[#4e5968]">{label}</p>
                <img alt="" className="size-[18px]" src="/products/j-kids/icon-chevron-right.svg" />
              </div>
            ))}
          </AccordionSection>
        </div>
        <div className="mt-[22px] text-[13px] leading-[22px] text-[#8b95a1]">
          <p>2026.06.16 준법감시인 심의필 2026-1179</p>
          <p>(유효기간 2026.07.31.)</p>
        </div>
        <div className="mt-[26px] flex w-full items-start">
          <div className="flex flex-1 flex-col items-center gap-[8px] py-[6px]">
            <img alt="" className="size-[28px]" src="/products/j-kids/icon-share.svg" />
            <p className="text-[14px] font-bold text-[#191f28]">공유하기</p>
          </div>
          <div className="h-[44px] w-px shrink-0 bg-[#eff2f7]" />
          <div className="flex flex-1 flex-col items-center gap-[8px] py-[6px]">
            <img alt="" className="size-[28px]" src="/products/j-kids/icon-phone.svg" />
            <p className="text-[14px] font-bold text-[#191f28]">전화상담</p>
          </div>
          <div className="h-[44px] w-px shrink-0 bg-[#eff2f7]" />
          <div className="flex flex-1 flex-col items-center gap-[8px] py-[6px]">
            <img alt="" className="size-[28px]" src="/products/j-kids/icon-chat.svg" />
            <p className="text-[14px] font-bold text-[#191f28]">톡상담</p>
          </div>
        </div>
      </div>

      <div className="fixed bottom-0 left-1/2 w-full max-w-[430px] -translate-x-1/2 bg-white px-[20px] pb-[24px] pt-[8px]">
        <button
          type="button"
          className="flex h-[56px] w-full items-center justify-center rounded-[14px] bg-[#0114a7]"
        >
          <p className="text-[17px] font-bold text-white">적금 만들기</p>
        </button>
      </div>
    </div>
  );
}
