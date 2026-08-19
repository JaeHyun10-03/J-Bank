"use client";

import { useParams } from "next/navigation";
import { ScreenPlaceholder } from "@/components/screen-placeholder";
import { MobileScreen } from "@/components/mobile-screen";
import { JKidsDetail } from "./_details/j-kids";
import { JKidsAccountDetail } from "./_details/j-kids-account";
import { JFarmDetail } from "./_details/j-farm";
import { CreditLoanDetail } from "./_details/credit-loan";
import { CheckCardDetail } from "./_details/checkcard";

const DETAIL_SCREENS: Record<string, () => React.JSX.Element> = {
  "j-kids": JKidsDetail,
  "j-kids-account": JKidsAccountDetail,
  "j-farm": JFarmDetail,
  "credit-loan": CreditLoanDetail,
  "one-checkcard": CheckCardDetail,
};

export default function ProductDetailPage() {
  const { productCode } = useParams<{ productCode: string }>();
  const Detail = DETAIL_SCREENS[productCode];

  if (!Detail) {
    return <ScreenPlaceholder title="상품상세" />;
  }

  return (
    <MobileScreen>
      <Detail />
    </MobileScreen>
  );
}
