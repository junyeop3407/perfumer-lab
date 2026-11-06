-- scent-studio DB 1판 (docs/db_design.md 3판, 2026-11-06 사장 승인)
-- 표 6개는 app 스키마. 공개 API(anon·authenticated)에서는 아무것도 못 읽고, 서버 계정 scent_server만 정해진 만큼 쓴다.
-- 실행 계정: postgres (Supabase 기본). auth.users, anon, authenticated는 Supabase가 이미 갖고 있다(테스트 DB는 준비 코드가 흉내 냄).
-- 이메일·비밀번호 같은 값은 이 파일에 넣지 않는다.

-- 1. 서버 계정 (로그인 불가로 만들고, 비밀번호는 사장이 SQL Editor에서 정한다: db_design.md 13절)
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'scent_server') then
    create role scent_server nologin;
  end if;
end $$;

create schema app;
revoke all on schema app from public, anon, authenticated;
grant usage on schema app to scent_server;

-- 2. 검사 함수
-- 앞으로 이 계정(postgres)이 만드는 함수는 기본으로 아무도 실행 못 하게 한다(필요한 곳만 grant).
-- 함수의 PUBLIC 실행 권한은 스키마별로는 거둘 수 없어 전체 기본값으로 건다(리뷰 QA-6).
alter default privileges revoke execute on functions from public;

-- 카탈로그 key 모양: 눈에 보이는 ASCII 글자만, 가운데 띄어쓰기는 허용, 앞뒤 공백·제어 문자·유니코드 없음(리뷰 QA-7)
create function app.valid_key(k text) returns boolean
language sql immutable set search_path = '' as $$
  select k is not null and char_length(k) between 1 and 200 and k ~ '^[!-~]([ -~]*[!-~])?$'
$$;

-- 키 목록(text[]): null 없음, 빈 문자열 없음, 100자 이하, 중복 없음, 최대 max_len개
create function app.valid_keys(k text[], max_len integer) returns boolean
language plpgsql immutable set search_path = '' as $$
begin
  if k is null then return false; end if;
  if cardinality(k) > max_len then return false; end if;
  if array_position(k, null) is not null then return false; end if;
  if exists (select 1 from unnest(k) x where char_length(x) > 100 or not app.valid_key(x)) then return false; end if;
  return (select count(distinct x) from unnest(k) x) = cardinality(k);
end $$;

-- 조합 노트 jsonb: [{note_key, layer, strength}] 1~9개, 층마다 3개까지, 같은 노트 한 번만
create function app.valid_blend_notes(n jsonb) returns boolean
language plpgsql immutable set search_path = '' as $$
declare
  e jsonb;
begin
  if n is null or jsonb_typeof(n) <> 'array' then return false; end if;
  if jsonb_array_length(n) not between 1 and 9 then return false; end if;
  for e in select * from jsonb_array_elements(n) loop
    if jsonb_typeof(e) <> 'object' then return false; end if;
    if (select count(*) from jsonb_object_keys(e)) <> 3 then return false; end if;
    if jsonb_typeof(e -> 'note_key') is distinct from 'string'
       or jsonb_typeof(e -> 'layer') is distinct from 'string'
       or jsonb_typeof(e -> 'strength') is distinct from 'string' then return false; end if;
    if char_length(e ->> 'note_key') > 100 or not app.valid_key(e ->> 'note_key') then return false; end if;
    if (e ->> 'layer') not in ('top', 'middle', 'base') then return false; end if;
    if (e ->> 'strength') not in ('weak', 'medium', 'strong') then return false; end if;
  end loop;
  if (select count(distinct x ->> 'note_key') from jsonb_array_elements(n) x) <> jsonb_array_length(n) then
    return false;
  end if;
  if exists (select 1 from jsonb_array_elements(n) x group by x ->> 'layer' having count(*) > 3) then
    return false;
  end if;
  return true;
end $$;

-- 수정할 때 updated_at을 지금으로 (서버가 값을 안 넣거나 잘못 넣어도 덮어씀)
create function app.touch_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end $$;

-- 3. 표
-- ① 취향 설정
create table app.user_preferences (
  user_id            uuid primary key references auth.users (id) on delete cascade,
  preferred_families text[] not null default '{}' check (app.valid_keys(preferred_families, 10)),
  disliked_note_keys text[] not null default '{}' check (app.valid_keys(disliked_note_keys, 200)),
  updated_at         timestamptz not null default now()
);

-- ② 저장한 조합
create table app.blends (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users (id) on delete cascade,
  -- 1~40자, 제어 문자(탭·줄바꿈 등) 없음, 공백·보이지 않는 글자만으로 된 이름 불가(리뷰 QA-4)
  name       text not null check (
               char_length(name) between 1 and 40
               and name !~ '[\u0001-\u001f\u007f-\u009f]'
               and regexp_replace(name, '[\s\u00a0\u1680\u2000-\u200f\u2028-\u202f\u205f-\u2064\u3000\ufeff]', '', 'g') <> ''),
  families   text[] not null default '{}' check (app.valid_keys(families, 10)),
  notes      jsonb not null check (app.valid_blend_notes(notes)),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, user_id)
);
create index blends_user_id_idx on app.blends (user_id);

-- ③ 찜
create table app.favorites (
  user_id     uuid not null references auth.users (id) on delete cascade,
  perfume_key text not null check (app.valid_key(perfume_key)),
  created_at  timestamptz not null default now(),
  primary key (user_id, perfume_key)
);

