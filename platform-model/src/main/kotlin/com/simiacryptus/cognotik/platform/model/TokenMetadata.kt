package com.simiacryptus.cognotik.platform.model

import java.time.Instant

/**
 * Non-secret description of an authentication session.
 *
 * This is the replacement for the reverse token lookup
 * (`AuthenticationInterface.getAccessToken`) called out as [H] in REVIEW.md §3.6:
 * it lets UIs list and revoke sessions without the token value ever being
 * recoverable from storage.
 *
 * @property tokenId stable, non-secret handle for the session (e.g. a hash prefix)
 * @property userId the owning user's [User.id]
 * @property issuedAt when the session was created
 * @property expiresAt when the session expires, or null if it does not expire
 * @property lastUsedAt when the token was last presented
 * @property label optional human-readable device/client description
 */
data class TokenMetadata(
  val tokenId: String,
  val userId: String,
  val issuedAt: Instant? = null,
  val expiresAt: Instant? = null,
  val lastUsedAt: Instant? = null,
  val label: String? = null,
)