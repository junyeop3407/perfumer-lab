-- scent-studio DB 2판 (docs/server_design.md 3-1·4-2·7절, 2026-11-06 사장 승인)
-- V1은 실제 DB에 적용됐으므로 고치지 않고 더한다. 실행 계정: postgres. 같은 방식: RLS 켜기 + scent_server에만 규칙, 칸 단위 권한.
-- scent_server의 auth 권한은 넓히지 않는다(세션은 아래 함수로만 묻는다).

-- 1. 세션 확인: 토큰의 세션이 아직 살아 있는지만 true/false로. 세션 id와 사용자 id가 둘 다 맞아야 true, null이면 false.
--    security definer(주인 postgres 권한으로 auth.sessions를 읽음) → search_path를 비우고 전체 이름만 쓴다.
create function app.session_alive(p_session_id uuid, p_user_id uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce((
    select true from auth.sessions s
    where s.id = p_session_id and s.user_id = p_user_id
      and (s.not_after is null or s.not_after > now())
    limit 1), false)
$$;
revoke all on function app.session_alive(uuid, uuid) from public, anon, authenticated;
grant execute on function app.session_alive(uuid, uuid) to scent_server;

-- 2. 카탈로그 버전 표. 넣기·바꾸기는 마이그레이션 계정 도구(dbCatalogPublish)로만, scent_server는 읽기만.
create table app.catalog_releases (
  version           text primary key check (version ~ '^[0-9]{4}\.(0[1-9]|1[0-2])\.(0[1-9]|[12][0-9]|3[01])(\.[1-9][0-9]{0,2})?$'),
  engine_version    integer not null check (engine_version > 0),
  sha256            text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  keys_sha256       text not null check (keys_sha256 ~ '^[0-9a-f]{64}$'),
  size_bytes        integer not null check (size_bytes between 1 and 50000000),
  -- 경로는 버전에서 정해진다(다른 파일·다른 폴더를 가리키지 못하게)
  storage_path      text not null unique check (storage_path = 'catalog-' || version || '.json.gz'),
  keys_storage_path text not null unique check (keys_storage_path = 'catalog-' || version || '.keys.json.gz'),
  min_app_version   text not null check (min_app_version ~ '^(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,3})$'),
  is_current        boolean not null default false,
  published_at      timestamptz not null default now(),
  note              text check (char_length(note) <= 200)
);
-- 지금 버전은 한 줄만
create unique index catalog_releases_one_current on app.catalog_releases (is_current) where is_current;

revoke all on app.catalog_releases from public, anon, authenticated;
grant select on app.catalog_releases to scent_server;
alter table app.catalog_releases enable row level security;
create policy server_read on app.catalog_releases for select to scent_server using (true);

-- 3. 사용 기록 다시 보내기 중복 무시: 앱이 기록마다 붙이는 uuid. 옛 줄·옛 앱은 비어 있어도 됨(필수 여부는 서버가 확인).
--    (install_id, client_event_id)가 같으면 서버가 on conflict do nothing으로 건너뛴다. null끼리는 겹치지 않는다.
alter table app.events add column client_event_id uuid;
create unique index events_install_client_event_uq on app.events (install_id, client_event_id);
grant insert (client_event_id) on app.events to scent_server;
