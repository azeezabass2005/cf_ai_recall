// SPEC §8 — Place resolution via Google Places Text Search (New) API.
//
// Called by the agent when the user sets a location-based reminder, and
// by the REST `/resolve-place` endpoint for the nickname edit screen.

import { z } from "zod";

export const resolvePlaceTool = {
  name: "resolve_place",
  description:
    "Resolve a free-text place query (e.g. 'Faculty of Technology', 'Shoprite') to a concrete place. Returns one match if confident, multiple matches when disambiguation is needed, or none.",
  input_schema: {
    type: "object" as const,
    properties: {
      query: { type: "string", description: "Free-text place name to search for." },
      bias_lat: { type: "number", description: "User's current latitude for proximity bias." },
      bias_lng: { type: "number", description: "User's current longitude for proximity bias." },
    },
    required: ["query"],
  },
} as const;

export interface PlaceResult {
  place_id: string;
  name: string;
  formatted_address: string;
  lat: number;
  lng: number;
}

const resolvePlaceInput = z.object({
  query: z.string().min(1),
  bias_lat: z.number().optional(),
  bias_lng: z.number().optional(),
});

/**
 * Call the Google Places Text Search (New) API and return 0–3 results.
 *
 * Disambiguation is pre-filtered here:
 *   • All results within 50 m of each other → return only the top one.
 *   • Otherwise return up to 3 for the client's disambiguation sheet.
 */
export async function handleResolvePlace(
  rawInput: unknown,
  apiKey: string | undefined,
): Promise<PlaceResult[]> {
  const input = resolvePlaceInput.parse(rawInput);

  if (!apiKey) {
    throw new Error(
      "GOOGLE_PLACES_API_KEY is not configured. " +
      "Set the secret in the Cloudflare dashboard or .dev.vars for local dev."
    );
  }

  const body: Record<string, unknown> = {
    textQuery: input.query,
    maxResultCount: 5,
  };

  if (input.bias_lat != null && input.bias_lng != null) {
    body.locationBias = {
      circle: {
        center: { latitude: input.bias_lat, longitude: input.bias_lng },
        radius: 50_000.0,
      },
    };
  }

  const resp = await fetch("https://places.googleapis.com/v1/places:searchText", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Goog-Api-Key": apiKey,
      "X-Goog-FieldMask": "places.id,places.displayName,places.formattedAddress,places.location",
    },
    body: JSON.stringify(body),
  });

  if (!resp.ok) {
    const text = await resp.text().catch(() => "(unreadable)");
    throw new Error(`Places API error ${resp.status}: ${text}`);
  }

  let json: { places?: Array<{
    id: string;
    displayName: { text: string };
    formattedAddress: string;
    location: { latitude: number; longitude: number };
  }> };

  try {
    json = await resp.json() as typeof json;
  } catch {
    const raw = await resp.text().catch(() => "");
    throw new Error(`Places API returned non-JSON: ${raw.slice(0, 200)}`);
  }

  const results: PlaceResult[] = (json.places ?? []).map((p) => ({
    place_id: p.id,
    name: p.displayName.text,
    formatted_address: p.formattedAddress,
    lat: p.location.latitude,
    lng: p.location.longitude,
  }));

  if (results.length === 0) return [];

  // If all results cluster within 50 m — they're the same place. Return only
  // the top one (Google sorts by relevance).
  if (results.length >= 2) {
    const first = results[0]!;
    const allClose = results.every(
      (r) => haversineMetres(first.lat, first.lng, r.lat, r.lng) <= 50,
    );
    if (allClose) return [first];
  }

  // Return at most 3 for the disambiguation UI.
  return results.slice(0, 3);
}

// ─── Haversine helper ──────────────────────────────────────────────────────

function haversineMetres(
  lat1: number, lng1: number,
  lat2: number, lng2: number,
): number {
  const R = 6_371_000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
