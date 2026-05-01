# Resume Upload + WhatsApp Apply Setup

This project now includes a marker-tap job apply modal with PDF resume upload and WhatsApp sharing.

## 1) Apply database/storage schema
Run SQL in Supabase SQL Editor:

```sql
-- use file: supabase/resume_schema.sql
```

## 2) Notes

- PDF max size is limited to 500KB in app validation.
- Resume metadata is stored in `public.resume_files`.
- Resume files are stored in bucket `resumes`.
- Current policies are MVP-open (`anon` + `authenticated`) for faster iteration.
  Tighten policies once auth is enabled.

## 3) WhatsApp behavior

- App opens WhatsApp specifically (`com.whatsapp`).
- If WhatsApp is missing, the app shows a toast to install it.
- Message + attached resume PDF are prefilled; user taps send.

