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

/* ------------------------------------------------------------------ *
 * Small coercion helpers
 * ------------------------------------------------------------------ */

export function nowIso(): string {
    return new Date().toISOString();
}

function asString(value: unknown, fallback = ''): string {
    return typeof value === 'string' ? value : value == null ? fallback : String(value);
}

function asNullableString(value: unknown): string | null {
    if (value == null) return null;
    const s = String(value).trim();
    return s === '' ? null : s;
}

function asIsoDate(value: unknown, fallback: string): string {
    const s = asNullableString(value);
    if (!s) return fallback;
    const t = Date.parse(s);
    return Number.isNaN(t) ? fallback : new Date(t).toISOString();
}

function asArray<T>(value: unknown): T[] {
    return Array.isArray(value) ? (value as T[]) : [];
}

function asObject(value: unknown): Record<string, unknown> {
    return value && typeof value === 'object' && !Array.isArray(value)
        ? {...(value as Record<string, unknown>)}
        : {};
}

function oneOf<T extends string>(value: unknown, allowed: readonly T[], fallback: T): T {
    const s = asString(value).trim().toLowerCase().replace(/[\s-]+/g, '_');
    return (allowed as readonly string[]).includes(s) ? (s as T) : fallback;
}

/** Short, collision-resistant id used for comments and imported issues. */
export function shortId(prefix = 'c'): string {
    const rnd = Math.random().toString(36).slice(2, 8);
    return `${prefix}${Date.now().toString(36)}${rnd}`;
}

/* ------------------------------------------------------------------ *
 * Factories
 * ------------------------------------------------------------------ */

export function createIssuesDocument(init: Partial<IssuesDocument> = {}): IssuesDocument {
    const ts = nowIso();
    return {
        schemaVersion: ISSUES_SCHEMA_VERSION,
        createdAt: init.createdAt ?? ts,
        updatedAt: init.updatedAt ?? ts,
        nextNumber: init.nextNumber ?? 1,
        idPrefix: init.idPrefix ?? DEFAULT_ID_PREFIX,
        labels: init.labels ? [...init.labels] : [],
        issues: init.issues ? [...init.issues] : [],
        meta: init.meta ? {...init.meta} : {}
    };
}

export function makeIssue(id: string, number: number, patch: Partial<Issue> = {}): Issue {
    const ts = nowIso();
    return {
        id,
        number,
        title: patch.title ?? 'Untitled issue',
        body: patch.body ?? '',
        status: patch.status ?? 'open',
        priority: patch.priority ?? 'medium',
        type: patch.type ?? 'task',
        labels: patch.labels ? [...patch.labels] : [],
        assignee: patch.assignee ?? null,
        reporter: patch.reporter ?? null,
        createdAt: patch.createdAt ?? ts,
        updatedAt: patch.updatedAt ?? ts,
        closedAt: patch.closedAt ?? null,
        dueDate: patch.dueDate ?? null,
        estimate: patch.estimate ?? null,
        parentId: patch.parentId ?? null,
        relatedIds: patch.relatedIds ? [...patch.relatedIds] : [],
        files: patch.files ? [...patch.files] : [],
        comments: patch.comments ? [...patch.comments] : [],
        history: patch.history ? [...patch.history] : [{at: ts, actor: patch.reporter ?? null, kind: 'created', from: null, to: null, note: null}],
        meta: patch.meta ? {...patch.meta} : {}
    };
}

/** Appends a new issue to `doc`, allocating `id`/`number`. Mutates and returns the issue. */
export function addIssue(doc: IssuesDocument, patch: Partial<Issue> = {}): Issue {
    const number = Math.max(1, doc.nextNumber | 0);
    const issue = makeIssue(`${doc.idPrefix}-${number}`, number, patch);
    doc.nextNumber = number + 1;
    doc.issues.push(issue);
    doc.updatedAt = issue.updatedAt;
    return issue;
}

export function makeComment(body: string, author: string | null = null): IssueComment {
    return {id: shortId('c'), author: asNullableString(author), body, createdAt: nowIso(), updatedAt: null};
}

