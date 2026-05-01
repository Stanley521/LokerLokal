import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const DETAILS_FIELD_MASK = "id,displayName,formattedAddress,photos";

type PlaceDetailsResponse = {
  placeId: string;
  displayName: string;
  formattedAddress: string;
  photoName: string | null;
  photoNames: string[];
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const googlePlacesApiKey = Deno.env.get("GOOGLE_PLACES_API_KEY")?.trim() ?? "";
  if (!googlePlacesApiKey) {
    return jsonResponse({ error: "GOOGLE_PLACES_API_KEY is not configured" }, 500);
  }

  const url = new URL(req.url);
  const photoName = url.searchParams.get("photoName")?.trim() ?? "";

  if (req.method === "GET" && photoName) {
    return await proxyPlacePhoto(photoName, googlePlacesApiKey);
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  let placeId = "";
  try {
    const body = await req.json();
    placeId = String(body?.placeId ?? "").trim();
  } catch {
    return jsonResponse({ error: "Invalid JSON body" }, 400);
  }

  if (!placeId) {
    return jsonResponse({ error: "placeId is required" }, 400);
  }

  const encodedPlaceId = encodeURIComponent(placeId);
  const response = await fetch(`https://places.googleapis.com/v1/places/${encodedPlaceId}`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": googlePlacesApiKey,
      "X-Goog-FieldMask": DETAILS_FIELD_MASK,
    },
  });

  const bodyText = await response.text();
  console.log(`[google-place-details] details response status=${response.status} placeId=${placeId} body=${bodyText.slice(0, 800)}`);

  if (!response.ok) {
    return new Response(bodyText || JSON.stringify({ error: "Failed to fetch place details" }), {
      status: response.status,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
      },
    });
  }

  const json = JSON.parse(bodyText);
  const result: PlaceDetailsResponse = {
    placeId: json.id || placeId,
    displayName: json.displayName?.text || "",
    formattedAddress: json.formattedAddress || "",
    photoName: json.photos?.[0]?.name || null,
    photoNames: Array.isArray(json.photos)
      ? json.photos
          .map((photo: { name?: string }) => String(photo?.name || "").trim())
          .filter((name: string) => name.length > 0)
      : [],
  };

  return jsonResponse(result, 200);
});

async function proxyPlacePhoto(photoName: string, apiKey: string): Promise<Response> {
  const encodedPhotoName = photoName
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
  const response = await fetch(
    `https://places.googleapis.com/v1/${encodedPhotoName}/media?maxHeightPx=360&maxWidthPx=600`,
    {
      method: "GET",
      headers: {
        "X-Goog-Api-Key": apiKey,
      },
    },
  );

  if (!response.ok) {
    const bodyText = await response.text();
    console.error(`[google-place-details] photo proxy failed status=${response.status} photoName=${photoName} body=${bodyText.slice(0, 500)}`);
    return new Response(bodyText || JSON.stringify({ error: "Failed to fetch place photo" }), {
      status: response.status,
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json",
      },
    });
  }

  const contentType = response.headers.get("content-type") || "image/jpeg";
  console.log(`[google-place-details] photo proxy success photoName=${photoName} contentType=${contentType}`);
  const bytes = await response.arrayBuffer();
  return new Response(bytes, {
    status: 200,
    headers: {
      ...corsHeaders,
      "Content-Type": contentType,
      "Cache-Control": "public, max-age=3600",
    },
  });
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}


