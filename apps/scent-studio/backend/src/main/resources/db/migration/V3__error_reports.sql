-- scent-studio DB 3판: 오류 신고 (docs/server_design.md 2·4·7절, 사장 결정 2026-11-06 "로그인한 사람만", 기능 "추천이 이상해요" 2026-11-08)
-- V1·V2는 실제 DB에 적용됐으므로 고치지 않고 더한다. 실행 계정: postgres. 같은 방식: RLS 켜기 + scent_server에만 규칙, 칸 단위 권한.
-- 서버는 넣기만 한다(읽기·바꾸기·지우기 없음). 신고를 보는 일은 사장이 대시보드(postgres)에서 한다.

create table app.error_reports (
  id              uuid primary key default gen_random_uuid(),
  -- 신고자. 계정을 지우면 신고도 같이 지운다(db_design.md 8절 "계정 삭제 = 내 데이터 전부")
  user_id         uuid not null references auth.users (id) on delete cascade,
  -- 무엇을: not_similar 추천이 비슷하지 않아요 / wrong_info 향수 정보(이름·브랜드·연도)가 틀려요 / note_name 노트 이름·번역이 이상해요 / other 기타
  kind            text not null check (kind in ('not_similar', 'wrong_info', 'note_name', 'other')),
  perfume_key     text check (perfume_key is null or app.valid_key(perfume_key)),
  note_key        text check (note_key is null or app.valid_key(note_key)),
  memo            text check (char_length(memo) <= 500),
  -- 그때 상황(추천 화면에서 신고했으면 순위·점수). 엔진·카탈로그·앱 버전은 꼭
  rank            smallint check (rank between 1 and 10),
  score           double precision check (score between 0 and 1),
  engine_version  integer not null check (engine_version > 0),
  catalog_version text not null check (char_length(catalog_version) between 1 and 32),
  app_version     text not null check (char_length(app_version) between 1 and 32),
  -- 처리 상태는 사장만 바꾼다(서버는 이 칸에 넣을 권한이 없어 늘 기본값 open)
  status          text not null default 'open' check (status in ('open', 'resolved', 'dismissed')),
  created_at      timestamptz not null default now(),
  -- 향수나 노트 중 하나는 꼭
  check (perfume_key is not null or note_key is not null)
);
-- 계정 삭제 때 연쇄 삭제가 빠르게
create index error_reports_user_id_idx on app.error_reports (user_id);

revoke all on app.error_reports from public, anon, authenticated;
grant insert (user_id, kind, perfume_key, note_key, memo, rank, score, engine_version, catalog_version, app_version)
  on app.error_reports to scent_server;

alter table app.error_reports enable row level security;
-- 넣기만 허용하는 규칙. 실제 방어선은 위 칸 단위 넣기 권한이고 RLS는 안전장치다(user_id를 토큰 사용자로 정하는 것은 서버 코드)
create policy server_insert on app.error_reports for insert to scent_server with check (true);
