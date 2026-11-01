---
paths:
  - "apps/*/tests/**"
  - "apps/**/*.{test,spec}.*"
  - "apps/**/playwright.config.*"
  - "apps/**/vitest.config.*"
  - "apps/*/docs/tests.md"
---

# 테스트 규칙 (QA팀)

## 도구
- 사장이 승인한 목록(`docs/tests.md`)은 Playwright(실제 브라우저를 사람처럼 조작)로.
- 계산·날짜처럼 까다로운 부분은 부품 단위 테스트를 QA팀이 알아서 더한다(React는 Vitest, Java·Spring은 JUnit). 승인 대상 아님.
- 웹 테스트에 더해, 폰 크기 화면 캡처와 안드로이드 에뮬레이터 실행 확인을 QA 보고에 넣는다.

## 승인된 테스트는 건드리지 않는다
- 승인된 테스트는 사장 허락 없이 고치거나 지우지 않는다. (커밋된 `tests/` 파일을 고치려 하면 훅이 사장 확인 창을 띄운다.)
- 테스트와 다르게 만드는 게 낫다고 생각되면 코드를 테스트에 맞추고, 의견은 질문으로 남긴다.
- 화면 구조가 바뀌어 요소를 못 찾게 되면: **검사 내용과 숫자는 그대로, 찾는 위치만** 바꾸겠다고 보여 주고 다시 승인받는다.
- 테스트 준비 코드(`conftest.py`, 기다리는 시간, 테스트용 서버, `tests/run.sh`)는 검사 내용이 아니므로 고칠 수 있다. 고친 이유는 `docs/NOTES.md`에.

## 고치는 방법
- **테스트 환경 탓인 실패를 앱 코드로 우회하지 않는다.** 테스트 준비 코드를 고친다.
- 리뷰·레드팀에서 나온 문제는 고치기 전에 그 문제를 잡는 회귀 테스트를 먼저 넣어 실패를 확인한다. 회귀 테스트는 승인 목록과 섞지 않고 `tests/test_review_fixes.*`에 둔다.
- 테스트 하나를 3번 고쳐도 통과하지 않으면 멈추고 superpowers:systematic-debugging으로 원인부터 찾는다.

## 돌리기
- 테스트는 직접 돌리지 말고 `test-runner` 에이전트에게 맡겨 요약만 받는다(긴 로그는 `docs/test-log.txt`).
- 실행 명령은 `apps/<앱>/HANDOFF.md`의 `테스트 실행:` 줄 하나. 훅이 이 줄을 읽어 커밋 전에 돌린다. 허용 명령과 형식은 `/build-app` "HANDOFF 형식" 절.
- 파이썬 테스트는 `bash tests/run.sh`(견본 `.claude/skills/build-app/templates/run.sh`).
