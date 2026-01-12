# FSM Analysis: User Authentication Flow

## Summary
This analysis models the **User Authentication Flow** as a Finite State Machine (FSM) to ensure security, robustness, and a seamless user experience.

---

### Detailed State Definitions

#### 1. State Name: Logged Out
*   **Description:** The default state where no user identity is established.
*   **Type:** Initial / Normal
*   **Invariants:** No active session token exists in the client or server-side cache.
*   **Entry Conditions:** Application launch, explicit logout action, or session termination.
*   **Exit Conditions:** User submits a login form with primary credentials.

#### 2. State Name: Authenticating
*   **Description:** A transient state where the system validates primary credentials (username/password).
*   **Type:** Normal (Transient)
*   **Invariants:** System is actively querying the identity provider; user input is blocked.
*   **Entry Conditions:** Submission of login credentials.
*   **Exit Conditions:** Credentials valid (move to MFA or Logged In); Credentials invalid (move to Logged Out or Locked); System error.

#### 3. State Name: MFA Pending
*   **Description:** Primary credentials are valid, but a second factor is required.
*   **Type:** Normal
*   **Invariants:** `primary_auth_verified = true`; `session_authorized = false`.
*   **Entry Conditions:** Successful primary authentication where MFA is enabled.
*   **Exit Conditions:** Valid MFA token provided; MFA bypass/recovery used; Timeout/Cancel.

#### 4. State Name: Logged In
*   **Description:** The user is fully authenticated and authorized to access protected resources.
*   **Type:** Normal (Stable)
*   **Invariants:** Valid, non-expired session token exists; User ID is mapped to the session.
*   **Entry Conditions:** Successful primary auth (if no MFA) or successful MFA verification.
*   **Exit Conditions:** User clicks "Logout"; Session idle timeout; Token revocation by admin.

#### 5. State Name: Account Locked
...(truncated)

## Key Components
- States identified and analyzed
- Transitions mapped
- State diagram generated
- Edge cases identified
- Properties validated
- Test scenarios generated
