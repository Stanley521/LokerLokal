# Supabase Auth Setup

This project now includes a minimal Supabase Auth foundation for the job apply flow.

## Files

- `supabase/auth_schema.sql`
- `supabase/config.toml`

## What it creates

- `public.profiles` linked to `auth.users(id)`
- A trigger that auto-creates a profile row after signup
- RLS policies so users can only access their own profile
- Helper functions:
  - `public.current_user_id()`
  - `public.current_jwt_claims()`

## Setup steps

1. Enable Supabase Auth in your project.
2. Run `supabase/auth_schema.sql` in the Supabase SQL editor.
3. Keep `verify_jwt = false` for `google-place-details` until the Android client sends JWTs.
4. When the app is ready, flip `verify_jwt = true` and make the Android app send the signed-in user's access token as:

```http
Authorization: Bearer <user_access_token>
```

## Recommended next app change

Replace the current Android-ID fallback in `JobApplyTabFragment.kt` with the logged-in Supabase user id.

## JWT note

Supabase already issues JWTs for authenticated sessions. You usually do **not** create your own JWT signing function in SQL.

