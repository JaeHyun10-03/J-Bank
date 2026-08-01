/** 화면 구현 전까지의 빈 자리표시자. `docs/04_J-Bank_화면플로우차트.md`의 화면 이름을 그대로 쓴다. */
export function ScreenPlaceholder({
  title,
  description,
}: {
  title: string;
  description?: string;
}) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-2 p-8">
      <h1 className="text-xl font-semibold">{title}</h1>
      {description ? <p className="text-sm text-neutral-500">{description}</p> : null}
    </main>
  );
}
