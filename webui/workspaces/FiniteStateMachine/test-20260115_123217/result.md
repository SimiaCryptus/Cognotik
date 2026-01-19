# FSM Analysis: User Authentication Flow

## Summary
This analysis models the **User Authentication Flow** as a Finite State Machine (FSM) to ensure security, prevent unauthorized access, and handle edge cases like Multi-Factor Authentication (MFA) and account lockouts.

---

### 1. Overview
The Authentication FSM governs the transition of a user from an anonymous state (**Logged Out**) to a verified state (**Authenticated**). Its purpose is to enforce security policies, manage session lifecycles, and handle failure modes (e.g., brute force attacks) systematically. By modeling this as an FSM, we ensure that no "illegal" transitions occur—such as a user reaching the Authenticated state without passing through the MFA Challenge.

---

### 2. Key States

| State Name | Description | Type | Invariants |
| :--- | :--- | :--- | :--- |
| **Logged Out** | The default state where no user identity is established. | Initial | `SessionToken == null` |
| **Authenticating** | A transient state where the system validates primary credentials (username/password). | Normal (Transient) | `ProcessingRequest == true` |
| **MFA Challenged** | Primary credentials are valid, but the system awaits a second factor (OTP, Biometric). | Normal | `PrimaryAuth == Success && SecondaryAuth == Pending` |
| **Authenticated** | The user has successfully proven their identity and holds a valid session. | Normal (Stable) | `SessionToken.IsValid == true` |
| **Account Locked** | Access is denied due to security policy violations (e.g., too many failed attempts). | Error | `LoginEnabled == false` |

---

### 3. Critical Transitions
*   **Credential Submission (`Logged Out` → `Authenticating`):** The entry point into the security logic.
*   **MFA Escalation (`Authenticating` → `MFA Challenged`):** Occurs when primary credentials are correct but the policy requires a second factor.
*   **Session Validation (`MFA Challenged` → `Authenticated`):** The final gate where the user is granted access.
...(truncated)

## Key Components
- States identified and analyzed
- Transitions mapped
- State diagram generated
- Edge cases identified
- Properties validated
- Test scenarios generated
