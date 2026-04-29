# `google-place-details` Edge Function

This function proxies Google Places Details + Places Photo requests so the Android app does not call Google Places REST directly.

## What it does

- `POST /functions/v1/google-place-details`
  - body: `{ "placeId": "..." }`
  - returns: `placeId`, `displayName`, `formattedAddress`, `photoName`
- `GET /functions/v1/google-place-details?photoName=...`
  - proxies the first Google Places photo as binary image bytes

## Required secret

Set a server-side Google Places web-service key in Supabase:

```bash
supabase secrets set GOOGLE_PLACES_API_KEY=YOUR_WEB_SERVICE_KEY
```

This key should be restricted to:
- `Places API (New)`

## Deploy

```bash
supabase functions deploy google-place-details
```

## Local serve

```bash
supabase functions serve google-place-details --env-file ./supabase/.env.local
```

Example `supabase/.env.local`:

```env
GOOGLE_PLACES_API_KEY=YOUR_WEB_SERVICE_KEY
```

## Notes

- The Android app calls this function using the existing `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
- Photo requests are also routed through this function, so the Google web-service key never ships inside the APK.

