export type NoteType = "bug" | "feature";

export interface Note {
  id: number;
  description: string;
  location?: string;
  type: NoteType;
  components: string[];
}

export type NotesSchema = Note[];

export function isNote(obj: any): obj is Note {
  return (
    obj &&
    typeof obj.id === "number" &&
    typeof obj.description === "string" &&
    (obj.location === undefined || typeof obj.location === "string") &&
    (obj.type === "bug" || obj.type === "feature") &&
    Array.isArray(obj.components) &&
    obj.components.every((c: any) => typeof c === "string")
  );
}

export function validateNotes(data: any): data is NotesSchema {
  return Array.isArray(data) && data.every(isNote);
}