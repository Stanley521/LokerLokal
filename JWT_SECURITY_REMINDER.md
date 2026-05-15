# ⚠️ PRODUCTION READINESS CHECKLIST

## Critical Security Item - JWT Verification

### Current Status
- **Location**: `supabase/config.toml`
- **Edge Function**: `google-place-details`
- **Current Setting**: `verify_jwt = false` ⚠️
- **Status**: Anonymous access still allowed for compatibility

### What This Means
- ✅ The app can keep calling `google-place-details` without breaking today
- ⚠️ The function is still public until the client starts sending JWTs
- ✅ The auth foundation is ready in `supabase/auth_schema.sql`

### Pre-Production Checklist
```
BEFORE RELEASING TO PRODUCTION:

□ Verify the Android app sends `Authorization: Bearer <user_access_token>`
□ Confirm the `public.profiles` trigger creates rows for new users
□ Validate RLS policies on user-owned tables
□ Switch `verify_jwt = true` before shipping protected edge functions

□ Run edge function tests with proper JWT tokens
□ Verify authentication flow works end-to-end
□ Test with production Supabase keys
□ Ensure API key restrictions are properly set
□ Review Supabase security policies
□ Enable Rate Limiting on edge functions
□ Set up logging and monitoring
□ Test with real devices before release
```

### How to Enable JWT Verification

1. Open `supabase/config.toml`
2. Locate the `[functions.google-place-details]` section
3. Change `verify_jwt = false` to `verify_jwt = true` when the app sends JWTs
4. Save and sync with Supabase:
   ```bash
   supabase functions deploy google-place-details
   ```

### Auth schema reference

- `supabase/auth_schema.sql` creates the `public.profiles` table
- It auto-creates profiles from `auth.users`
- It includes helper functions for `auth.uid()` and `auth.jwt()`

### Other Security Reminders

- [ ] Validate all API keys have appropriate scopes (not Admin level for public use)
- [ ] Implement rate limiting on edge functions
- [ ] Use secure environment variables for all secrets
- [ ] Enable CORS restrictions to your domain only
- [ ] Set up request/response logging
- [ ] Regular security audits of Supabase setup
- [ ] Monitor API usage for anomalies
- [ ] Keep Android Gradle Plugin and dependencies updated
- [ ] Enable ProGuard/R8 for release builds

### Reference Documentation
- [Supabase Edge Functions Security](https://supabase.com/docs/guides/functions/auth)
- [JWT Implementation Guide](https://supabase.com/docs/learn/auth-deep-dive/jwts)
- [API Keys Best Practices](https://supabase.com/docs/guides/api/api-keys-and-secrets)

---

**REMINDER**: This warning will appear in logs during build as a reminder to address before shipping.

