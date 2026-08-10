/**
 * 피그마 회원가입 플로우엔 생년월일 입력 화면이 따로 없다. 주민등록번호 앞 6자리(YYMMDD)와
 * 뒷자리 첫 숫자(성별·세기 코드)로 역산해 백엔드가 요구하는 birthDate를 채운다.
 */
export function deriveBirthDate(ssnFront: string, ssnBack: string): string | null {
  if (!/^\d{6}$/.test(ssnFront) || !/^\d{7}$/.test(ssnBack)) return null;

  const century = { "1": 1900, "2": 1900, "3": 2000, "4": 2000 }[ssnBack[0]];
  if (!century) return null;

  const yy = Number(ssnFront.slice(0, 2));
  const mm = ssnFront.slice(2, 4);
  const dd = ssnFront.slice(4, 6);
  return `${century + yy}-${mm}-${dd}`;
}

export function maskSsnBack(ssnBack: string): string {
  return ssnBack.length === 0 ? "" : ssnBack[0] + "●".repeat(ssnBack.length - 1);
}
