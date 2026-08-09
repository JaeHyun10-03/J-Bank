"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import Link from "next/link";
import { apiClient } from "@/lib/api-client";
import { getApiError } from "@/lib/api-error";
import { useAuthStore } from "@/lib/auth-store";
import type { components } from "@/types/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Alert, AlertDescription } from "@/components/ui/alert";

const signupSchema = z.object({
  name: z.string().min(1, "이름을 입력하세요"),
  loginId: z.string().min(1, "로그인ID를 입력하세요"),
  password: z.string().min(8, "비밀번호는 8자 이상이어야 합니다"),
  residentRegNo: z
    .string()
    .regex(/^\d{6}-\d{7}$/, "실명번호 형식이 올바르지 않습니다 (예: 900101-1234567)"),
  birthDate: z.string().min(1, "생년월일을 입력하세요"),
  phone: z.string().min(1, "연락처를 입력하세요"),
  address: z.string().optional(),
  occupation: z.string().optional(),
  identityVerificationMethod: z.enum(["FACE_TO_FACE", "NON_FACE_TO_FACE"]),
  transactionPurpose: z.string().optional(),
  fundSource: z.string().optional(),
});

type SignupForm = z.infer<typeof signupSchema>;
type ApiResponseCustomerRegisterResponse =
  components["schemas"]["ApiResponseCustomerRegisterResponse"];
type ApiResponseLoginResponse = components["schemas"]["ApiResponseLoginResponse"];

export default function SignupPage() {
  const router = useRouter();
  const login = useAuthStore((state) => state.login);
  const [formError, setFormError] = useState<string | null>(null);
  const [duplicate, setDuplicate] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<SignupForm>({
    resolver: zodResolver(signupSchema),
    defaultValues: { identityVerificationMethod: "FACE_TO_FACE" },
  });

  async function onSubmit(values: SignupForm) {
    setFormError(null);
    setDuplicate(false);
    try {
      const registerResponse = await apiClient.post<ApiResponseCustomerRegisterResponse>(
        "/customers",
        values,
      );
      const registered = registerResponse.data.data;

      // 계좌개설·EDD API는 인증이 필요한데 가입 직후에는 아직 로그인 상태가 아니다.
      // 화면플로우차트는 가입과 다음 화면 사이에 별도 로그인 화면을 두지 않으므로,
      // 방금 입력한 자격증명으로 조용히 한 번 더 로그인해 인증 쿠키를 받는다.
      const loginResponse = await apiClient.post<ApiResponseLoginResponse>("/auth/login", {
        loginId: values.loginId,
        password: values.password,
      });
      const loggedIn = loginResponse.data.data;
      login(loggedIn?.customerId ?? registered?.customerId ?? "", loggedIn?.name ?? values.name);

      if (registered?.eddRequired) {
        router.push("/signup/edd");
      } else {
        router.push("/accounts/open");
      }
    } catch (error) {
      const apiError = getApiError(error);
      if (apiError?.code === "ACC_001_DUPLICATE_RESIDENT_REG_NO") {
        setDuplicate(true);
      } else {
        setFormError(apiError?.message ?? "가입에 실패했습니다. 입력값을 확인해주세요.");
      }
    }
  }

  if (duplicate) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-8">
        <Card className="w-full max-w-sm">
          <CardHeader>
            <CardTitle>이미 등록된 계정이 있습니다</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <p className="text-sm text-muted-foreground">
              동일한 실명번호로 이미 가입된 계정이 있습니다. 로그인해주세요.
            </p>
            <Button render={<Link href="/login" />}>로그인하러 가기</Button>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-8">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>회원가입</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <FieldGroup>
              <Field data-invalid={!!errors.name}>
                <FieldLabel htmlFor="name">이름</FieldLabel>
                <Input id="name" {...register("name")} />
                <FieldError errors={[errors.name]} />
              </Field>
              <Field data-invalid={!!errors.loginId}>
                <FieldLabel htmlFor="loginId">로그인ID</FieldLabel>
                <Input id="loginId" autoComplete="username" {...register("loginId")} />
                <FieldError errors={[errors.loginId]} />
              </Field>
              <Field data-invalid={!!errors.password}>
                <FieldLabel htmlFor="password">비밀번호</FieldLabel>
                <Input
                  id="password"
                  type="password"
                  autoComplete="new-password"
                  {...register("password")}
                />
                <FieldError errors={[errors.password]} />
              </Field>
              <Field data-invalid={!!errors.residentRegNo}>
                <FieldLabel htmlFor="residentRegNo">실명번호</FieldLabel>
                <Input id="residentRegNo" placeholder="900101-1234567" {...register("residentRegNo")} />
                <FieldError errors={[errors.residentRegNo]} />
              </Field>
              <Field data-invalid={!!errors.birthDate}>
                <FieldLabel htmlFor="birthDate">생년월일</FieldLabel>
                <Input id="birthDate" type="date" {...register("birthDate")} />
                <FieldError errors={[errors.birthDate]} />
              </Field>
              <Field data-invalid={!!errors.phone}>
                <FieldLabel htmlFor="phone">연락처</FieldLabel>
                <Input id="phone" placeholder="010-1234-5678" {...register("phone")} />
                <FieldError errors={[errors.phone]} />
              </Field>
              <Field>
                <FieldLabel htmlFor="address">주소</FieldLabel>
                <Input id="address" {...register("address")} />
              </Field>
              <Field>
                <FieldLabel htmlFor="occupation">직업</FieldLabel>
                <Input id="occupation" {...register("occupation")} />
              </Field>
              <Field>
                <FieldLabel htmlFor="transactionPurpose">거래목적</FieldLabel>
                <Input id="transactionPurpose" {...register("transactionPurpose")} />
              </Field>
              <Field>
                <FieldLabel htmlFor="fundSource">자금원천</FieldLabel>
                <Input id="fundSource" {...register("fundSource")} />
              </Field>
              <Field data-invalid={!!errors.identityVerificationMethod}>
                <FieldLabel>실명확인 방법</FieldLabel>
                <div className="flex gap-4 text-sm">
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      value="FACE_TO_FACE"
                      {...register("identityVerificationMethod")}
                    />
                    대면
                  </label>
                  <label className="flex items-center gap-1.5">
                    <input
                      type="radio"
                      value="NON_FACE_TO_FACE"
                      {...register("identityVerificationMethod")}
                    />
                    비대면
                  </label>
                </div>
                <FieldError errors={[errors.identityVerificationMethod]} />
              </Field>
              {formError ? (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              ) : null}
              <Button type="submit" disabled={isSubmitting} className="w-full">
                가입하기
              </Button>
            </FieldGroup>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