-- ④ 시향 기록
create table app.tasting_feedback (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references auth.users (id) on delete cascade,
  perfume_key     text not null check (app.valid_key(perfume_key)),
  blend_id        uuid,
  satisfaction    smallint not null check (satisfaction between 1 and 5),
  similarity      smallint not null check (similarity between 1 and 5),
  memo            text check (char_length(memo) <= 500),
  tested_at       timestamptz not null default now(),
  rank            smallint check (rank between 1 and 10),
  score           double precision check (score between 0 and 1),
  engine_version  integer not null check (engine_version > 0),
  catalog_version text not null check (char_length(catalog_version) between 1 and 32),
  created_at      timestamptz not null default now(),
  -- 내 조합만 연결. 조합을 지우면 blend_id만 비운다
  foreign key (blend_id, user_id) references app.blends (id, user_id) on delete set null (blend_id)
);
create index tasting_feedback_user_id_idx on app.tasting_feedback (user_id);

-- ⑤ 사용 기록 (12개월 보관, 계정 삭제 시 user_id만 비움)
create table app.events (
  id          bigint generated always as identity primary key,
  install_id  uuid not null,
  user_id     uuid references auth.users (id) on delete set null,
  name        text not null check (name in ('app_open', 'blend_started', 'blend_summary_viewed', 'recs_viewed',
                                            'perfume_opened', 'blend_saved', 'favorite_added', 'feedback_submitted')),
  props       jsonb not null default '{}' check (jsonb_typeof(props) = 'object' and octet_length(props::text) <= 2048),
  app_version text not null check (char_length(app_version) between 1 and 32),
  occurred_at timestamptz not null,
  received_at timestamptz not null default now()
);
create index events_install_id_idx on app.events (install_id);
create index events_user_id_idx on app.events (user_id);
create index events_received_at_idx on app.events (received_at);

-- ⑥ 약관·방침 동의 (버전마다 쌓음)
create table app.consents (
  id          bigint generated always as identity primary key,
  user_id     uuid not null references auth.users (id) on delete cascade,
  doc_type    text not null check (doc_type in ('terms', 'privacy', 'age_14')),
  doc_version text not null check (char_length(doc_version) between 1 and 20),
  agreed_at   timestamptz not null default now(),
  app_version text not null check (char_length(app_version) between 1 and 32),
  unique (user_id, doc_type, doc_version)
);

create trigger user_preferences_touch before update on app.user_preferences
  for each row execute function app.touch_updated_at();
create trigger blends_touch before update on app.blends
  for each row execute function app.touch_updated_at();

-- 4. 사용 기록 정리 (pg_cron이 매일 부름. 등록은 Supabase에서만: db_design.md 13절)
create function app.purge_old_events() returns bigint
language plpgsql set search_path = '' as $$
declare
  removed bigint;
begin
  delete from app.events where received_at < now() - interval '12 months';
  get diagnostics removed = row_count;
  return removed;
end $$;

-- 5. 권한: 함수는 기본으로 누구나 실행할 수 있으므로 먼저 거둔다
revoke all on all functions in schema app from public, anon, authenticated;
grant execute on function app.valid_key(text), app.valid_keys(text[], integer), app.valid_blend_notes(jsonb) to scent_server;

revoke all on all tables in schema app from public, anon, authenticated;
revoke all on all sequences in schema app from public, anon, authenticated;
-- 칸 단위로 준다: 주인(user_id)·만든 때·서버 시각·자동 번호는 서버 계정도 못 넣고 못 바꾼다(리뷰 QA-8)
grant select, delete on app.user_preferences to scent_server;
grant insert (user_id, preferred_families, disliked_note_keys) on app.user_preferences to scent_server;
grant update (preferred_families, disliked_note_keys)          on app.user_preferences to scent_server;

grant select, delete on app.blends to scent_server;
grant insert (id, user_id, name, families, notes) on app.blends to scent_server;
grant update (name, families, notes)              on app.blends to scent_server;

grant select, delete on app.favorites to scent_server;
grant insert (user_id, perfume_key) on app.favorites to scent_server;

grant select, delete on app.tasting_feedback to scent_server;
grant insert (user_id, perfume_key, blend_id, satisfaction, similarity, memo, tested_at, rank, score,
              engine_version, catalog_version) on app.tasting_feedback to scent_server;

grant insert (install_id, user_id, name, props, app_version, occurred_at) on app.events to scent_server;

grant select on app.consents to scent_server;
grant insert (user_id, doc_type, doc_version, app_version) on app.consents to scent_server;

-- 6. RLS: 모두 켜고, 규칙은 scent_server에만. 규칙이 없는 계정(anon·authenticated)은 권한이 생겨도 아무 줄도 못 본다
alter table app.user_preferences enable row level security;
alter table app.blends           enable row level security;
alter table app.favorites        enable row level security;
alter table app.tasting_feedback enable row level security;
alter table app.events           enable row level security;
alter table app.consents         enable row level security;

create policy server_only on app.user_preferences to scent_server using (true) with check (true);
create policy server_only on app.blends           to scent_server using (true) with check (true);
create policy server_only on app.favorites        to scent_server using (true) with check (true);
create policy server_only on app.tasting_feedback to scent_server using (true) with check (true);
create policy server_only on app.events           to scent_server using (true) with check (true);
create policy server_only on app.consents         to scent_server using (true) with check (true);

-- 7. 일시 정지 방지용 (공개 키로 부름, 표를 읽지 않음)
create function public.keepalive() returns integer
language sql stable set search_path = '' as $$ select 1 $$;
revoke all on function public.keepalive() from public;
grant execute on function public.keepalive() to anon, authenticated;
