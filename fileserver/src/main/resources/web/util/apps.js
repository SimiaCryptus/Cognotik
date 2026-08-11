/**
 * Cognotik Dev Tools — tool catalog.
 *
 * This file is *data only*: `index.html` renders whatever it finds here, so adding a
 * new tool is a matter of appending one object to `tools` (and, if it does not fit an
 * existing bucket, one object to `categories`).
 *
 * Field reference
 * ---------------
 *   id          stable slug, used for deep links (`#tool=<id>`) and "recently opened"
 *   name        display name
 *   icon        a single emoji — cheap, dependency-free iconography
 *   category    id of an entry in `categories`
 *   tagline     one line, shown under the title
 *   description one short paragraph, shown in the card body
 *   entry       page to open, relative to this file; `null` for op-only tools that
 *               ship no UI page (render the card as docs-only in that case)
 *   docs        optional README / design doc, relative to this file
 *   status      'stable' | 'beta' | 'experimental'
 *   tags        free-form keywords; the page derives its tag filter from these
 *   pipeline    ordered list of the stages the tool drives (optional)
 *   artifacts   [{path, note}] — generated files the tool owns (optional)
 *   requires    server endpoints / libraries the page needs at runtime (optional)
 *   storage     localStorage keys the tool writes (optional)
 */

export const meta = {
    title: 'Cognotik Dev Tools',
    tagline: 'Schema-driven, doc-op powered tools for planning, reviewing and evolving code.',
    blurb:
        'Every tool here is a single dependency-light HTML page. There is no build step, no ' +
        'bundler and no npm install: each one drives a Cognotik doc-op pipeline whose stages ' +
        'emit fixed-schema JSON, so results can be rendered, diffed and executed.',
    conventions: [
        'Schema-first — stages emit JSON conforming to a code-defined schema, never prose.',
        'One document per unit — per file, per package, per phase; small contexts, parallel runs.',
        'Advisory, not authoritative — dependencies and readiness are hints, never gatekeepers.',
        'Nothing implicit — roots, status paths and generated op files are derived and displayed.',
        'Docs live beside the tool — READMEs are markdown files rendered in place, not a wiki.'
    ]
};

export const categories = [
    {
        id: 'plan',
        name: 'Plan',
        accent: '#7c5cff',
        blurb: 'Turn an idea or a question into an inspectable plan before any code is written.'
    },
    {
        id: 'review',
        name: 'Review',
        accent: '#4f8cff',
        blurb: 'Analyse code that already exists and turn the findings into executable tasks.'
    },
    {
        id: 'build',
        name: 'Build',
        accent: '#f5a623',
        blurb: 'Make a repository build, test and run the same way on every machine.'
    },
    {
        id: 'track',
        name: 'Track',
        accent: '#3ecf8e',
        blurb: 'Keep the work itself organised, in files that live next to the code.'
    }
];

