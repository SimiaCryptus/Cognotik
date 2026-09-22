# AuthenticationDB test notes

* `sessionCount()` / `clearAllSessions()` are **instance** members here (the state lives in
  the `access_tokens` table, not a companion object), so the tests call them on `db`.
* `listTokens` *filters* expired rows instead of returning them, and it does **not** delete
  them - that is `purgeExpired()`'s job, or lazy eviction on `getUser`.
* `last_used_at` is throttled by `cognotik.auth.touchIntervalMillis` (default 60s). The
  property is read in the instance initializer, so the "flush" test sets the property and
  then constructs a fresh `AuthenticationDB`.
* The cache is per-instance and only eventually consistent across instances; the test
  `a stale cache can still serve a session revoked by another instance` documents this and
  shows `invalidate` as the remedy.
* There is no `hash` helper on `AuthenticationDB` (it lives on the in-memory
  `AuthenticationManager`), so those vectors are not duplicated here.
* Cross-instance reads deserialize the user from JSON, so those assertions compare
  `email`/`name` rather than relying on `User.equals`.