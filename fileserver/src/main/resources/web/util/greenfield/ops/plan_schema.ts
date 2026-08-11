/**
 * Schema definitions for the **planning** artifacts produced by the
 * `greenfield` pipeline (see ../idea.md).
 *
 * The pipeline is schema-first: every planning stage emits JSON conforming to
 * a fixed, code-defined schema rather than free-form prose. Plans must be
 * renderable, diffable and *executable*.
 *
 * Stages that produce documents conforming to these types:
 *  - plan_feature.op.md      -> FeaturePlan       (plan/feature.json)
 *  - plan_stack.op.md        -> StackPlan         (plan/stack.json)
 *  - plan_architecture.op.md -> ArchitecturePlan  (plan/architecture.json)
 *  - plan_phases.op.md       -> PhasePlan         (plan/phases.json)
 *
 * The actionable output derived from these documents (one BuildPlan per
 * phase) is described by ./task_schema.ts.
 *
 * NOTE: these schemas are intentionally self-contained. They are wire
 * formats for this app only and must not be imported from `reviewer/`.
 */

/* ------------------------------------------------------------------ */
/* 1. Feature spec                                                     */

/* ------------------------------------------------------------------ */

/** A single user story with testable acceptance criteria. */
export interface UserStory {
    /** Short stable id, e.g. "us-import-csv". */
    id?: string;
    /** The actor: "a returning user", "an operator", ... */
    as_a: string;
    /** The capability wanted. */
    i_want: string;
    /** The value delivered. */
    so_that: string;
    /**
     * Observable, checkable statements. These become the basis for the tests
     * generated in stage 8, so they must be concrete.
     */
    acceptance_criteria: string[];
    /** Relative importance; used to order phase/task generation. */
    priority?: "low" | "medium" | "high" | "critical";
}

/**
 * Root structure of `plan/feature.json`, derived from `idea.md`.
 *
 * `non_goals` matter more than features here: they are what keep the later
 * stages from ballooning.
 */
export interface FeaturePlan {
    /** Human-readable product name. */
    name: string;
    /** kebab-case slug, safe for directory and package names. */
    slug: string;
    /** One paragraph: what problem is being solved, for whom. */
    problem_statement: string;
    /** Distinct user types/personas. */
    users: string[];
    /** The stories that define scope. */
    user_stories: UserStory[];
    /** Explicitly out of scope. Be aggressive. */
    non_goals: string[];
    /** Hard constraints (offline, single binary, no network, licence, ...). */
    constraints?: string[];
    /** Things a human should decide; do not block on them. */
    open_questions?: string[];
    /** ISO-8601 timestamp of when this plan was generated. */
    generated_at?: string;
}

/* ------------------------------------------------------------------ */
/* 2. Tech stack                                                       */

/* ------------------------------------------------------------------ */

/** A dependency choice with the reasoning that produced it. */
export interface LibraryChoice {
    name: string;
    /** Version or range, if it matters. */
    version?: string;
    /** What it is used for in this project. */
    purpose: string;
    /** Why this one and not the obvious alternative. */
    rationale: string;
}

/** A road not taken, recorded so a re-plan diff stays readable. */
export interface AlternativeConsidered {
    option: string;
    rejected_because: string;
}

/**
 * Root structure of `plan/stack.json`.
 *
 * One stack per project: multi-language monorepos are explicitly out of
 * scope for now (see ../idea.md, "Multi-language projects").
 */
export interface StackPlan {
    /** Primary implementation language, e.g. "TypeScript", "Kotlin", "Python". */
    language: string;
    /** Language/toolchain version pin, e.g. "21", "3.12". */
    language_version?: string;
    /** Runtime/platform, e.g. "Node 20", "JVM 21", "CPython". */
    runtime: string;
    /** Build tool, e.g. "gradle", "vite", "uv", "cargo". */
    build_tool: string;
    /** Package manager, when distinct from the build tool. */
    package_manager?: string;
    /** Application frameworks (web, CLI, UI, ...). */
    frameworks?: string[];
    /** Direct dependencies with rationale. */
    libraries?: LibraryChoice[];
    /** Test framework, e.g. "vitest", "JUnit 5", "pytest". */
    test_framework: string;
    /** Lint/format toolchain, e.g. "eslint + prettier", "ktlint". */
    lint_format?: string;
    /** CI system and workflow shape, e.g. "GitHub Actions: build+test on PR". */
    ci?: string;
    /** How the artifact ships, e.g. "npm package", "fat jar", "docker image". */
    packaging?: string;
    /**
     * Where generated source is written, relative to the *analysis root*
     * (`folder:` in every op — i.e. `../../..` from `greenfield/ops`).
     * Defaults to ".", meaning the repo root itself.
     */
    target_root?: string;
    /** Options weighed and discarded. */
    alternatives_considered?: AlternativeConsidered[];
    /** ISO-8601 timestamp of when this plan was generated. */
    generated_at?: string;
}

