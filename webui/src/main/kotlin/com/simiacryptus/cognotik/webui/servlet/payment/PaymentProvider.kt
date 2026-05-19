package com.simiacryptus.cognotik.webui.servlet.payment

import com.simiacryptus.cognotik.platform.model.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * Abstraction over a payment / credit-top-up backend.
 *
 * Implementations are responsible for:
 *  - Initiating a checkout session (may redirect the user to an external page).
 *  - Handling the return / webhook callback that confirms payment.
 *  - Crediting the user's budget via [com.simiacryptus.cognotik.platform.model.UsageInterface].
 *
 * The servlet calls [initiateCheckout] when the user submits the review form.
 * For providers that use an external redirect (e.g. Stripe Checkout) the
 * implementation should call [HttpServletResponse.sendRedirect] itself and
 * return [CheckoutResult.Redirected].  For no-op / synchronous providers it
 * should apply the credit immediately and return [CheckoutResult.Completed].
 */
interface PaymentProvider {

    /**
     * Human-readable name shown in the UI (e.g. "No-op", "Stripe").
     */
    val name: String

    /**
     * Whether this provider requires real payment details from the user.
     * Used by the UI to show/hide the "no payment processed" notice.
     */
    val requiresPayment: Boolean

    /**
     * Called when the user confirms their order on the review page.
     *
     * @param req      The current HTTP request (POST to the servlet).
     * @param resp     The current HTTP response.
     * @param user     The authenticated user.
     * @param amount   The credit amount to apply (already capped by the servlet).
     * @param orderId  A pre-generated order ID string for correlation.
     * @return A [CheckoutResult] describing what happened.
     */
    fun initiateCheckout(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        user: User,
        amount: Double,
        orderId: String
    ): CheckoutResult

    /**
     * Called when an external provider redirects the user back to the
     * servlet with `?step=callback`.  Synchronous / no-op providers can
     * leave this as a no-op that returns null.
     *
     * @return A [CheckoutResult.Completed] if the payment was confirmed, or
     *         null if the callback could not be verified / is not applicable.
     */
    fun handleCallback(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        user: User
    ): CheckoutResult? = null

    /**
     * Optional: render provider-specific UI elements inside the review page
     * (e.g. a Stripe card element).  Return an empty string if not needed.
     */
    fun reviewPageExtras(req: HttpServletRequest, user: User): String = ""

    sealed class CheckoutResult {
        /**
         * The credit was applied synchronously.  The servlet will redirect
         * the user to the receipt page.
         *
         * @param newBudget The user's balance after the credit was applied.
         * @param orderId   The order ID (may differ from the one passed in for
         *                  provider-generated IDs).
         * @param amount    The amount actually applied.
         */
        data class Completed(
            val newBudget: Double,
            val orderId: String,
            val amount: Double
        ) : CheckoutResult()

        /**
         * The provider has already redirected the user (e.g. to Stripe Checkout).
         * The servlet should not write any further response.
         */
        object Redirected : CheckoutResult()

        /**
         * The checkout failed before any redirect occurred.
         *
         * @param message A user-facing error message.
         */
        data class Failed(val message: String) : CheckoutResult()
    }
}