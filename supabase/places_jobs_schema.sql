-- Places + map marker RPC schema
-- Run this in Supabase SQL editor.

create table if not exists public.places (
  -- Google Place ID
  id text primary key,
  name text not null,
  address_text text,
  phone text,
  whatsapp text,
  latitude double precision not null,
  longitude double precision not null,
  latest_job_expiry timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

do $$
declare
  existing_id_type text;
begin
  select c.data_type
    into existing_id_type
  from information_schema.columns c
  where c.table_schema = 'public'
    and c.table_name = 'places'
    and c.column_name = 'id';

  if existing_id_type is not null and existing_id_type <> 'text' then
    raise exception 'public.places.id must be text (Google Place ID). Found: %', existing_id_type;
  end if;
end;
$$;

create index if not exists places_latest_job_expiry_idx
  on public.places (latest_job_expiry);

create index if not exists places_lat_lng_idx
  on public.places (latitude, longitude);

-- Rename business_place_id -> place_id (raw/source Google Place ID), if needed.
do $$
begin
  if exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'jobs'
      and column_name = 'business_place_id'
  ) and not exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'jobs'
      and column_name = 'place_id'
  ) then
    execute 'alter table public.jobs rename column business_place_id to place_id';
  end if;
end;
$$;

alter table public.jobs
  add column if not exists place_id text;

-- Existing triggers from previous runs can depend on jobs.place_id type.
drop trigger if exists trg_jobs_ensure_place on public.jobs;
drop trigger if exists trg_jobs_sync_place_after_write on public.jobs;

-- If both columns exist, copy values then drop old column.
do $$
begin
  if exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'jobs'
      and column_name = 'business_place_id'
  ) then
    execute 'update public.jobs set place_id = coalesce(nullif(trim(place_id), ''''), nullif(trim(business_place_id), ''''))';
    execute 'alter table public.jobs drop column business_place_id';
  end if;
end;
$$;

-- Ensure type is text even if previous scripts created uuid place_id.
alter table public.jobs
  alter column place_id type text using place_id::text;

-- Replace any prior foreign-key on jobs.place_id.
do $$
declare
  r record;
begin
  for r in
    select c.conname
    from pg_constraint c
    join pg_class t on t.oid = c.conrelid
    join pg_namespace n on n.oid = t.relnamespace
    join pg_attribute a on a.attrelid = t.oid and a.attnum = any(c.conkey)
    where n.nspname = 'public'
      and t.relname = 'jobs'
      and c.contype = 'f'
      and a.attname = 'place_id'
  loop
    execute format('alter table public.jobs drop constraint if exists %I', r.conname);
  end loop;
end;
$$;

-- Precreate missing places for existing non-null jobs.place_id before FK is enforced.
insert into public.places (
  id,
  name,
  address_text,
  phone,
  whatsapp,
  latitude,
  longitude,
  latest_job_expiry,
  updated_at
)
select distinct on (j.place_id)
  j.place_id,
  coalesce(nullif(trim(j.business_name), ''), 'Unknown Business') as name,
  nullif(trim(j.address_text), '') as address_text,
  nullif(trim(j.phone), '') as phone,
  nullif(trim(j.whatsapp), '') as whatsapp,
  0::double precision as latitude,
  0::double precision as longitude,
  case when coalesce(j.is_active, false) then j.expires_at else null end as latest_job_expiry,
  now()
from public.jobs j
where j.place_id is not null
  and not exists (
    select 1 from public.places p where p.id = j.place_id
  )
order by j.place_id, j.expires_at desc nulls last, j.created_at desc nulls last
on conflict (id) do nothing;

alter table public.jobs
  add constraint jobs_place_id_fkey
  foreign key (place_id) references public.places(id) on delete set null
  not valid;

create index if not exists jobs_place_id_idx
  on public.jobs(place_id);

-- Assign/ensure place row before writing a job row.
create or replace function public.ensure_place_for_job()
returns trigger
language plpgsql
as $$
declare
  resolved_place_id text;
  resolved_name text;
  resolved_lat double precision;
  resolved_lng double precision;
