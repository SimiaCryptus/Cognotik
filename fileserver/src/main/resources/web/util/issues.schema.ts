/**
 * Session-local issue tracker schema.
 *
 * Storage contract
 * ----------------
 * Every app session directory may contain a single `issues.json` document that is read/written
 * through the shared file API (`/app/fileIO.js` -> `GET|PUT {basePath}/issues.json`).
 * `util/issues.html` is copied into each new session and is the reference UI for this document.
 *
 * The on-disk shape is *forward tolerant*: unknown fields are preserved in `meta` bags where
 * possible and normalisation never throws — a corrupt or partial file degrades to a valid empty
 * document plus a list of validation warnings.
 *
 * NOTE: `util/issues.html` embeds a JavaScript mirror of the runtime helpers in this file
 * (constants, normalise/validate/filter/sort/summarise). Keep the two in sync when editing.
 */

/* ------------------------------------------------------------------ *
 * Constants
 * ------------------------------------------------------------------ */

export const ISSUES_SCHEMA_VERSION = 1;
export const ISSUES_FILE_NAME = 'issues.json';
export const DEFAULT_ID_PREFIX = 'ISS';

export const ISSUE_STATUSES = [
    'open',
    'in_progress',
    'blocked',
    'resolved',
    'closed',
    'wontfix'
] as const;
export type IssueStatus = (typeof ISSUE_STATUSES)[number];

/** Statuses that still require work. */
export const ACTIVE_STATUSES: readonly IssueStatus[] = ['open', 'in_progress', 'blocked'];
/** Statuses that stop the clock (`closedAt` is stamped when an issue enters one of these). */
export const TERMINAL_STATUSES: readonly IssueStatus[] = ['resolved', 'closed', 'wontfix'];

export const ISSUE_PRIORITIES = ['critical', 'high', 'medium', 'low'] as const;
export type IssuePriority = (typeof ISSUE_PRIORITIES)[number];

export const ISSUE_TYPES = ['bug', 'feature', 'task', 'question', 'docs', 'chore'] as const;
export type IssueType = (typeof ISSUE_TYPES)[number];

export const ISSUE_SORTS = [
    'updated_desc',
    'updated_asc',
    'created_desc',
    'created_asc',
    'priority_desc',
    'status_asc',
    'number_asc',
    'number_desc',
    'title_asc'
] as const;
export type IssueSort = (typeof ISSUE_SORTS)[number];

/** Lower is more urgent — used for `priority_desc` ordering. */
export const PRIORITY_RANK: Record<IssuePriority, number> = {
    critical: 0,
    high: 1,
    medium: 2,
    low: 3
};

export const STATUS_RANK: Record<IssueStatus, number> = {
    open: 0,
    in_progress: 1,
    blocked: 2,
    resolved: 3,
    closed: 4,
    wontfix: 5
};

/* ------------------------------------------------------------------ *
 * Types
 * ------------------------------------------------------------------ */

/** A single threaded comment on an issue. */
export interface IssueComment {
    /** Unique within the parent issue. */
    id: string;
    /** Free-form author name; `null` when unattributed. */
    author: string | null;
    /** Markdown. */
    body: string;
    /** ISO-8601 timestamp. */
    createdAt: string;
    /** ISO-8601 timestamp of the last edit, `null` if never edited. */
    updatedAt: string | null;
}

/** A pointer to a file inside the same session (relative to `basePath`). */
export interface IssueFileRef {
    /** Session-relative path, e.g. `slides/deck.md`. */
    path: string;
    /** Optional human note ("failing input", "screenshot", …). */
    note: string | null;
}

/** An append-only audit record. */
export interface IssueEvent {
    /** ISO-8601 timestamp. */
    at: string;
    actor: string | null;
    /** `created` | `status` | `priority` | `assigned` | `comment` | `edited` | string */
    kind: string;
    from: string | null;
    to: string | null;
    note: string | null;
}

export interface Issue {
    /** Stable, human readable identifier — `${idPrefix}-${number}`, e.g. `ISS-12`. */
    id: string;
    /** Monotonic per-document counter; never reused. */
    number: number;
    title: string;
    /** Markdown description. */
    body: string;
    status: IssueStatus;
    priority: IssuePriority;
    type: IssueType;
    /** Free-form tags; matched case-insensitively by the UI. */
    labels: string[];
    assignee: string | null;
    reporter: string | null;
    /** ISO-8601. */
    createdAt: string;
    /** ISO-8601, bumped by `touchIssue()`. */
    updatedAt: string;
    /** ISO-8601, set when the issue enters a terminal status, cleared when it leaves one. */
    closedAt: string | null;
    /** `YYYY-MM-DD` or `null`. */
    dueDate: string | null;
    /** Estimate in hours, `null` when unknown. */
    estimate: number | null;
    /** `Issue.id` of the parent issue, or `null`. */
    parentId: string | null;
    /** `Issue.id`s of related issues. */
    relatedIds: string[];
    files: IssueFileRef[];
    comments: IssueComment[];
    history: IssueEvent[];
    /** Anything app-specific; round-tripped untouched. */
    meta: Record<string, unknown>;
}

