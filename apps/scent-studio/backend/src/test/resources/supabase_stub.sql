-- 테스트 준비: Supabase에만 있는 것을 흉내 낸다 (실제 Supabase에는 이미 있어서 마이그레이션에 넣지 않음)
create role anon nologin;
create role authenticated nologin;
create schema auth;
create table auth.users (
  id    uuid primary key,
  email text
);