begin
  resolved_name := coalesce(nullif(trim(new.business_name), ''), 'Unknown Business');
  resolved_lat := 0;
  resolved_lng := 0;
  resolved_place_id := nullif(trim(coalesce(new.place_id, '')), '');

  -- Primary path: use provided Google Place ID.
  if resolved_place_id is not null then
    select p.latitude, p.longitude
      into resolved_lat, resolved_lng
    from public.places p
    where p.id = resolved_place_id
    limit 1;

    resolved_lat := coalesce(resolved_lat, 0);
    resolved_lng := coalesce(resolved_lng, 0);

    insert into public.places (
      id,
      name,
      address_text,
      phone,
      whatsapp,
      latitude,
      longitude,
      latest_job_expiry,
      updated_at
    )
    values (
      resolved_place_id,
      resolved_name,
      new.address_text,
      new.phone,
      new.whatsapp,
      resolved_lat,
      resolved_lng,
      case when coalesce(new.is_active, false) then new.expires_at else null end,
      now()
    )
    on conflict (id) do update
      set name = excluded.name,
          address_text = coalesce(excluded.address_text, public.places.address_text),
          phone = coalesce(excluded.phone, public.places.phone),
          whatsapp = coalesce(excluded.whatsapp, public.places.whatsapp),
          updated_at = now();

    new.place_id := resolved_place_id;
    return new;
  end if;

  -- Fallback path: missing Google Place ID, resolve by name + coordinates.
  select p.id, p.latitude, p.longitude
    into resolved_place_id, resolved_lat, resolved_lng
  from public.places p
  where p.name = resolved_name
    and coalesce(nullif(trim(p.address_text), ''), '') = coalesce(nullif(trim(new.address_text), ''), '')
  limit 1;

  if resolved_place_id is null then
    resolved_place_id := 'fallback_' || md5(resolved_name || '|' || coalesce(new.address_text, ''));
    resolved_lat := 0;
    resolved_lng := 0;

    insert into public.places (
      id,
      name,
      address_text,
      phone,
      whatsapp,
      latitude,
      longitude,
      latest_job_expiry,
      updated_at
    )
    values (
      resolved_place_id,
      resolved_name,
      new.address_text,
      new.phone,
      new.whatsapp,
      resolved_lat,
      resolved_lng,
      case when coalesce(new.is_active, false) then new.expires_at else null end,
      now()
    )
    on conflict (id) do nothing;
  end if;

  new.place_id := resolved_place_id;
  return new;
end;
$$;

drop trigger if exists trg_jobs_ensure_place on public.jobs;
create trigger trg_jobs_ensure_place
before insert or update of place_id, business_name, address_text, phone, whatsapp
on public.jobs
for each row
execute function public.ensure_place_for_job();

-- Recompute latest active job expiry for a place.
create or replace function public.refresh_place_latest_job_expiry(p_place_id text)
returns void
language plpgsql
as $$
declare
  latest_expiry timestamptz;
begin
  if p_place_id is null then
    return;
  end if;

  select max(j.expires_at)
    into latest_expiry
  from public.jobs j
  where j.place_id = p_place_id
    and coalesce(j.is_active, false) = true
    and j.expires_at > now();

  update public.places p
  set latest_job_expiry = latest_expiry,
      updated_at = now()
  where p.id = p_place_id;
end;
$$;

create or replace function public.sync_place_after_job_write()
returns trigger
language plpgsql
as $$
begin
  if tg_op in ('INSERT', 'UPDATE') then
    perform public.refresh_place_latest_job_expiry(new.place_id);
  end if;

  if tg_op in ('UPDATE', 'DELETE') then
    perform public.refresh_place_latest_job_expiry(old.place_id);
  end if;

  return coalesce(new, old);
end;
$$;

drop trigger if exists trg_jobs_sync_place_after_write on public.jobs;
create trigger trg_jobs_sync_place_after_write
after insert or update of place_id, expires_at, is_active or delete
on public.jobs
for each row
execute function public.sync_place_after_job_write();

