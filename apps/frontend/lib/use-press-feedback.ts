"use client";

import { useRef, useState } from "react";

const MIN_PRESS_MS = 100;

/** 빠른 탭에서도 눌림 피드백이 최소 노출시간(MIN_PRESS_MS) 동안은 보이도록 보장한다. */
export function usePressFeedback() {
  const [pressed, setPressed] = useState<string | null>(null);
  const pressStartRef = useRef(0);

  function onPressStart(key: string) {
    pressStartRef.current = Date.now();
    setPressed(key);
  }

  function onPressEnd() {
    const elapsed = Date.now() - pressStartRef.current;
    setTimeout(() => setPressed(null), Math.max(0, MIN_PRESS_MS - elapsed));
  }

  return { pressed, onPressStart, onPressEnd };
}
