---
name: handoff
description: Use when a step of work is finished, before stopping a session, or when switching computers ("마무리해 줘", "올려 줘", "/handoff") — updates HANDOFF.md, commits, and pushes so another computer can continue.
---

# handoff — 단계 마무리 (HANDOFF 갱신 → 커밋 → 푸시)

이 저장소는 여러 컴퓨터에서 이어 작업한다. **대화 기록에만 있는 정보는 다른 컴퓨터에서 사라진다.** 그래서 단계가 끝날 때마다 이 순서를 지킨다.

## 순서
1. `git pull` — 다른 컴퓨터에서 한 작업을 먼저 받는다. 충돌이 나면 멈추고 사장에게 쉬운 말로 알린다.
2. **앱 HANDOFF 갱신** (`apps/<앱>/HANDOFF.md`, 형식은 `/build-app` "HANDOFF 형식"): 현재 단계, 결과(테스트 N/N 통과 + 날짜), 사용자에게 남은 질문, 다음 할 일.
3. **루트 HANDOFF 갱신** (`HANDOFF.md`): 앱을 끝냈을 때, 또는 중간에 멈출 때만. 로드맵 표와 "마지막 갱신" 날짜.
4. **커밋**: 한 커밋에 한 가지 일. 메시지는 한국어로 "무엇을, 왜" (예: `가계부: 월별 합계 추가 — 사장 요청`).
   - 테스트가 실패하는 상태로는 커밋하지 않는다(훅이 막는다). 훅이 막으면 우회하지 말고 고친다.
   - 비밀값·`.env`·컴퓨터 전용 경로가 섞이면 훅이 막는다. 그 파일을 빼고 다시 커밋한다.
   - 3단계(테스트만 쓴 상태)는 커밋하지 않는다. 상태만 HANDOFF에 적고 4단계 통과 후 함께 커밋.
5. **푸시**: `git push`. 강제 푸시는 하지 않는다(훅이 막는다). 거절되면 1번부터 다시.
6. 사장에게 한 줄로: 무엇을 올렸는지 + 다음 할 일.

## 세션을 끝낼 때
- 대화가 길어져 흐름을 놓칠 것 같으면 이 절차를 마치고 새 세션에서 "HANDOFF.md 읽고 이어서 해 줘"(앱은 "/build-app 이어서")를 하자고 제안한다.
- 커밋 전 파일은 다른 컴퓨터로 따라가지 않는다. 옮기기 전에 이 절차를 끝낸다.