/** Stamps `updatedAt`, maintains `closedAt`, and optionally appends an audit event. */
export function touchIssue(issue: Issue, event?: Partial<IssueEvent>): Issue {
    issue.updatedAt = nowIso();
    const terminal = TERMINAL_STATUSES.includes(issue.status);
    if (terminal && !issue.closedAt) issue.closedAt = issue.updatedAt;
    if (!terminal) issue.closedAt = null;
    if (event) {
        issue.history.push({
            at: issue.updatedAt,
            actor: event.actor ?? null,
            kind: event.kind ?? 'edited',
            from: event.from ?? null,
            to: event.to ?? null,
            note: event.note ?? null
        });
        if (issue.history.length > 200) issue.history.splice(0, issue.history.length - 200);
    }
    return issue;
}

/* ------------------------------------------------------------------ *
 * Normalisation / validation
 * ------------------------------------------------------------------ */

export function normalizeComment(raw: unknown, where: string, errors: ValidationIssue[]): IssueComment {
    const o = asObject(raw);
    const createdAt = asIsoDate(o.createdAt, nowIso());
    if (typeof o.body !== 'string') errors.push({path: `${where}.body`, message: 'comment body missing or not a string'});
    return {
        id: asNullableString(o.id) ?? shortId('c'),
        author: asNullableString(o.author),
        body: asString(o.body),
        createdAt,
        updatedAt: o.updatedAt == null ? null : asIsoDate(o.updatedAt, createdAt)
    };
}

export function normalizeIssue(raw: unknown, where: string, errors: ValidationIssue[], fallbackNumber: number, idPrefix: string): Issue {
    const o = asObject(raw);

    const number = Number.isFinite(Number(o.number)) && Number(o.number) > 0 ? Math.floor(Number(o.number)) : fallbackNumber;
    if (o.number != null && number !== Number(o.number)) {
        errors.push({path: `${where}.number`, message: `invalid issue number, replaced with ${number}`});
    }

    const status = oneOf(o.status, ISSUE_STATUSES, 'open');
    if (o.status != null && status !== String(o.status)) {
        errors.push({path: `${where}.status`, message: `unknown status "${String(o.status)}", coerced to "${status}"`});
    }

    const createdAt = asIsoDate(o.createdAt, nowIso());
    const issue: Issue = {
        id: asNullableString(o.id) ?? `${idPrefix}-${number}`,
        number,
        title: asString(o.title, '').trim() || 'Untitled issue',
        body: asString(o.body),
        status,
        priority: oneOf(o.priority, ISSUE_PRIORITIES, 'medium'),
        type: oneOf(o.type, ISSUE_TYPES, 'task'),
        labels: asArray<unknown>(o.labels).map(l => asString(l).trim()).filter(Boolean),
        assignee: asNullableString(o.assignee),
        reporter: asNullableString(o.reporter),
        createdAt,
        updatedAt: asIsoDate(o.updatedAt, createdAt),
        closedAt: o.closedAt == null ? null : asIsoDate(o.closedAt, createdAt),
        dueDate: (() => {
            const d = asNullableString(o.dueDate);
            if (!d) return null;
            if (/^\d{4}-\d{2}-\d{2}$/.test(d)) return d;
            const t = Date.parse(d);
            if (Number.isNaN(t)) {
                errors.push({path: `${where}.dueDate`, message: `unparsable date "${d}", dropped`});
                return null;
            }
            return new Date(t).toISOString().slice(0, 10);
        })(),
        estimate: (() => {
            if (o.estimate == null || o.estimate === '') return null;
            const n = Number(o.estimate);
            if (!Number.isFinite(n) || n < 0) {
                errors.push({path: `${where}.estimate`, message: 'invalid estimate, dropped'});
                return null;
            }
            return n;
        })(),
        parentId: asNullableString(o.parentId),
        relatedIds: asArray<unknown>(o.relatedIds).map(v => asString(v).trim()).filter(Boolean),
        files: asArray<unknown>(o.files)
            .map(f => {
                const fo = asObject(f);
                const path = asNullableString(fo.path ?? f);
                return path ? {path, note: asNullableString(fo.note)} : null;
            })
            .filter((f): f is IssueFileRef => f !== null),
        comments: asArray<unknown>(o.comments).map((c, i) => normalizeComment(c, `${where}.comments[${i}]`, errors)),
        history: asArray<unknown>(o.history).map(h => {
            const ho = asObject(h);
            return {
                at: asIsoDate(ho.at, createdAt),
                actor: asNullableString(ho.actor),
                kind: asString(ho.kind, 'edited'),
                from: asNullableString(ho.from),
                to: asNullableString(ho.to),
                note: asNullableString(ho.note)
            };
        }),
        meta: asObject(o.meta)
    };

    const terminal = TERMINAL_STATUSES.includes(issue.status);
    if (terminal && !issue.closedAt) issue.closedAt = issue.updatedAt;
    if (!terminal) issue.closedAt = null;
    if (issue.history.length === 0) {
        issue.history.push({at: issue.createdAt, actor: issue.reporter, kind: 'created', from: null, to: null, note: null});
    }
    return issue;
}