/* ------------------------------------------------------------------ */
/* 3. Architecture                                                     */

/* ------------------------------------------------------------------ */

/** A named unit of the system with a single responsibility. */
export interface Component {
    /** Stable id, kebab-case, referenced by BuildTask.source_components. */
    id: string;
    /** Human-readable name. */
    name: string;
    /** One sentence: what this component is responsible for. */
    responsibility: string;
    /** Ids of other components this one depends on. */
    depends_on?: string[];
    /** The API it exposes: key types/functions/endpoints. */
    public_interface?: string;
    /** Files expected to implement it, relative to `StackPlan.target_root`. */
    files?: string[];
}

/** An entity/record in the data model. */
export interface DataEntity {
    name: string;
    description?: string;
    /** Field descriptions, e.g. "id: string — primary key". */
    fields: string[];
    /** Relationships to other entities. */
    relations?: string[];
}

/** A boundary the system crosses: I/O, persistence, network, process. */
export interface Boundary {
    kind: "io" | "persistence" | "network" | "process" | "ui" | (string & {});
    description: string;
    /** Component ids that own this boundary. */
    owned_by?: string[];
}

/** A directory in the generated tree and why it exists. */
export interface DirectoryEntry {
    /** Path relative to `StackPlan.target_root`. */
    path: string;
    purpose: string;
}

/** A concern that cuts across components: logging, config, errors, auth. */
export interface CrossCuttingConcern {
    name: string;
    approach: string;
}

/** A known risk with a mitigation. */
export interface Risk {
    description: string;
    severity?: "low" | "medium" | "high" | "critical";
    mitigation?: string;
}

/** Root structure of `plan/architecture.json`. */
export interface ArchitecturePlan {
    /** e.g. "layered", "hexagonal", "pipeline", "mvc". */
    style: string;
    /** Why that style, given the feature spec and stack. */
    style_rationale?: string;
    components: Component[];
    data_model?: DataEntity[];
    boundaries?: Boundary[];
    directory_layout: DirectoryEntry[];
    cross_cutting?: CrossCuttingConcern[];
    risks?: Risk[];
    /** ISO-8601 timestamp of when this plan was generated. */
    generated_at?: string;
}

/* ------------------------------------------------------------------ */
/* 4. Phases / WBS                                                     */

/* ------------------------------------------------------------------ */

/**
 * One increment of work. Phase 0 is always the "walking skeleton": build
 * runs, one test passes, one end-to-end path works.
 */
export interface Phase {
    /** Stable, filename-safe id, e.g. "p0-skeleton". */
    id: string;
    title: string;
    /** What this phase is for, in one sentence. */
    goal: string;
    /** Concrete artifacts produced. */
    deliverables: string[];
    /** Checkable statements that mean the phase is done. */
    exit_criteria: string[];
    /** Ids of phases that must land first. Advisory. */
    depends_on?: string[];
    /** Component ids touched by this phase. */
    components?: string[];
}

/** Root structure of `plan/phases.json`. */
export interface PhasePlan {
    /** Short summary of the overall delivery plan. */
    summary: string;
    /** Ordered phases; index 0 is the walking skeleton. */
    phases: Phase[];
    /** ISO-8601 timestamp of when this plan was generated. */
    generated_at?: string;
}

/* ------------------------------------------------------------------ */
/* JSON Schemas (draft-07)                                             */
/* ------------------------------------------------------------------ */

const STRING_ARRAY = {type: "array", items: {type: "string"}} as const;

const PRIORITY = {
    type: "string",
    enum: ["low", "medium", "high", "critical"],
    default: "medium",
} as const;

export const FEATURE_PLAN_JSON_SCHEMA = {
    $schema: "http://json-schema.org/draft-07/schema#",
    title: "FeaturePlan",
    type: "object",
    required: ["name", "slug", "problem_statement", "users", "user_stories", "non_goals"],
    additionalProperties: false,
    properties: {
        name: {type: "string", minLength: 1},
        slug: {type: "string", pattern: "^[a-z0-9][a-z0-9-]*$"},
        problem_statement: {type: "string", minLength: 1},
        users: STRING_ARRAY,
        user_stories: {
            type: "array",
            minItems: 1,
            items: {
                type: "object",
                required: ["as_a", "i_want", "so_that", "acceptance_criteria"],
                additionalProperties: false,
                properties: {
                    id: {type: "string"},
                    as_a: {type: "string"},
                    i_want: {type: "string"},
                    so_that: {type: "string"},
                    acceptance_criteria: {type: "array", minItems: 1, items: {type: "string"}},
                    priority: PRIORITY,
                },
            },
        },
        non_goals: STRING_ARRAY,
        constraints: STRING_ARRAY,
        open_questions: STRING_ARRAY,
        generated_at: {type: "string"},
    },
} as const;

