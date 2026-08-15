package com.simiacryptus.cognotik.platform

/**
 * Opt-in base class carrying the naive (N+1) bulk/listing fallbacks declared as
 * defaults on [MetadataStorageInterface].
 *
 * Extending this class is an explicit statement that the per-session fallbacks are
 * acceptable for the backend in question. Implementations backed by a database
 * should implement [MetadataStorageInterface] directly and override
 * `listSessionMetadata`, `listSessionEntries`, `getSessionMetadataMap` and
 * `exists` with single-round-trip projections (REVIEW.md §3.4, Phase 3 item 15).
 */
abstract class AbstractMetadataStorage : MetadataStorageInterface