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