export interface LabelDef {
    name: string;
    /** CSS colour (`#rrggbb`), or `null` for the default chip styling. */
    color: string | null;
    description: string | null;
}

export interface IssuesDocument {
    schemaVersion: number;
    createdAt: string;
    /** Bumped on every write; used for optimistic concurrency checks. */
    updatedAt: string;
    /** Number handed to the next created issue. */
    nextNumber: number;
    idPrefix: string;
    labels: LabelDef[];
    issues: Issue[];
    meta: Record<string, unknown>;
}

export interface IssueFilter {
    /** Free-text needle (title / body / labels / comments), already lower-cased. */
    text: string;
    /** `'all'`, `'active'` (any of {@link ACTIVE_STATUSES}) or a concrete status. */
    status: 'all' | 'active' | IssueStatus;
    priority: 'all' | IssuePriority;
    type: 'all' | IssueType;
    /** `'all'`, `'none'`, or a label name (case-insensitive). */
    label: string;
    /** `'all'`, `'none'`, or an assignee name (case-insensitive). */
    assignee: string;
}

export interface ValidationIssue {
    /** JSON-pointer-ish location, e.g. `issues[3].status`. */
    path: string;
    message: string;
}

export interface ValidationResult {
    /** `true` when the input needed no repairs. */
    ok: boolean;
    errors: ValidationIssue[];
    /** Always a usable document, even when `ok` is `false`. */
    value: IssuesDocument;
}

export interface IssuesSummary {
    total: number;
    active: number;
    done: number;
    byStatus: Record<IssueStatus, number>;
    byPriority: Record<IssuePriority, number>;
    overdue: number;
}

/* ------------------------------------------------------------------ *
 * JSON Schema (draft-07) — for external validators / editors
 * ------------------------------------------------------------------ */

export const ISSUES_JSON_SCHEMA = {
    $schema: 'http://json-schema.org/draft-07/schema#',
    $id: 'https://cognotik.local/schemas/issues.json',
    title: 'Session issues document',
    type: 'object',
    required: ['schemaVersion', 'issues'],
    additionalProperties: true,
    properties: {
        schemaVersion: {type: 'integer', minimum: 1},
        createdAt: {type: 'string', format: 'date-time'},
        updatedAt: {type: 'string', format: 'date-time'},
        nextNumber: {type: 'integer', minimum: 1},
        idPrefix: {type: 'string', pattern: '^[A-Za-z][A-Za-z0-9_]*$'},
        meta: {type: 'object'},
        labels: {
            type: 'array',
            items: {
                type: 'object',
                required: ['name'],
                properties: {
                    name: {type: 'string', minLength: 1},
                    color: {type: ['string', 'null'], pattern: '^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$'},
                    description: {type: ['string', 'null']}
                }
            }
        },
        issues: {
            type: 'array',
            items: {$ref: '#/definitions/issue'}
        }
    },
    definitions: {
        issue: {
            type: 'object',
            required: ['id', 'number', 'title', 'status'],
            additionalProperties: true,
            properties: {
                id: {type: 'string', minLength: 1},
                number: {type: 'integer', minimum: 1},
                title: {type: 'string'},
                body: {type: 'string'},
                status: {enum: [...ISSUE_STATUSES]},
                priority: {enum: [...ISSUE_PRIORITIES]},
                type: {enum: [...ISSUE_TYPES]},
                labels: {type: 'array', items: {type: 'string'}},
                assignee: {type: ['string', 'null']},
                reporter: {type: ['string', 'null']},
                createdAt: {type: 'string', format: 'date-time'},
                updatedAt: {type: 'string', format: 'date-time'},
                closedAt: {type: ['string', 'null'], format: 'date-time'},
                dueDate: {type: ['string', 'null'], pattern: '^\\d{4}-\\d{2}-\\d{2}$'},
                estimate: {type: ['number', 'null'], minimum: 0},
                parentId: {type: ['string', 'null']},
                relatedIds: {type: 'array', items: {type: 'string'}},
                meta: {type: 'object'},
                files: {
                    type: 'array',
                    items: {
                        type: 'object',
                        required: ['path'],
                        properties: {
                            path: {type: 'string', minLength: 1},
                            note: {type: ['string', 'null']}
                        }
                    }
                },
                comments: {
                    type: 'array',
                    items: {
                        type: 'object',
                        required: ['id', 'body', 'createdAt'],
                        properties: {
                            id: {type: 'string', minLength: 1},
                            author: {type: ['string', 'null']},
                            body: {type: 'string'},
                            createdAt: {type: 'string', format: 'date-time'},
                            updatedAt: {type: ['string', 'null'], format: 'date-time'}
                        }
                    }
                },
                history: {
                    type: 'array',
                    items: {
                        type: 'object',
                        required: ['at', 'kind'],
                        properties: {
                            at: {type: 'string', format: 'date-time'},
                            actor: {type: ['string', 'null']},
                            kind: {type: 'string'},
                            from: {type: ['string', 'null']},
                            to: {type: ['string', 'null']},
                            note: {type: ['string', 'null']}
                        }
                    }
                }
            }
        }
    }
} as const;