export const STACK_PLAN_JSON_SCHEMA = {
    $schema: "http://json-schema.org/draft-07/schema#",
    title: "StackPlan",
    type: "object",
    required: ["language", "runtime", "build_tool", "test_framework"],
    additionalProperties: false,
    properties: {
        language: {type: "string"},
        language_version: {type: "string"},
        runtime: {type: "string"},
        build_tool: {type: "string"},
        package_manager: {type: "string"},
        frameworks: STRING_ARRAY,
        libraries: {
            type: "array",
            items: {
                type: "object",
                required: ["name", "purpose", "rationale"],
                additionalProperties: false,
                properties: {
                    name: {type: "string"},
                    version: {type: "string"},
                    purpose: {type: "string"},
                    rationale: {type: "string"},
                },
            },
        },
        test_framework: {type: "string"},
        lint_format: {type: "string"},
        ci: {type: "string"},
        packaging: {type: "string"},
        target_root: {type: "string", default: "."},
        alternatives_considered: {
            type: "array",
            items: {
                type: "object",
                required: ["option", "rejected_because"],
                additionalProperties: false,
                properties: {
                    option: {type: "string"},
                    rejected_because: {type: "string"},
                },
            },
        },
        generated_at: {type: "string"},
    },
} as const;

export const ARCHITECTURE_PLAN_JSON_SCHEMA = {
    $schema: "http://json-schema.org/draft-07/schema#",
    title: "ArchitecturePlan",
    type: "object",
    required: ["style", "components", "directory_layout"],
    additionalProperties: false,
    properties: {
        style: {type: "string"},
        style_rationale: {type: "string"},
        components: {
            type: "array",
            minItems: 1,
            items: {
                type: "object",
                required: ["id", "name", "responsibility"],
                additionalProperties: false,
                properties: {
                    id: {type: "string", minLength: 1},
                    name: {type: "string"},
                    responsibility: {type: "string"},
                    depends_on: STRING_ARRAY,
                    public_interface: {type: "string"},
                    files: STRING_ARRAY,
                },
            },
        },
        data_model: {
            type: "array",
            items: {
                type: "object",
                required: ["name", "fields"],
                additionalProperties: false,
                properties: {
                    name: {type: "string"},
                    description: {type: "string"},
                    fields: STRING_ARRAY,
                    relations: STRING_ARRAY,
                },
            },
        },
        boundaries: {
            type: "array",
            items: {
                type: "object",
                required: ["kind", "description"],
                additionalProperties: false,
                properties: {
                    kind: {type: "string"},
                    description: {type: "string"},
                    owned_by: STRING_ARRAY,
                },
            },
        },
        directory_layout: {
            type: "array",
            minItems: 1,
            items: {
                type: "object",
                required: ["path", "purpose"],
                additionalProperties: false,
                properties: {
                    path: {type: "string"},
                    purpose: {type: "string"},
                },
            },
        },
        cross_cutting: {
            type: "array",
            items: {
                type: "object",
                required: ["name", "approach"],
                additionalProperties: false,
                properties: {
                    name: {type: "string"},
                    approach: {type: "string"},
                },
            },
        },
        risks: {
            type: "array",
            items: {
                type: "object",
                required: ["description"],
                additionalProperties: false,
                properties: {
                    description: {type: "string"},
                    severity: {type: "string", enum: ["low", "medium", "high", "critical"]},
                    mitigation: {type: "string"},
                },
            },
        },
        generated_at: {type: "string"},
    },
} as const;

export const PHASE_PLAN_JSON_SCHEMA = {
    $schema: "http://json-schema.org/draft-07/schema#",
    title: "PhasePlan",
    type: "object",
    required: ["summary", "phases"],
    additionalProperties: false,
    properties: {
        summary: {type: "string"},
        generated_at: {type: "string"},
        phases: {
            type: "array",
            minItems: 1,
            items: {
                type: "object",
                required: ["id", "title", "goal", "deliverables", "exit_criteria"],
                additionalProperties: false,
                properties: {
                    id: {type: "string", pattern: "^[a-z0-9][a-z0-9._-]*$"},
                    title: {type: "string"},
                    goal: {type: "string"},
                    deliverables: STRING_ARRAY,
                    exit_criteria: STRING_ARRAY,
                    depends_on: STRING_ARRAY,
                    components: STRING_ARRAY,
                },
            },
        },
    },
} as const;