export const tools = [
    {
        id: 'reviewer',
        name: 'Reviewer',
        icon: '🔍',
        category: 'review',
        status: 'stable',
        tagline: 'Focused code review: findings → follow-up tasks → generated doc-ops.',
        description:
            'Type what the review should be about, point it at a list of files, and every stage ' +
            'emits schema-conforming JSON: a FileAnalysis per file, a PackageAnalysis per folder, ' +
            'then FollowupPlans. Each follow-up task can be executed as its own synthesized ' +
            'doc-op that patches the code.',
        entry: 'reviewer/index.html',
        docs: 'reviewer/README.md',
        tags: ['doc-ops', 'code-review', 'json-schema', 'follow-ups', 'refactoring'],
        pipeline: [
            'focus.md — the single source of truth for review intent',
            'review/**.json — one FileAnalysis per source file',
            'review/<pkg>.json — package rollups with an overall severity',
            'tasks/**.json + tasks.json — FollowupPlans, per review and aggregate',
            'tmp/*.op.md — one generated doc-op per task, run on demand'
        ],
        artifacts: [
            {path: 'reviewer/review/', note: 'per-file and per-package analyses'},
            {path: 'reviewer/tasks/', note: 'one follow-up plan per review document'},
            {path: 'reviewer/tasks.json', note: 'aggregate follow-up plan'},
            {path: 'reviewer/tmp/', note: 'temporary per-task doc-op files'}
        ],
        requires: [
            '/app/docops.js — runDocOp, waitForTask, createStatusPoller',
            '/app/fileIO.js — readFile, writeFile, listFiles, deleteFile',
            '/lib/marked.min.js — markdown rendering'
        ],
        storage: ['codeReview_tasks', 'codeReview_targets']
    },
    {
        id: 'greenfield',
        name: 'Greenfield Implementer',
        icon: '🌱',
        category: 'plan',
        status: 'stable',
        tagline: 'One paragraph of product idea → spec, stack, architecture, phases, build tasks.',
        description:
            'Starts from an empty directory. Planning stages produce JSON artifacts under plan/ ' +
            'and one BuildPlan per phase under tasks/; implementation happens per build task, ' +
            'each executed as its own generated doc-op that writes real source under the ' +
            "stack plan's target_root.",
        entry: 'greenfield/index.html',
        docs: 'greenfield/README.md',
        tags: ['doc-ops', 'planning', 'scaffolding', 'json-schema', 'wbs', 'new-project'],
        pipeline: [
            'idea.md — the seed idea, read by every stage',
            'plan/feature.json — problem statement, users, user stories',
            'plan/stack.json — language, build tool, libraries, target_root',
            'plan/architecture.json — components, data model, directory layout',
            'plan/phases.json — delivery phases with exit criteria',
            'tasks/<phase>.json — a BuildPlan per phase, executed task by task'
        ],
        artifacts: [
            {path: 'greenfield/plan/', note: 'the planning artifacts'},
            {path: 'greenfield/tasks/', note: 'one BuildPlan per phase'},
            {path: 'greenfield/tmp/', note: 'generated per-task and per-phase doc-ops'}
        ],
        requires: [
            '/app/docops.js — runDocOp, waitForTask, createStatusPoller',
            '/app/fileIO.js — readFile, writeFile, listFiles, deleteFile',
            '/lib/marked.min.js — markdown rendering'
        ],
        storage: ['greenfield_idea', 'greenfield_tasks']
    },
    {
        id: 'coder',
        name: 'Coder',
        icon: '🧭',
        category: 'review',
        status: 'beta',
        tagline: 'Research an existing codebase, then drive changes from the answers.',
        description:
            'Ask a question about a repository you did not write. Answers are gathered into ' +
            'documents that later stages can act on, in the same schema-first, one-document-' +
            'per-unit style as the reviewer.',
        entry: 'coder/index.html',
        docs: 'coder/README.md',
        tags: ['doc-ops', 'research', 'onboarding', 'q-and-a'],
        requires: [
            '/app/docops.js',
            '/app/fileIO.js'
        ]
    },
    {
        id: 'builder',
        name: 'Builder',
        icon: '🔨',
        category: 'build',
        status: 'beta',
        tagline: 'Research how a repo builds, then generate and repair one standard build.sh.',
        description:
            'Explores an unfamiliar repository to work out its modules, build tools, runtimes ' +
            'and test commands, writes a single environment-agnostic build.sh at the repo root ' +
            'with setup/compile/test sub-commands, then runs that script under an AutoFix task ' +
            'until each mode passes. Op-driven only — there is no UI page for this one.',
        entry: 'builder/index.html',
        docs: 'builder/README.md',
        tags: ['doc-ops', 'build', 'ci', 'autofix', 'bootstrapping', 'shell', 'no-ui'],
        pipeline: [
            'idea.md — research, draft, run: the three-line design sketch',
            'research.md — build requirements discovered from the repo (SubPlan, Adaptive)',
            'build.sh — one script at the analysis root: setup, compile, test',
            'build.log.md — the AutoFix transcript of running each mode until it passes'
        ],
        artifacts: [
            {path: 'builder/research.md', note: 'requirements for the build system'},
            {path: 'build.sh', note: 'written at the repo root — meant to be committed'},
            {path: 'builder/build.log.md', note: 'AutoFix run log per build mode'}
        ],
        requires: [
            'a doc-op runner able to execute ops/*.op.md (SubPlan, AutoFix task types)',
            'a shell environment matching the drafted script — Ubuntu 22 by default'
        ]
    },
    {
        id: 'issues',
        name: 'Issues',
        icon: '🗂️',
        category: 'track',
        status: 'stable',
        tagline: 'A session-local issue tracker backed by a single issues.json.',
        description:
            'Filterable list, markdown descriptions, comments, linked files and an activity log — ' +
            'all persisted to one normalised JSON document next to the page, with optimistic ' +
            'concurrency on save and import/export for moving it around.',
        entry: 'issues/issues.html',
        docs: null,
        tags: ['issues', 'tracking', 'json', 'offline', 'no-build'],
        artifacts: [
            {path: 'issues/issues.json', note: 'the whole database — one normalised document'}
        ],
        requires: [
            '/app/fileIO.js — optional; falls back to plain fetch GET/PUT',
            '/app/marked.min.js — optional markdown rendering',
            '/lib/purify.min.js — optional HTML sanitising'
        ],
        storage: ['issues.sort', 'issues.author', 'issues.backup.<basePath>']
    }
];

export default {meta, categories, tools};