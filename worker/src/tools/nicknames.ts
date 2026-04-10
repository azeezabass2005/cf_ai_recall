// Nickname tools — stubs. Real implementations in milestone §10.

export const saveNicknameTool = {
  name: "save_nickname",
  description:
    "Save a personal nickname for a place (e.g. 'my hostel' → Adekunle Fajuyi Hall of Residence).",
  input_schema: {
    type: "object",
    properties: {
      nickname: { type: "string" },
      place_id: { type: "string" },
      place_name: { type: "string" },
      lat: { type: "number" },
      lng: { type: "number" },
    },
    required: ["nickname", "place_name", "lat", "lng"],
  },
} as const;

export const getNicknamesTool = {
  name: "get_nicknames",
  description: "List the user's saved place nicknames so the agent can resolve 'my hostel' etc.",
  input_schema: { type: "object", properties: {} },
} as const;