/** Never throws — always yields a usable document. */
export function normalizeIssuesDocument(raw: unknown, errors: ValidationIssue[] = []): IssuesDocument {
    const o = asObject(raw);

    // Tolerate a bare array of issues.
    const rawIssues = Array.isArray(raw) ? (raw as unknown[]) : asArray<unknown>(o.issues);
    if (!Array.isArray(raw) && !Array.isArray(o.issues)) {
        if (raw != null) errors.push({path: 'issues', message: 'missing "issues" array, defaulted to []'});
    }

    const idPrefix = (asNullableString(o.idPrefix) ?? DEFAULT_ID_PREFIX).replace(/[^A-Za-z0-9_]/g, '') || DEFAULT_ID_PREFIX;
    const createdAt = asIsoDate(o.createdAt, nowIso());

    const seenIds = new Set<string>();
    let maxNumber = 0;
    const issues = rawIssues.map((r, i) => {
        const issue = normalizeIssue(r, `issues[${i}]`, errors, i + 1, idPrefix);
        while (seenIds.has(issue.id)) {
            errors.push({path: `issues[${i}].id`, message: `duplicate id "${issue.id}", regenerated`});
            issue.id = `${idPrefix}-${issue.number}-${shortId('d')}`;
        }
        seenIds.add(issue.id);
        maxNumber = Math.max(maxNumber, issue.number);
        return issue;
    });

    const declaredNext = Number(o.nextNumber);
    const nextNumber = Number.isFinite(declaredNext) && declaredNext > maxNumber ? Math.floor(declaredNext) : maxNumber + 1;

    const schemaVersion = Number.isFinite(Number(o.schemaVersion)) ? Math.floor(Number(o.schemaVersion)) : ISSUES_SCHEMA_VERSION;
    if (schemaVersion > ISSUES_SCHEMA_VERSION) {
        errors.push({
            path: 'schemaVersion',
            message: `document was written by a newer UI (v${schemaVersion} > v${ISSUES_SCHEMA_VERSION}); unknown fields are preserved`
        });
    }

    return {
        schemaVersion: ISSUES_SCHEMA_VERSION,
        createdAt,
        updatedAt: asIsoDate(o.updatedAt, createdAt),
        nextNumber,
        idPrefix,
        labels: asArray<unknown>(o.labels)
            .map(l => {
                const lo = asObject(l);
                const name = asNullableString(lo.name ?? l);
                return name ? {name, color: asNullableString(lo.color), description: asNullableString(lo.description)} : null;
            })
            .filter((l): l is LabelDef => l !== null),
        issues,
        meta: asObject(o.meta)
    };
}

export function validateIssuesDocument(raw: unknown): ValidationResult {
    const errors: ValidationIssue[] = [];
    const value = normalizeIssuesDocument(raw, errors);
    return {ok: errors.length === 0, errors, value};
}

