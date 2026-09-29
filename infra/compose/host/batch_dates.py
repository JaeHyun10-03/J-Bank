#!/usr/bin/env python3
"""CTR·FDS 따라잡기 기준일 목록. boot.sh가 잡마다 부른다.

사용법: batch_dates.py <마지막 완료 runDate YYYY-MM-DD | -> [--now ISO8601]
출력:   (마지막 완료일 + 1) ~ 어제(KST)를 한 줄에 하나씩. 기록이 없으면(-) 어제 하나.
        마지막 완료일이 어제 이후면 아무것도 출력하지 않는다.
호스트 시간대(UTC)와 무관하게 KST 날짜로 계산한다.
"""
import sys
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

KST = ZoneInfo("Asia/Seoul")


def catch_up(last, now):
    yesterday = now.astimezone(KST).date() - timedelta(days=1)
    day = yesterday if last is None else last + timedelta(days=1)
    out = []
    while day <= yesterday:
        out.append(day)
        day += timedelta(days=1)
    return out


def self_test():
    utc = ZoneInfo("UTC")
    d = date.fromisoformat
    # 월요일 09:05 KST = 월요일 00:05 UTC
    mon = datetime(2026, 10, 5, 0, 5, tzinfo=utc)
    assert catch_up(None, mon) == [d("2026-10-04")]
    assert catch_up(d("2026-10-01"), mon) == [d("2026-10-02"), d("2026-10-03"), d("2026-10-04")]  # 목 완료 → 금·토·일
    assert catch_up(d("2026-10-04"), mon) == []  # 어제 완료
    assert catch_up(d("2026-10-05"), mon) == []  # 오늘 완료(cron 시절 runDate=당일)
    assert catch_up(d("2026-10-09"), mon) == []  # 미래
    # 08:30 KST 수동 기동 = 전날 23:30 UTC. KST로 계산해야 어제가 10/04.
    early = datetime(2026, 10, 4, 23, 30, tzinfo=utc)
    assert catch_up(None, early) == [d("2026-10-04")]
    # 월·연 경계
    jan1 = datetime(2027, 1, 1, 0, 10, tzinfo=utc)
    assert catch_up(d("2026-12-30"), jan1) == [d("2026-12-31")]
    mar1 = datetime(2027, 3, 1, 1, 0, tzinfo=utc)
    assert catch_up(d("2027-02-26"), mar1) == [d("2027-02-27"), d("2027-02-28")]
    print("batch_dates self-test ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    last = None if sys.argv[1] in ("-", "") else date.fromisoformat(sys.argv[1])
    now = datetime.fromisoformat(sys.argv[3]) if len(sys.argv) > 3 and sys.argv[2] == "--now" else datetime.now(KST)
    for day in catch_up(last, now):
        print(day.isoformat())
