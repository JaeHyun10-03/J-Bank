"use client";

import { useRouter } from "next/navigation";

const ACCORDION_ROWS = ["상품상세", "금리안내", "이용안내", "약관 및 상품설명서"];

export function JFarmDetail() {
  const router = useRouter();

  return (
    <div className="flex w-full flex-col items-center bg-white pb-[100px]">
      <div className="flex h-[56px] w-full items-center px-[14px]">
        <button
          type="button"
          onClick={() => router.back()}
          aria-label="뒤로가기"
          className="block size-[26px]"
        >
          <img alt="" className="size-full" src="/products/j-farm/icon-chevron-left.svg" />
        </button>
        <p className="flex-1 text-center text-[17px] font-bold text-[#191f28]">J팜 농장</p>
        <div className="size-[26px]" />
      </div>

      <div className="flex w-full flex-col items-center px-[20px] pt-[52px]">
        <p className="text-center text-[15px] font-medium text-[#8b95a1]">
          이자 수확하고, 다시 심어 내 돈을 굴려요
        </p>
        <p className="mt-[14px] text-center text-[26px] font-extrabold leading-[36px] text-[#191f28]">
          매달 이자가 굴러오는 적금
        </p>
        <p className="text-center text-[26px] font-extrabold leading-[36px] text-[#4262ff]">
          J팜 농장
        </p>

        <div className="relative mt-[34px] h-[250px] w-[300px]">
          <img alt="" className="absolute left-[20px] top-[100px] h-[140px] w-[260px]" src="/products/j-farm/ellipse-1.svg" />
          <img alt="" className="absolute left-[45px] top-[130px] h-[100px] w-[210px]" src="/products/j-farm/ellipse-2.svg" />
          <img alt="" className="absolute left-[124px] top-[10px] size-[52px]" src="/products/j-farm/ellipse-3.svg" />
          <img alt="" className="absolute left-[131px] top-[17px] size-[38px]" src="/products/j-farm/ellipse-4.svg" />
          <p className="absolute left-[143px] top-[24px] text-[20px] font-extrabold text-[#c79217]">₩</p>
          <img alt="" className="absolute left-[105px] top-[80px] h-[105px] w-[90px]" src="/products/j-farm/ellipse-5.svg" />
          <img alt="" className="absolute left-[118px] top-[88px] h-[70px] w-[58px]" src="/products/j-farm/ellipse-6.svg" />
          <div className="absolute left-[120px] top-[66px] h-[22px] w-[60px] rounded-[8px] bg-[#d98e14]" />
          <img alt="" className="absolute left-[42px] top-[120px] h-[52px] w-[58px]" src="/products/j-farm/ellipse-7.svg" />
          <img alt="" className="absolute left-[48px] top-[152px] h-[44px] w-[46px]" src="/products/j-farm/ellipse-8.svg" />
          <div className="absolute left-[55px] top-[190px] h-[16px] w-[10px] rounded-[4px] bg-[#3f8e2e]" />
          <div className="absolute left-[76px] top-[190px] h-[16px] w-[10px] rounded-[4px] bg-[#3f8e2e]" />
          <img alt="" className="absolute left-[112px] top-[150px] h-[72px] w-[76px]" src="/products/j-farm/ellipse-9.svg" />
          <img alt="" className="absolute left-[130px] top-[158px] size-[40px]" src="/products/j-farm/ellipse-10.svg" />
          <div className="absolute left-[131px] top-[216px] h-[18px] w-[11px] rounded-[4px] bg-[#3f8e2e]" />
          <div className="absolute left-[157px] top-[216px] h-[18px] w-[11px] rounded-[4px] bg-[#3f8e2e]" />
          <img alt="" className="absolute left-[203px] top-[118px] h-[86px] w-[62px]" src="/products/j-farm/ellipse-11.svg" />
          <img alt="" className="absolute left-[214px] top-[128px] h-[44px] w-[32px]" src="/products/j-farm/ellipse-12.svg" />
          <div className="absolute left-[228px] top-[100px] h-[22px] w-[7px] rounded-[3px] bg-[#3f8e2e]" />
          <img alt="" className="absolute left-[211px] top-[196px] h-[12px] w-[16px]" src="/products/j-farm/ellipse-13.svg" />
          <img alt="" className="absolute left-[238px] top-[196px] h-[12px] w-[16px]" src="/products/j-farm/ellipse-13.svg" />
        </div>

        <div className="mt-[44px] flex w-full items-center">
          <div className="flex flex-1 flex-col items-center gap-[6px] text-center">
            <p className="text-[13px] text-[#8b95a1]">금리(1년,세전)</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">기본 연 3.00%</p>
          </div>
          <div className="h-[52px] w-px shrink-0 bg-[#e9edf3]" />
          <div className="flex flex-1 flex-col items-center gap-[6px] text-center">
            <p className="text-[13px] text-[#8b95a1]">납입금</p>
            <p className="text-[19px] font-extrabold text-[#191f28]">월 최대 1천만원</p>
            <p className="text-[13px] text-[#8b95a1]">6개월, 12개월</p>
          </div>
        </div>
      </div>

      <div className="mt-[44px] flex w-full flex-col items-center bg-[#f7f8fb] px-[20px] py-[56px]">
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          매달 들어오는 이자로
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">더 크게 불려요</p>
        <div className="mt-[16px] text-center text-[15px] leading-[24px] text-[#8b95a1]">
          <p>이자는 다시 저금 가능하고,</p>
          <p>중도해지해도 받은 이자는 차감되지 않아요</p>
        </div>
        <div className="relative mt-[44px] size-[180px]">
          <img alt="" className="absolute left-[26px] top-[50px] h-[124px] w-[128px]" src="/products/j-farm/ellipse-14.svg" />
          <img alt="" className="absolute left-[48px] top-[66px] h-[64px] w-[68px]" src="/products/j-farm/ellipse-15.svg" />
          <div className="absolute left-[58px] top-[32px] h-[24px] w-[64px] rounded-[10px] bg-[#2f58d6]" />
          <img alt="" className="absolute left-[80px] top-[14px] size-[22px]" src="/products/j-farm/ellipse-16.svg" />
          <p className="absolute left-[72px] top-[88px] text-[34px] font-extrabold text-white">₩</p>
        </div>
      </div>

      <div className="flex w-full flex-col items-center px-[20px] py-[60px]">
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">입금 금액에 따라</p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          귀여운 과일 캐릭터를 획득해요
        </p>
        <p className="mt-[16px] text-center text-[15px] text-[#8b95a1]">
          과일이 많을수록 더 많은 이자를 수확해요
        </p>
        <div className="relative mt-[40px] h-[306px] w-[225px]">
          <img alt="" className="absolute left-0 top-0 h-[110px] w-[105px]" src="/products/j-farm/card-fruit-1.svg" />
          <img alt="" className="absolute left-[120px] top-[68px] h-[110px] w-[105px]" src="/products/j-farm/card-fruit-2.svg" />
          <img alt="" className="absolute left-0 top-[118px] h-[110px] w-[105px]" src="/products/j-farm/card-fruit-3.svg" />
          <img alt="" className="absolute left-[120px] top-[196px] h-[110px] w-[105px]" src="/products/j-farm/card-fruit-4.svg" />
        </div>
      </div>

      <div className="flex w-full flex-col items-center px-[20px] py-[44px]">
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">
          월 최대 1천만원까지
        </p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          자유로운 입금이 가능해요
        </p>
        <p className="mt-[16px] text-center text-[15px] text-[#8b95a1]">
          원하는 만큼, 필요할 때 마다 입금해요
        </p>
        <div className="relative mt-[44px] h-[150px] w-[200px]">
          <div className="absolute left-[26px] top-[52px] h-[62px] w-[120px] rounded-[8px] bg-[#e8d9a8]" />
          <div className="absolute left-[26px] top-[52px] h-[14px] w-[120px] bg-white" />
          <div className="absolute left-[26px] top-[96px] h-[14px] w-[120px] bg-white" />
          <div className="absolute left-[60px] top-[60px] h-[46px] w-[52px] rounded-[6px] bg-[#f5ebc8]" />
          <p className="absolute left-[42px] top-[88px] text-[15px] font-extrabold text-[#b99b4e]">5000</p>
          <div className="absolute left-[120px] top-[44px] h-[54px] w-[52px] rounded-[8px] bg-[#d9c68a]" />
          <div className="absolute left-[120px] top-[44px] h-[10px] w-[52px] bg-[#efe3bc]" />
          <img alt="" className="absolute left-[132px] top-[96px] h-[26px] w-[44px]" src="/products/j-farm/ellipse-17.svg" />
          <img alt="" className="absolute left-[140px] top-[104px] h-[20px] w-[34px]" src="/products/j-farm/ellipse-18.svg" />
          <div className="absolute left-[58px] top-[20px] h-[34px] w-[5px] rounded-[2px] bg-[#c9cdd5]" />
        </div>
      </div>

      <div className="flex w-full flex-col items-center bg-[#f7f8fb] px-[20px] py-[52px]">
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#191f28]">J팜 농장</p>
        <p className="text-center text-[25px] font-extrabold leading-[35px] text-[#4262ff]">
          체험기가 궁금하다면?
        </p>
        <p className="mt-[16px] text-center text-[15px] text-[#8b95a1]">
          대학생 크리에이터들의 리뷰를 확인하세요
        </p>
        <img alt="" className="mt-[18px] h-[46px] w-[96px]" src="/products/j-farm/illo-heads.svg" />
        <div className="relative mt-[10px] h-[175px] w-full overflow-hidden rounded-[4px] bg-[#d5dae2]">
          <p className="absolute left-[24px] top-[22px] text-[40px] font-extrabold text-[#1b33c7]">
            귀여운 적금?
          </p>
          <div className="absolute left-0 top-[140px] h-[35px] w-full bg-[#c3c9d2]" />
          <img alt="" className="absolute left-[72px] top-[74px] h-[72px] w-[60px]" src="/products/j-farm/ellipse-19.svg" />
          <div className="absolute left-[78px] top-[110px] h-[40px] w-[48px] rounded-[6px] bg-[#3b5bf0]" />
          <img alt="" className="absolute left-[222px] top-[74px] h-[72px] w-[60px]" src="/products/j-farm/ellipse-20.svg" />
          <div className="absolute left-[228px] top-[110px] h-[40px] w-[48px] rounded-[6px] bg-[#2b3a66]" />
          <img alt="" className="absolute left-[148px] top-[58px] size-[58px]" src="/products/j-farm/ellipse-21.svg" />
          <img alt="" className="absolute left-[171px] top-[76px] h-[22px] w-[18px]" src="/products/j-farm/vector-play.svg" />
        </div>
      </div>

      <div className="flex w-full flex-col items-start px-[20px] pt-[36px]">
        <p className="text-[15px] font-bold text-[#191f28]">상품 안내</p>
        <div className="mt-[6px] flex w-full flex-col">
          {ACCORDION_ROWS.map((row) => (
            <button
              key={row}
              type="button"
              className="flex h-[56px] w-full items-center justify-between"
            >
              <p className="text-[16px] font-bold text-[#191f28]">{row}</p>
              <img alt="" className="size-[22px]" src="/products/j-farm/icon-chevron-down.svg" />
            </button>
          ))}
        </div>
      </div>
      <div className="w-full px-[20px] pb-[10px] pt-[18px] text-[13px] leading-[22px] text-[#8b95a1]">
        <p>2025.11.21. 준법감시인 심의필 2025-2560</p>
        <p>(유효기간 : 2026.08.13.)</p>
      </div>
      <div className="flex w-full items-start px-[20px] pb-[24px] pt-[18px]">
        <div className="flex flex-1 flex-col items-center gap-[8px]">
          <img alt="" className="size-[28px]" src="/products/j-farm/icon-share.svg" />
          <p className="text-[13px] font-bold text-[#191f28]">공유하기</p>
        </div>
        <div className="h-[34px] w-px shrink-0 bg-[#e9edf3]" />
        <div className="flex flex-1 flex-col items-center gap-[8px]">
          <img alt="" className="size-[28px]" src="/products/j-farm/icon-phone.svg" />
          <p className="text-[13px] font-bold text-[#191f28]">전화상담</p>
        </div>
        <div className="h-[34px] w-px shrink-0 bg-[#e9edf3]" />
        <div className="flex flex-1 flex-col items-center gap-[8px]">
          <img alt="" className="size-[28px]" src="/products/j-farm/icon-chat.svg" />
          <p className="text-[13px] font-bold text-[#191f28]">톡상담</p>
        </div>
      </div>

      <div className="fixed bottom-0 left-1/2 w-full max-w-[430px] -translate-x-1/2 bg-white px-[20px] pb-[24px] pt-[8px]">
        <button
          type="button"
          className="flex h-[56px] w-full items-center justify-center rounded-[14px] bg-[#0114a7]"
        >
          <p className="text-[17px] font-bold text-white">농장 만들기</p>
        </button>
      </div>
    </div>
  );
}
