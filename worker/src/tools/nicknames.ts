// SPEC §10 — Place nickname tools backed by D1.
//
// Nicknames let users say "remind me when I get to my hostel" — the agent
// resolves "my hostel" to the saved place coordinates and creates a
// location-based reminder without needing a resolve_place round-trip.

import { z } from "zod";
import {
  upsertNickname,
  selectNicknames,
  selectNicknameByText,
  deleteNickname as deleteNicknameQuery,
} from "../db/queries";
import type { Nickname } from "../lib/types";

// ─── Tool schemas (Anthropic tool_use format) ───────────────────────────────

export const saveNicknameTool = {
  name: "save_nickname",
  description:
    "Save a personal nickname for a place (e.g. 'my hostel' → Adekunle Fajuyi Hall of Residence). If the nickname already exists for this user, it will be updated with the new place.",
  input_schema: {
    type: "object" as const,
    properties: {
      nickname: {
        type: "string",
        description: "The user's nickname for the place (e.g. 'my hostel', 'home', 'the shop').",
      },
      place_id: {
        type: "string",
        description: "Google Places place_id, if available.",
      },
      place_name: {
        type: "string",
        description: "Human-readable place name (e.g. 'Adekunle Fajuyi Hall').",
      },
      lat: { type: "number", description: "Latitude of the place." },
      lng: { type: "number", description: "Longitude of the place." },
    },
    required: ["nickname", "place_name", "lat", "lng"],
  },
} as const;

export const getNicknamesTool = {
  name: "get_nicknames",
  description:
    "List the user's saved place nicknames so the agent can resolve 'my hostel' etc. without calling resolve_place. Returns all nicknames for the current device.",
  input_schema: {
    type: "object" as const,
    properties: {},
  },
} as const;

export const deleteNicknameTool = {
  name: "delete_nickname",
  description: "Delete a saved place nickname by its nickname text.",
  input_schema: {
    type: "object" as const,
    properties: {
      nickname: {
        type: "string",
        description: "The nickname to delete (e.g. 'my hostel').",
      },
    },
    required: ["nickname"],
  },
} as const;

// ─── Zod schemas ────────────────────────────────────────────────────────────

const saveNicknameInput = z.object({
  nickname: z.string().min(1).max(100),
  place_id: z.string().optional(),
  place_name: z.string().min(1).max(300),
  lat: z.number(),
  lng: z.number(),
});

const deleteNicknameInput = z.object({
  nickname: z.string().min(1),
});

// Row types and D1 queries now live in db/queries.ts.

// ─── Handler context ────────────────────────────────────────────────────────

export interface NicknameHandlerContext {
  db: D1Database;
  deviceId: string;
}

// ─── Handlers ───────────────────────────────────────────────────────────────

/**
 * UPSERT a nickname. If the (device_id, nickname) pair already exists,
 * update the place info. Otherwise insert a new row.
 */
export async function handleSaveNickname(
  rawInput: unknown,
  ctx: NicknameHandlerContext,
): Promise<{ nickname: Nickname }> {
  const input = saveNicknameInput.parse(rawInput);
  const id = crypto.randomUUID();

  const nickname = await upsertNickname(ctx.db, {
    id,
    deviceId: ctx.deviceId,
    nickname: input.nickname,
    placeName: input.place_name,
    placeId: input.place_id ?? null,
    lat: input.lat,
    lng: input.lng,
  });

  return { nickname };
}

/**
 * List all nicknames for the current device.
 */
export async function handleGetNicknames(
  _rawInput: unknown,
  ctx: NicknameHandlerContext,
): Promise<{ nicknames: Nickname[] }> {
  const nicknames = await selectNicknames(ctx.db, ctx.deviceId);
  return { nicknames };
}

/**
 * Delete a nickname by its text. Case-insensitive match.
 */
export async function handleDeleteNickname(
  rawInput: unknown,
  ctx: NicknameHandlerContext,
): Promise<{ deleted: Nickname | null }> {
  const input = deleteNicknameInput.parse(rawInput);

  const found = await selectNicknameByText(ctx.db, ctx.deviceId, input.nickname);
  if (!found) return { deleted: null };

  await deleteNicknameQuery(ctx.db, found.id, ctx.deviceId);
  return { deleted: found };
}

// ─── Tool dispatch table ────────────────────────────────────────────────────

export const nicknameTools = [
  saveNicknameTool,
  getNicknamesTool,
  deleteNicknameTool,
] as const;

export type NicknameToolName = "save_nickname" | "get_nicknames" | "delete_nickname";

export async function dispatchNicknameTool(
  name: string,
  input: unknown,
  ctx: NicknameHandlerContext,
): Promise<unknown> {
  switch (name as NicknameToolName) {
    case "save_nickname":   return handleSaveNickname(input, ctx);
    case "get_nicknames":   return handleGetNicknames(input, ctx);
    case "delete_nickname": return handleDeleteNickname(input, ctx);
    default:
      throw new Error(`Unknown nickname tool: ${name}`);
  }
}
