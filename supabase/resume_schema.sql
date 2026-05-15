-- Resume metadata table for WhatsApp apply flow
-- Auth-aware MVP: requires authenticated Supabase users.
-- Run in Supabase SQL Editor

create table if not exists public.resume_files (
  user_key text primary key,
  file_name text not null,
  file_path text not null,
  size_bytes bigint not null,
  updated_at timestamptz not null default now()
);

alter table public.resume_files enable row level security;

-- Auth policy: only the owning authenticated user can access their resume metadata.
drop policy if exists "resume_files public access" on public.resume_files;
create policy "resume_files public access"
  on public.resume_files
  for all
  to authenticated
  using (user_key = auth.uid()::text)
  with check (user_key = auth.uid()::text);

-- Storage bucket for resume PDFs
insert into storage.buckets (id, name, public)
values ('resumes', 'resumes', false)
on conflict (id) do nothing;

-- Auth storage policies: only authenticated users can manage files in the resumes bucket.
drop policy if exists "resumes public read" on storage.objects;
create policy "resumes public read"
  on storage.objects
  for select
  to authenticated
  using (bucket_id = 'resumes' and auth.uid()::text = split_part(name, '/', 1));

drop policy if exists "resumes public write" on storage.objects;
create policy "resumes public write"
  on storage.objects
  for insert
  to authenticated
  with check (bucket_id = 'resumes' and auth.uid()::text = split_part(name, '/', 1));

drop policy if exists "resumes public update" on storage.objects;
create policy "resumes public update"
  on storage.objects
  for update
  to authenticated
  using (bucket_id = 'resumes' and auth.uid()::text = split_part(name, '/', 1))
  with check (bucket_id = 'resumes' and auth.uid()::text = split_part(name, '/', 1));

