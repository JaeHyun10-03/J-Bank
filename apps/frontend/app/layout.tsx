import type { Metadata } from "next";
import "./globals.css";
import { Gothic_A1 } from "next/font/google";
import { cn } from "@/lib/utils";
import { Providers } from "@/components/providers";

const gothicA1 = Gothic_A1({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-sans",
});

export const metadata: Metadata = {
  title: "J-Bank",
  description: "제이뱅크",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className={cn("font-sans", gothicA1.variable)}>
      <body className="antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
