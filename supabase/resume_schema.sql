-- Resume metadata table for WhatsApp apply flow
-- Run in Supabase SQL Editor

create table if not exists public.resume_files (
  user_key text primary key,
  file_name text not null,
  file_path text not null,
  size_bytes bigint not null,
  updated_at timestamptz not null default now()
);

alter table public.resume_files enable row level security;

-- MVP policy: allow anon/authenticated access. Tighten later when auth is enabled.
drop policy if exists "resume_files public access" on public.resume_files;
create policy "resume_files public access"
  on public.resume_files
  for all
  to anon, authenticated
  using (true)
  with check (true);

-- Storage bucket for resume PDFs
insert into storage.buckets (id, name, public)
values ('resumes', 'resumes', false)
on conflict (id) do nothing;

-- MVP storage policies. Tighten later with auth-based ownership checks.
drop policy if exists "resumes public read" on storage.objects;
create policy "resumes public read"
  on storage.objects
  for select
  to anon, authenticated
  using (bucket_id = 'resumes');

drop policy if exists "resumes public write" on storage.objects;
create policy "resumes public write"
  on storage.objects
  for insert
  to anon, authenticated
  with check (bucket_id = 'resumes');

drop policy if exists "resumes public update" on storage.objects;
create policy "resumes public update"
  on storage.objects
  for update
  to anon, authenticated
  using (bucket_id = 'resumes')
  with check (bucket_id = 'resumes');

