#!/usr/bin/env python3
"""PostgreSQL 로그(log_min_duration_statement)에서 느린 SELECT를 뽑아 바인드 값을 채운 한 줄 SQL로 출력한다.

target.sh explain이 부하 없는 배치 구간 로그에 대해 호출한다. 같은 모양의 쿼리는 한 번만,
최대 10개까지 출력한다. 로그 형식(postgres 기본 stderr):
  2026-.. LOG:  duration: 812.3 ms  execute <unnamed>: select ... where x=$1
  2026-.. DETAIL:  parameters: $1 = '2026-09-27 00:00:00+09'
"""
import re
import sys

ENTRY = re.compile(r"^\S+ \S+ \S+ \[\d+\] (LOG|DETAIL|STATEMENT|ERROR):\s+(.*)$")
DURATION = re.compile(r"duration: [\d.]+ ms\s+(?:execute [^:]+|statement):\s*(.*)$", re.S)
PARAM = re.compile(r"\$(\d+) = ('(?:[^']|'')*'|NULL)")


def entries(lines):
    kind, buf = None, []
    for line in lines:
        m = ENTRY.match(line)
        if m:
            if kind:
                yield kind, "\n".join(buf)
            kind, buf = m.group(1), [m.group(2)]
        elif kind:
            buf.append(line.rstrip("\n").lstrip("\t"))
    if kind:
        yield kind, "\n".join(buf)


def main(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        items = list(entries(f))
    seen, out = set(), []
    for i, (kind, text) in enumerate(items):
        m = DURATION.search(text) if kind == "LOG" else None
        if not m:
            continue
        sql = m.group(1).strip()
        if not sql.lower().lstrip("(").startswith("select"):
            continue
        params = {}
        if i + 1 < len(items) and items[i + 1][0] == "DETAIL":
            params = {int(n): v for n, v in PARAM.findall(items[i + 1][1])}
        key = re.sub(r"\s+", " ", sql)
        if key in seen:
            continue
        seen.add(key)
        filled = re.sub(r"\$(\d+)\b", lambda p: params.get(int(p.group(1)), p.group(0)), sql)
        out.append(re.sub(r"\s+", " ", filled))
        if len(out) == 10:
            break
    print("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1])
