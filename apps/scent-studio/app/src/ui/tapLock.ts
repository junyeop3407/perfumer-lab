/**
 * 연속 클릭 잠금: 화면을 여는 버튼을 빠르게 두 번 눌러도 한 번만 연다(plan_stage5.md 3절 "연속 클릭 잠금").
 * 같은 이동(key)만 막는다. 다른 이동은 바로 이어서 눌러도 된다.
 */
export function makeTapLock(now: () => number = Date.now, ms = 600) {
  let lastKey: string | null = null;
  let last = -Infinity;
  return (key: string, action: () => void) => {
    const t = now();
    if (key === lastKey && t - last < ms) return;
    lastKey = key;
    last = t;
    action();
  };
}

/** 화면 이동용 잠금 하나를 앱 전체가 같이 쓴다 */
export const navTap = makeTapLock();