-- Backfill existing rows so triggers normalize place_id + latest_job_expiry.
update public.jobs j
set place_id = coalesce(nullif(trim(j.place_id), ''), null)
where true;

alter table public.jobs validate constraint jobs_place_id_fkey;

-- Nearby jobs RPC (compatible with text UUID/string ids).
drop function if exists public.get_nearby_jobs(
  double precision,
  double precision,
  integer
);

drop function if exists public.get_nearby_jobs(
  double precision,
  double precision,
  integer,
  integer,
  integer
);

create or replace function public.get_nearby_jobs(
  lat double precision,
  lng double precision,
  radius integer,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table (
  id text,
  title text,
  business_name text,
  description text,
  job_type text,
  pay_text text,
  address_text text,
  whatsapp text,
  phone text,
  created_at timestamptz,
  expires_at timestamptz,
  place_id text,
  latitude double precision,
  longitude double precision,
  distance double precision
)
language sql
stable
as $$
with user_location as (
  select st_makepoint(lng, lat)::geography as point
)
select
  j.id::text as id,
  j.title,
  j.business_name,
  j.description,
  j.job_type,
  j.pay_text,
  j.address_text,
  j.whatsapp,
  j.phone,
  j.created_at,
  j.expires_at,
  j.place_id,
  p.latitude,
  p.longitude,
  st_distance(st_makepoint(p.longitude, p.latitude)::geography, ul.point) as distance
from public.jobs j
join public.places p on p.id = j.place_id
cross join user_location ul
where coalesce(j.is_active, false) = true
  and j.expires_at > now()
  and st_dwithin(st_makepoint(p.longitude, p.latitude)::geography, ul.point, radius)
order by distance asc
limit greatest(p_limit, 1)
offset greatest(p_offset, 0);
$$;

grant execute on function public.get_nearby_jobs(
  double precision,
  double precision,
  integer,
  integer,
  integer
) to anon, authenticated;

-- Jobs by place RPC (aligned with get_nearby_jobs response fields).
drop function if exists public.get_jobs_by_place(
  text,
  integer,
  integer
);

create or replace function public.get_jobs_by_place(
  p_place_id text,
  p_limit integer default 20,
  p_offset integer default 0
)
returns table (
  id text,
  title text,
  business_name text,
  description text,
  job_type text,
  pay_text text,
  address_text text,
  whatsapp text,
  phone text,
  created_at timestamptz,
  expires_at timestamptz,
  place_id text,
  latitude double precision,
  longitude double precision
)
language sql
stable
as $$
select
  j.id::text as id,
  j.title,
  j.business_name,
  j.description,
  j.job_type,
  j.pay_text,
  j.address_text,
  j.whatsapp,
  j.phone,
  j.created_at,
  j.expires_at,
  j.place_id,
  p.latitude,
  p.longitude
from public.jobs j
join public.places p on p.id = j.place_id
where j.place_id = p_place_id
  and coalesce(j.is_active, false) = true
  and j.expires_at > now()
order by j.expires_at asc, j.created_at desc
limit greatest(p_limit, 1)
offset greatest(p_offset, 0);
$$;

grant execute on function public.get_jobs_by_place(
  text,
  integer,
  integer
) to anon, authenticated;

alter table public.jobs
  drop column if exists location;

-- Minimal markers endpoint for map viewport/tile fetching.
drop function if exists public.get_place_w_jobs_map_markers_in_bounds(
  double precision,
  double precision,
  double precision,
  double precision
);

create or replace function public.get_place_w_jobs_map_markers_in_bounds(
  min_lat double precision,
  min_lng double precision,
  max_lat double precision,
  max_lng double precision
)
returns table (
  place_id text,
  latitude double precision,
  longitude double precision,
  name text
)
language sql
stable
as $$
  select
    p.id as place_id,
    p.latitude,
    p.longitude,
    p.name
  from public.places p
  where p.latest_job_expiry > now()
    and p.latitude between min_lat and max_lat
    and p.longitude between min_lng and max_lng
  order by p.latitude, p.longitude;
$$;





