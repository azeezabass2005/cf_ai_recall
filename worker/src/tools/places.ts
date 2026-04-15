// SPEC §8 — Place resolution via OpenStreetMap Nominatim API.
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
 * Call the OpenStreetMap Nominatim Search API and return 0–3 results.
 *
 * Disambiguation is pre-filtered here:
 *   • All results within 50 m of each other → return only the top one.
 *   • Otherwise return up to 3 for the client's disambiguation sheet.
 */
export async function handleResolvePlace(
  rawInput: unknown,
  _apiKey?: string, // Kept for backwards compatibility if needed, but not used.
): Promise<PlaceResult[]> {
  const input = resolvePlaceInput.parse(rawInput);

  const url = new URL("https://nominatim.openstreetmap.org/search");
  url.searchParams.set("q", input.query);
  url.searchParams.set("format", "json");
  url.searchParams.set("limit", "5");

  // Approximate a 50km bounding box around the user for bias
  if (input.bias_lat != null && input.bias_lng != null) {
    const lat = input.bias_lat;
    const lng = input.bias_lng;
    const offset = 0.45; // roughly 50km
    
    const left = lng - offset;
    const top = lat + offset;
    const right = lng + offset;
    const bottom = lat - offset;
    
    url.searchParams.set("viewbox", `${left},${top},${right},${bottom}`);
    url.searchParams.set("bounded", "0"); // Prefer results in box, but allow others
  }

  const resp = await fetch(url.toString(), {
    headers: {
      // Nominatim STRICTLY requires a unique User-Agent
      "User-Agent": "RantiWorker/1.0 (reminder-app-bot)",
    },
  });

  if (!resp.ok) {
    const text = await resp.text().catch(() => "(unreadable)");
    throw new Error(`Nominatim API error ${resp.status}: ${text}`);
  }

  let json: Array<{
    place_id: number;
    display_name: string;
    name: string;
    lat: string;
    lon: string;
  }>;

  try {
    json = await resp.json() as typeof json;
  } catch {
    const raw = await resp.text().catch(() => "");
    throw new Error(`Nominatim API returned non-JSON: ${raw.slice(0, 200)}`);
  }

  const results: PlaceResult[] = json.map((p) => {
    // If the short name is empty or identical to the display name, try to extract the first part of display_name
    let shortName = p.name;
    if (!shortName) {
        shortName = p.display_name.split(",")[0] || p.display_name;
    }
    
    return {
      place_id: String(p.place_id),
      name: shortName,
      formatted_address: p.display_name,
      lat: Number(p.lat),
      lng: Number(p.lon),
    };
  });

  if (results.length === 0) return [];

  // If all results cluster within 50 m — they're the same place. Return only
  // the top one.
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
