// Place resolution tool — calls Google Places from the Worker. Stub.
// Real implementation lands in milestone §8.

export const resolvePlaceTool = {
  name: "resolve_place",
  description:
    "Resolve a free-text place query (e.g. 'Faculty of Technology', 'Shoprite') to a concrete place. Returns one match if confident, multiple matches when disambiguation is needed, or none.",
  input_schema: {
    type: "object",
    properties: {
      query: { type: "string" },
      bias_lat: { type: "number", description: "User's current latitude for proximity bias." },
      bias_lng: { type: "number" },
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

export async function handleResolvePlace(
  _input: unknown,
  _apiKey: string,
): Promise<PlaceResult[]> {
  throw new Error("resolve_place: not implemented (milestone §8)");
}
