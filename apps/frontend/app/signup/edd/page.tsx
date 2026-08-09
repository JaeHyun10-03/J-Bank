"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { useAuthStore } from "@/lib/auth-store";
import type { components } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Alert, AlertDescription } from "@/components/ui/alert";

const eddSchema = z.object({
  transactionPurpose: z.string().min(1, "거래목적을 입력하세요"),
  fundSource: z.string().min(1, "자금원천을 입력하세요"),
  supportingDocumentRef: z.string().min(1, "증빙자료 참조번호를 입력하세요"),
});

type EddForm = z.infer<typeof eddSchema>;
type ApiResponseEddRegisterResponse = components["schemas"]["ApiResponseEddRegisterResponse"];

export default function EddPage() {
  const router = useRouter();
  const customerId = useAuthStore((state) => state.customerId);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<EddForm>({ resolver: zodResolver(eddSchema) });

  async function onSubmit(values: EddForm) {
    setFormError(null);
    if (!customerId) {
      setFormError("로그인 정보가 없습니다. 처음부터 다시 시도해주세요.");
      return;
    }
    try {
      await apiClient.post<ApiResponseEddRegisterResponse>(
        `/customers/${customerId}/edd`,
        values,
      );
      router.push("/accounts/open");
    } catch (error) {
      const apiError = getApiError(error);
      if (apiError?.code === "ACC_003_EDD_EVIDENCE_INSUFFICIENT") {
        setFormError("소명자료가 부족합니다. 자금원천과 증빙자료를 보완해주세요.");
      } else {
        setFormError(apiError?.message ?? "EDD 등록에 실패했습니다.");
      }
    }
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-8">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>강화된 고객확인(EDD)</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <FieldGroup>
              <Field data-invalid={!!errors.transactionPurpose}>
                <FieldLabel htmlFor="transactionPurpose">거래목적 상세</FieldLabel>
                <Input id="transactionPurpose" {...register("transactionPurpose")} />
                <FieldError errors={[errors.transactionPurpose]} />
              </Field>
              <Field data-invalid={!!errors.fundSource}>
                <FieldLabel htmlFor="fundSource">자금원천</FieldLabel>
                <Input id="fundSource" {...register("fundSource")} />
                <FieldError errors={[errors.fundSource]} />
              </Field>
              <Field data-invalid={!!errors.supportingDocumentRef}>
                <FieldLabel htmlFor="supportingDocumentRef">증빙자료 참조번호</FieldLabel>
                <Input id="supportingDocumentRef" {...register("supportingDocumentRef")} />
                <FieldError errors={[errors.supportingDocumentRef]} />
              </Field>
              {formError ? (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              ) : null}
              <Button type="submit" disabled={isSubmitting} className="w-full">
                제출
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
