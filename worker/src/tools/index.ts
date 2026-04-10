// Tool registry — the only way the LLM can affect the world.
//
// Each tool exports an Anthropic-style tool definition (name, description,
// input_schema) plus a handler. The full implementations land in milestones
// §7 (reminders), §8 (places), §10 (nicknames). For now we export the stubs
// so the agent loop has something to mount in milestone §5.

export * from "./reminders";
export * from "./places";
export * from "./nicknames";
