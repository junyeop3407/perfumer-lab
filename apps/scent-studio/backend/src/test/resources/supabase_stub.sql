-- 테스트 준비: Supabase에만 있는 것을 흉내 낸다 (실제 Supabase에는 이미 있어서 마이그레이션에 넣지 않음)
create role anon nologin;
create role authenticated nologin;
-- Supabase에서 auth 스키마와 auth.users의 주인은 로그인 시스템 계정 supabase_auth_admin이다.
-- 이 계정은 app 스키마 권한이 없다. 계정 삭제 연쇄가 이 계정으로도 되는지 테스트가 확인한다.
create role supabase_auth_admin nologin;
create schema auth authorization supabase_auth_admin;
create table auth.users (
  id    uuid primary key,
  email text
);
alter table auth.users owner to supabase_auth_admin;
-- 로그인 세션 (V2 app.session_alive가 읽음). 실제 Supabase auth.sessions의 칸 중 쓰는 것만, 같은 이름·타입.
-- 실제처럼 사용자를 지우면 세션도 같이 지워진다.
create table auth.sessions (
  id        uuid primary key,
  user_id   uuid not null references auth.users (id) on delete cascade,
  not_after timestamptz
);
alter table auth.sessions owner to supabase_auth_admin;
