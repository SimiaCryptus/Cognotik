package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.authenticate
import com.simiacryptus.cognotik.webui.servlet.payment.NoOpPaymentProvider
import com.simiacryptus.cognotik.webui.servlet.payment.PaymentProvider
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

/**
 * Self-service "buy credits" servlet.
 *
 * Structured as a low-friction checkout (package selection -> review ->
 * confirmation / external redirect -> receipt).  The actual payment logic is
 * delegated to a [PaymentProvider], making it easy to swap between the
 * built-in no-op provider and a real processor such as Stripe.
 *
 * Default provider: [NoOpPaymentProvider] – applies credits immediately
 * without any real payment processing (original behaviour).
 */
open class CreditsServlet(
    private val provider: PaymentProvider?
) : HttpServlet() {

    val usageDB: UsageInterface by lazy { ApplicationServices.fileApplicationServices().usageManager }

    private fun currentBudget(user: User): Double? = runCatching { usageDB.getAvailableBudget(user) }.getOrNull()

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val user = authenticate(req, resp) ?: throw RuntimeException("User must be authenticated to purchase credits")

        when (req.getParameter("step")?.lowercase()) {
            "review" -> renderReview(req, resp, user)
            "receipt" -> renderReceipt(req, resp, user)
            "callback" -> handleProviderCallback(req, resp, user)
            else -> renderCheckout(resp, user)
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val user = authenticate(req, resp) ?: throw RuntimeException("User must be authenticated to purchase credits")

        val amount = parseAmount(req)
        if (amount == null || amount <= 0.0) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            renderError(resp, "Invalid amount. Please select a package or enter a positive amount.")
            return
        }

        val cappedAmount = amount.coerceAtMost(MAX_PURCHASE_AMOUNT)
        val orderId = UUID.randomUUID().toString().take(8).uppercase()

        val provider = provider ?: throw RuntimeException("No payment provider configured for CreditsServlet")
        when (val result = provider.initiateCheckout(req, resp, user, cappedAmount, orderId)) {
            is PaymentProvider.CheckoutResult.Completed -> {
                log.info(
                    "Credit applied via ${provider.name}: user=${user.email} " +
                            "amount=${result.amount} order=${result.orderId} newBudget=${result.newBudget}"
                )
                resp.sendRedirect(
                    "?step=receipt" +
                            "&order=${result.orderId}" +
                            "&amount=${result.amount}" +
                            "&balance=${result.newBudget}"
                )
            }

            is PaymentProvider.CheckoutResult.Redirected -> {
                // Provider has already redirected the user; nothing more to do.
            }

            is PaymentProvider.CheckoutResult.Failed -> {
                resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                renderError(resp, result.message)
            }
        }
    }

    private fun handleProviderCallback(req: HttpServletRequest, resp: HttpServletResponse, user: User) {
        val provider = provider ?: throw RuntimeException("No payment provider configured for CreditsServlet")
        when (val result = provider.handleCallback(req, resp, user)) {
            is PaymentProvider.CheckoutResult.Completed -> {
                log.info(
                    "Callback credit applied via ${provider.name}: user=${user.email} " +
                            "amount=${result.amount} order=${result.orderId} newBudget=${result.newBudget}"
                )
                resp.sendRedirect(
                    "?step=receipt" +
                            "&order=${result.orderId}" +
                            "&amount=${result.amount}" +
                            "&balance=${result.newBudget}"
                )
            }

            is PaymentProvider.CheckoutResult.Failed -> {
                resp.status = HttpServletResponse.SC_BAD_GATEWAY
                renderError(resp, result.message)
            }

            is PaymentProvider.CheckoutResult.Redirected -> {
                // Unusual in a callback, but respect it.
            }

            null -> {
                log.warn("Provider ${provider.name} returned null from handleCallback for user=${user.email}")
                resp.sendRedirect("?")
            }
        }
    }


    private fun parseAmount(request: HttpServletRequest): Double? {
        request.getParameter("package")?.let { pkg ->
            PACKAGES.firstOrNull { it.id == pkg }?.let { return it.amount }
        }
        return request.getParameter("amount")?.toDoubleOrNull()
    }


    private fun renderCheckout(response: HttpServletResponse, user: User) {
        val provider = provider ?: throw RuntimeException("No payment provider configured for CreditsServlet")
        response.contentType = "text/html"
        response.status = HttpServletResponse.SC_OK

        val budget = currentBudget(user)
        val budgetHtml = if (budget != null) {
            """<div class="budget">Current balance: <strong>${"%.4f".format(budget)}</strong></div>"""
        } else ""
        val paymentNotice = if (!provider.requiresPayment) {
            """
             <div class="notice">
                 <strong>Notice:</strong> This is a self-service credit top-up.
                 No payment is processed. Credits applied here are governed by
                 your account's budgeting policy and audited via ledger entries.
             </div>
             """.trimIndent()
        } else {
            """
             <div class="notice">
                 <strong>Payment provider:</strong> ${provider.name}.
                 You will be redirected to complete payment before credits are applied.
             </div>
             """.trimIndent()
        }


        val packageCards = PACKAGES.joinToString("\n") { pkg ->
            """
                <label class="pkg-card">
                    <input type="radio" name="package" value="${pkg.id}" ${if (pkg.id == "starter") "checked" else ""}/>
                    <div class="pkg-title">${pkg.label}</div>
                    <div class="pkg-amount">${"%.2f".format(pkg.amount)} credits</div>
                    <div class="pkg-desc">${pkg.description}</div>
                </label>
                """.trimIndent()
        }

        response.writer.write(
            """
                <html>
                <head>
                    <title>Buy Credits</title>
                    <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                    ${commonStyles()}
                </head>
                <body>
                <div class="container">
                    <h1>Buy Credits</h1>
                    <div class="scope">Account: ${user.email}</div>
                    $budgetHtml
                     $paymentNotice
                    <form method="get" action="">
                        <input type="hidden" name="step" value="review"/>
                        <h2>Select a package</h2>
                        <div class="pkg-grid">
                            $packageCards
                        </div>
                        <h2>Or enter a custom amount</h2>
                        <div class="custom-row">
                            <label>
                                <input type="radio" name="package" value="custom"/>
                                Custom:
                            </label>
                            <input type="number" name="amount" min="0.01" max="$MAX_PURCHASE_AMOUNT" step="0.01" placeholder="0.00"/>
                            <span class="hint">(max ${"%.2f".format(MAX_PURCHASE_AMOUNT)})</span>
                        </div>
                        <div class="actions">
                             <button type="submit" class="btn-primary">
                                 ${if (provider.requiresPayment) "Continue to Payment &rarr;" else "Continue &rarr;"}
                             </button>
                            <a href="/usage" class="btn-link">View usage</a>
                        </div>
                    </form>
                </div>
                </body>
                </html>
                """.trimIndent()
        )
    }

    private fun renderReview(request: HttpServletRequest, response: HttpServletResponse, user: User) {
        val provider = provider ?: throw RuntimeException("No payment provider configured for CreditsServlet")
        response.contentType = "text/html"
        response.status = HttpServletResponse.SC_OK

        val amount = parseAmount(request)
        if (amount == null || amount <= 0.0) {
            response.sendRedirect("?")
            return
        }
        val capped = amount.coerceAtMost(MAX_PURCHASE_AMOUNT)
        val budget = currentBudget(user)
        val projected = (budget ?: 0.0) + capped

        val pkgLabel = request.getParameter("package")?.let { id ->
            PACKAGES.firstOrNull { it.id == id }?.label
        } ?: "Custom amount"

        val warning = if (amount > MAX_PURCHASE_AMOUNT) {
            """<div class="warning">Requested amount exceeds the per-purchase cap of ${"%.2f".format(MAX_PURCHASE_AMOUNT)}. The applied amount will be capped.</div>"""
        } else ""
        val providerExtras = provider.reviewPageExtras(request, user)
        val paymentMethodRow = if (provider.requiresPayment) {
            "<tr><th>Payment method</th><td><em>${provider.name}</em></td></tr>"
        } else {
            "<tr><th>Payment method</th><td><em>No-op (self-service)</em></td></tr>"
        }
        val confirmLabel = if (provider.requiresPayment) "Proceed to Payment &rarr;" else "Confirm &amp; Apply Credits"


        response.writer.write(
            """
                <html>
                <head>
                    <title>Review Purchase</title>
                    <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                    ${commonStyles()}
                </head>
                <body>
                <div class="container">
                    <h1>Review Your Order</h1>
                    <div class="scope">Account: ${user.email}</div>
                    $warning
                    <table class="review-table">
                        <tr><th>Package</th><td>$pkgLabel</td></tr>
                        <tr><th>Requested amount</th><td>${"%.4f".format(amount)}</td></tr>
                        <tr><th>Applied amount</th><td><strong>${"%.4f".format(capped)}</strong></td></tr>
                        <tr><th>Current balance</th><td>${budget?.let { "%.4f".format(it) } ?: "—"}</td></tr>
                        <tr class="total-row"><th>Balance after</th><td><strong>${"%.4f".format(projected)}</strong></td></tr>
                         $paymentMethodRow
                    </table>
                     $providerExtras
                    <form method="post" action="">
                        <input type="hidden" name="amount" value="$capped"/>
                        <div class="actions">
                             <button type="submit" class="btn-primary">$confirmLabel</button>
                            <a href="?" class="btn-link">Back</a>
                        </div>
                    </form>
                </div>
                </body>
                </html>
                """.trimIndent()
        )
    }

    private fun renderReceipt(req: HttpServletRequest, resp: HttpServletResponse, user: User) {
        resp.contentType = "text/html"
        resp.status = HttpServletResponse.SC_OK

        val orderId = req.getParameter("order") ?: "—"
        val amount = req.getParameter("amount")?.toDoubleOrNull() ?: 0.0
        val balance = req.getParameter("balance")?.toDoubleOrNull() ?: currentBudget(user) ?: 0.0

        resp.writer.write(
            """
                <html>
                <head>
                    <title>Receipt</title>
                    <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                    ${commonStyles()}
                </head>
                <body>
                <div class="container">
                    <h1>✓ Credits Applied</h1>
                    <div class="scope">Account: ${user.email}</div>
                    <div class="receipt">
                        <table class="review-table">
                            <tr><th>Order ID</th><td><code>$orderId</code></td></tr>
                            <tr><th>Amount applied</th><td><strong>${"%.4f".format(amount)}</strong></td></tr>
                            <tr><th>New balance</th><td><strong>${"%.4f".format(balance)}</strong></td></tr>
                            <tr><th>Timestamp</th><td>${Instant.now()}</td></tr>
                        </table>
                    </div>
                    <div class="notice">
                        Your credits have been applied. A ledger entry has been recorded for audit purposes.
                    </div>
                    <div class="actions">
                        <a href="?" class="btn-primary">Buy more credits</a>
                        <a href="/usage" class="btn-link">View usage</a>
                    </div>
                </div>
                </body>
                </html>
                """.trimIndent()
        )
    }

    private fun renderError(resp: HttpServletResponse, message: String) {
        resp.contentType = "text/html"
        resp.writer.write(
            """
                <html>
                <head>
                    <title>Error</title>
                    ${commonStyles()}
                </head>
                <body>
                <div class="container">
                    <h1>Unable to complete purchase</h1>
                    <div class="warning">$message</div>
                    <div class="actions"><a href="?" class="btn-primary">Try again</a></div>
                </div>
                </body>
                </html>
                """.trimIndent()
        )
    }

    private fun commonStyles(): String = """
            <style>
                body { font-family: Arial, sans-serif; margin: 0; background: #f7f8fa; }
                .container { max-width: 760px; margin: 30px auto; padding: 24px; background: #fff;
                             border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
                h1, h2 { color: #333; }
                h2 { margin-top: 24px; font-size: 1.1em; }
                .scope { color: #666; margin-bottom: 12px; }
                .budget { padding: 10px 14px; background: #eef7ee; border-left: 4px solid #4a8;
                          margin-bottom: 16px; border-radius: 4px; }
                .notice { padding: 10px 14px; background: #fffbe6; border-left: 4px solid #e0b500;
                          margin: 14px 0; border-radius: 4px; font-size: 0.95em; }
                .warning { padding: 10px 14px; background: #fdecea; border-left: 4px solid #c0392b;
                           margin: 14px 0; border-radius: 4px; }
                .pkg-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                            gap: 12px; margin: 12px 0; }
                .pkg-card { display: block; border: 2px solid #ddd; border-radius: 6px; padding: 12px;
                            cursor: pointer; transition: border-color 0.15s, background 0.15s; }
                .pkg-card:hover { border-color: #4a6fa5; background: #f4f7fc; }
                .pkg-card input[type=radio] { margin-right: 6px; }
                .pkg-title { font-weight: bold; margin-top: 6px; }
                .pkg-amount { color: #4a6fa5; font-size: 1.05em; margin: 4px 0; }
                .pkg-desc { color: #777; font-size: 0.85em; }
                .custom-row { display: flex; align-items: center; gap: 8px; margin: 8px 0; }
                .custom-row input[type=number] { padding: 6px; width: 120px; }
                .hint { color: #888; font-size: 0.85em; }
                .actions { margin-top: 20px; display: flex; gap: 12px; align-items: center; }
                .btn-primary { background: #4a6fa5; color: #fff; border: none; padding: 10px 18px;
                               border-radius: 4px; cursor: pointer; font-size: 1em; text-decoration: none; }
                .btn-primary:hover { background: #3a5a8c; }
                .btn-link { color: #4a6fa5; text-decoration: none; }
                .btn-link:hover { text-decoration: underline; }
                table.review-table { width: 100%; border-collapse: collapse; margin: 12px 0; }
                table.review-table th, table.review-table td { border: 1px solid #e1e4e8;
                                                                padding: 8px 10px; text-align: left; }
                table.review-table th { background: #f4f6f9; width: 35%; }
                .total-row td, .total-row th { background: #eef7ee; }
                .receipt { margin: 14px 0; }
                code { background: #f4f6f9; padding: 2px 6px; border-radius: 3px; }
            </style>
        """.trimIndent()

    private data class CreditPackage(
        val id: String,
        val label: String,
        val amount: Double,
        val description: String
    )

    companion object {
        private val log = LoggerFactory.getLogger(CreditsServlet::class.java)

        /**
         * Per-purchase cap. Acts as the failsafe budgeting control: even if a
         * user attempts to self-issue a large credit, the applied amount is
         * capped here.
         */
        const val MAX_PURCHASE_AMOUNT: Double = 100.0

        private val PACKAGES = listOf(
            CreditPackage("starter", "Starter", 5.0, "Quick top-up for light usage"),
            CreditPackage("standard", "Standard", 20.0, "Typical monthly workload"),
            CreditPackage("pro", "Pro", 50.0, "Heavy usage / multiple projects"),
            CreditPackage("max", "Max", 100.0, "Maximum allowed per purchase")
        )
    }
}