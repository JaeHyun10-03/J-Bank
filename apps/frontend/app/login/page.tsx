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

const loginSchema = z.object({
  loginId: z.string().min(1, "로그인ID를 입력하세요"),
  password: z.string().min(1, "비밀번호를 입력하세요"),
});

type LoginForm = z.infer<typeof loginSchema>;
type LoginResponse = components["schemas"]["LoginResponse"];
type ApiResponseLoginResponse = components["schemas"]["ApiResponseLoginResponse"];

export default function LoginPage() {
  const router = useRouter();
  const login = useAuthStore((state) => state.login);
  const [locked, setLocked] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) });

  async function onSubmit(values: LoginForm) {
    setFormError(null);
    setLocked(false);
    try {
      const response = await apiClient.post<ApiResponseLoginResponse>("/auth/login", values);
      const data = response.data.data as LoginResponse;
      login(data.customerId ?? "", data.name ?? "");
      router.push("/");
    } catch (error) {
      const apiError = getApiError(error);
      if (apiError?.code === "AUTH_002_ACCOUNT_LOCKED") {
        setLocked(true);
      } else if (apiError?.code === "AUTH_001_INVALID_CREDENTIALS") {
        setFormError("로그인ID 또는 비밀번호가 일치하지 않습니다.");
      } else {
        setFormError(apiError?.message ?? "로그인에 실패했습니다. 잠시 후 다시 시도하세요.");
      }
    }
  }

  if (locked) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-8">
        <Card className="w-full max-w-sm">
          <CardHeader>
            <CardTitle>계정이 잠겼습니다</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              비밀번호 연속 실패로 계정이 잠겼습니다. 잠시 후 다시 시도해주세요.
            </p>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-8">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>로그인</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <FieldGroup>
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
                  autoComplete="current-password"
                  {...register("password")}
                />
                <FieldError errors={[errors.password]} />
              </Field>
              {formError ? (
                <Alert variant="destructive">
                  <AlertDescription>{formError}</AlertDescription>
                </Alert>
              ) : null}
              <Button type="submit" disabled={isSubmitting} className="w-full">
                로그인
              </Button>
            </FieldGroup>
          </form>
          <p className="mt-4 text-center text-sm text-muted-foreground">
            계정이 없으신가요?{" "}
            <Link href="/signup" className="text-primary underline underline-offset-4">
              회원가입
            </Link>
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