/** Parses `raw` JSON text; a parse failure is reported as a validation error. */
export function parseIssuesJson(text: string): ValidationResult {
    try {
        return validateIssuesDocument(JSON.parse(text));
    } catch (e) {
        return {
            ok: false,
            errors: [{path: '$', message: `invalid JSON: ${(e as Error).message}`}],
            value: createIssuesDocument()
        };
    }
}

export function serializeIssuesDocument(doc: IssuesDocument): string {
    return JSON.stringify({...doc, schemaVersion: ISSUES_SCHEMA_VERSION}, null, 2) + '\n';
}

/* ------------------------------------------------------------------ *
 * Query / filter / sort / stats
 * ------------------------------------------------------------------ */

export function emptyFilter(): IssueFilter {
    return {text: '', status: 'all', priority: 'all', type: 'all', label: 'all', assignee: 'all'};
}

/**
 * Parses a GitHub-ish query into a filter, e.g.
 * `is:open label:ui assignee:me priority:high crash on save`.
 * Recognised keys: `is`/`status`, `label`, `assignee`, `type`, `priority`.
 * Everything else becomes free text.
 */
export function parseIssueQuery(query: string, base: IssueFilter = emptyFilter()): IssueFilter {
    const filter: IssueFilter = {...base};
    const words: string[] = [];
    for (const token of String(query || '').split(/\s+/).filter(Boolean)) {
        const m = /^(is|status|label|assignee|type|priority):(.*)$/i.exec(token);
        if (!m) {
            words.push(token.toLowerCase());
            continue;
        }
        const key = m[1].toLowerCase();
        const val = m[2].toLowerCase();
        if (key === 'is' || key === 'status') {
            filter.status = val === 'active' || val === 'open?' ? 'active' : oneOf(val, ISSUE_STATUSES, 'all' as IssueStatus) as IssueFilter['status'];
            if (val === 'active') filter.status = 'active';
        } else if (key === 'label') filter.label = val;
        else if (key === 'assignee') filter.assignee = val;
        else if (key === 'type') filter.type = oneOf(val, ISSUE_TYPES, 'all' as IssueType) as IssueFilter['type'];
        else if (key === 'priority') filter.priority = oneOf(val, ISSUE_PRIORITIES, 'all' as IssuePriority) as IssueFilter['priority'];
    }
    filter.text = words.join(' ');
    return filter;
}

export function issueMatches(issue: Issue, filter: IssueFilter): boolean {
    if (filter.status === 'active') {
        if (!ACTIVE_STATUSES.includes(issue.status)) return false;
    } else if (filter.status !== 'all' && issue.status !== filter.status) return false;

    if (filter.priority !== 'all' && issue.priority !== filter.priority) return false;
    if (filter.type !== 'all' && issue.type !== filter.type) return false;

    if (filter.label === 'none') {
        if (issue.labels.length > 0) return false;
    } else if (filter.label !== 'all') {
        const needle = filter.label.toLowerCase();
        if (!issue.labels.some(l => l.toLowerCase() === needle)) return false;
    }

    if (filter.assignee === 'none') {
        if (issue.assignee) return false;
    } else if (filter.assignee !== 'all') {
        if ((issue.assignee || '').toLowerCase() !== filter.assignee.toLowerCase()) return false;
    }

    const text = filter.text.trim().toLowerCase();
    if (!text) return true;
    const haystack = [
        issue.id,
        `#${issue.number}`,
        issue.title,
        issue.body,
        issue.assignee || '',
        issue.reporter || '',
        issue.labels.join(' '),
        issue.comments.map(c => `${c.author || ''} ${c.body}`).join(' ')
    ]
        .join('\n')
        .toLowerCase();
    return text.split(/\s+/).every(w => haystack.includes(w));
}

export function sortIssues(issues: readonly Issue[], sort: IssueSort = 'updated_desc'): Issue[] {
    const out = [...issues];
    const time = (s: string | null) => (s ? Date.parse(s) || 0 : 0);
    const cmp: Record<IssueSort, (a: Issue, b: Issue) => number> = {
        updated_desc: (a, b) => time(b.updatedAt) - time(a.updatedAt),
        updated_asc: (a, b) => time(a.updatedAt) - time(b.updatedAt),
        created_desc: (a, b) => time(b.createdAt) - time(a.createdAt),
        created_asc: (a, b) => time(a.createdAt) - time(b.createdAt),
        priority_desc: (a, b) => PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority] || time(b.updatedAt) - time(a.updatedAt),
        status_asc: (a, b) => STATUS_RANK[a.status] - STATUS_RANK[b.status] || PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority],
        number_asc: (a, b) => a.number - b.number,
        number_desc: (a, b) => b.number - a.number,
        title_asc: (a, b) => a.title.localeCompare(b.title)
    };
    return out.sort(cmp[sort] || cmp.updated_desc);
}

export function summarize(doc: IssuesDocument): IssuesSummary {
    const byStatus = Object.fromEntries(ISSUE_STATUSES.map(s => [s, 0])) as Record<IssueStatus, number>;
    const byPriority = Object.fromEntries(ISSUE_PRIORITIES.map(p => [p, 0])) as Record<IssuePriority, number>;
    const today = new Date().toISOString().slice(0, 10);
    let overdue = 0;
    for (const issue of doc.issues) {
        byStatus[issue.status] = (byStatus[issue.status] || 0) + 1;
        byPriority[issue.priority] = (byPriority[issue.priority] || 0) + 1;
        if (issue.dueDate && issue.dueDate < today && ACTIVE_STATUSES.includes(issue.status)) overdue++;
    }
    const active = ACTIVE_STATUSES.reduce((n, s) => n + byStatus[s], 0);
    return {total: doc.issues.length, active, done: doc.issues.length - active, byStatus, byPriority, overdue};
}

/** All labels used by issues, unioned with the declared ones, sorted. */
export function collectLabels(doc: IssuesDocument): string[] {
    const set = new Set<string>(doc.labels.map(l => l.name));
    for (const issue of doc.issues) for (const l of issue.labels) set.add(l);
    return [...set].sort((a, b) => a.localeCompare(b));
}

export function collectAssignees(doc: IssuesDocument): string[] {
    const set = new Set<string>();
    for (const issue of doc.issues) if (issue.assignee) set.add(issue.assignee);
    return [...set].sort((a, b) => a.localeCompare(b));
}

/** Merges `incoming` into `target`, renumbering imported issues. Returns the number added. */
export function mergeIssuesDocument(target: IssuesDocument, incoming: IssuesDocument): number {
    let added = 0;
    for (const issue of incoming.issues) {
        const clone: Issue = JSON.parse(JSON.stringify(issue));
        clone.number = target.nextNumber++;
        clone.id = `${target.idPrefix}-${clone.number}`;
        clone.history.push({at: nowIso(), actor: null, kind: 'edited', from: issue.id, to: clone.id, note: 'imported'});
        target.issues.push(clone);
        added++;
    }
    for (const label of incoming.labels) {
        if (!target.labels.some(l => l.name.toLowerCase() === label.name.toLowerCase())) target.labels.push(label);
    }
    if (added) target.updatedAt = nowIso();
    return added;
}

export const IssuesSchema = {
    ISSUES_SCHEMA_VERSION,
    ISSUES_FILE_NAME,
    ISSUE_STATUSES,
    ISSUE_PRIORITIES,
    ISSUE_TYPES,
    ISSUE_SORTS,
    ACTIVE_STATUSES,
    TERMINAL_STATUSES,
    ISSUES_JSON_SCHEMA,
    createIssuesDocument,
    makeIssue,
    addIssue,
    makeComment,
    touchIssue,
    normalizeIssue,
    normalizeIssuesDocument,
    validateIssuesDocument,
    parseIssuesJson,
    serializeIssuesDocument,
    emptyFilter,
    parseIssueQuery,
    issueMatches,
    sortIssues,
    summarize,
    collectLabels,
    collectAssignees,
    mergeIssuesDocument,
    nowIso,
    shortId
};

export default IssuesSchema;