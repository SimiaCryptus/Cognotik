package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
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
    private vararg val providers: PaymentProvider
) : HttpServlet() {

    val usageDB: UsageInterface by lazy { ApplicationServices.fileApplicationServices().usageDB }

    private fun currentBudget(user: User): Double? = runCatching { usageDB.getAvailableBudget(user) }.getOrNull()

    /**
     * Providers visible to (and usable by) the given user.  Filters out
     * providers that have explicitly denied authorization for the user,
     * so the UI never teases admin-only payment methods.
     */
    private fun authorizedProviders(user: User): List<PaymentProvider> =
        providers.filter { it.isAuthorized(user) }

    private fun resolveProvider(req: HttpServletRequest, user: User): PaymentProvider {
        val available = authorizedProviders(user)
        if (available.isEmpty()) throw RuntimeException("No payment provider available for user ${user.email}")
        val requested = req.getParameter("provider")
        if (requested != null) {
            available.firstOrNull { it.name.equals(requested, ignoreCase = true) }?.let { return it }
        }
        return available.first()
    }

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val user = UserProviderImpl().authenticate(req, resp)
          ?: throw RuntimeException("User must be authenticated to purchase credits")
        if (authorizedProviders(user).isEmpty()) {
            resp.status = HttpServletResponse.SC_FORBIDDEN
            renderError(resp, "You are not authorized to purchase credits. Please contact support.")
            return
        }


        when (req.getParameter("step")?.lowercase()) {
            "review" -> renderReview(req, resp, user)
            "receipt" -> renderReceipt(req, resp, user)
            "callback" -> handleProviderCallback(req, resp, user)
            "poll", "webhook" -> handleProviderCallback(req, resp, user)
            else -> renderCheckout(resp, user)
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        val user = UserProviderImpl().authenticate(req, resp)
          ?: throw RuntimeException("User must be authenticated to purchase credits")
        if (authorizedProviders(user).isEmpty()) {
            resp.status = HttpServletResponse.SC_FORBIDDEN
            renderError(resp, "You are not authorized to purchase credits. Please contact support.")
            return
        }


        val amount = parseAmount(req)
        if (amount == null || amount <= 0.0) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            renderError(resp, "Invalid amount. Please select a package or enter a positive amount.")
            return
        }

        val cappedAmount = amount.coerceAtMost(MAX_PURCHASE_AMOUNT)
        val orderId = UUID.randomUUID().toString().take(8).uppercase()

        val provider = resolveProvider(req, user)
        if (!provider.isAuthorized(user)) {
            resp.status = HttpServletResponse.SC_FORBIDDEN
            renderError(resp, "You are not authorized to use this payment method. Please contact support.")
            return
        }
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
                            "&balance=${result.newBudget}" +
                            "&provider=${provider.name}"
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
        val provider = resolveProvider(req, user)
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
                            "&balance=${result.newBudget}" +
                            "&provider=${provider.name}"
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
        val available = authorizedProviders(user)
        val provider = available.first()
        response.contentType = "text/html"
        response.status = HttpServletResponse.SC_OK

        val budget = currentBudget(user)
        val budgetHtml = if (budget != null) {
            """<div class="budget">Current balance: <strong>${"%.4f".format(budget)}</strong></div>"""
        } else ""
        val paymentNotice = if (available.size > 1) {
            """
              <div class="notice">
                  <strong>Multiple payment providers available.</strong> Select your preferred method below.
              </div>
              """.trimIndent()
        } else if (!provider.requiresPayment) {
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
        val licenseNotice = """
              <div class="notice license-notice">
                  <strong>📄 License Terms:</strong> By purchasing credits, you agree to the
                  <a href="/LICENSE.html" target="_blank" rel="noopener">Cognotik Software License Agreement</a>,
                  including the <a href="/LICENSE.html#82-api-credits-for-the-cloud-hosted-version" target="_blank" rel="noopener">API Credits terms (Section 8.2)</a>.
                  <strong>API Credits are non-refundable, have no cash value, and may be consumed unpredictably.</strong>
              </div>
              """.trimIndent()

        val providerSelection = if (available.size > 1) {
            val providerOptions = available.mapIndexed { idx, p ->
                val checked = if (idx == 0) "checked" else ""
                val desc = if (p.requiresPayment) "External payment" else "Self-service (no payment)"
                """
                     <label class="pkg-card">
                         <input type="radio" name="provider" value="${p.name}" $checked/>
                         <div class="pkg-title">${p.name}</div>
                         <div class="pkg-desc">$desc</div>
                     </label>
                     """.trimIndent()
            }.joinToString("\n")
            """
                 <h2>Select a payment method</h2>
                 <div class="pkg-grid">
                     $providerOptions
                 </div>
                 """.trimIndent()
        } else {
            """<input type="hidden" name="provider" value="${provider.name}"/>"""
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
                   <script src="/modules/theme.js"></script>
                </head>
                <body>
                <div class="container">
                    <h1>Buy Credits</h1>
                    <div class="scope">Account: ${user.email}</div>
                    ${navBar("credits")}
                    $budgetHtml
                     $paymentNotice
                    <form method="get" action="">
                        <input type="hidden" name="step" value="review"/>
                         $providerSelection
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
                         $licenseNotice
                        <div class="actions">
                             <button type="submit" class="btn-primary">
                                  Continue &rarr;
                             </button>
                            <a href="/usage" class="btn-link">View usage</a>
                            <a href="/gifts/" class="btn-link">Gifts</a>
                        </div>
                    </form>
                </div>
               <script>ThemeManager.init();</script>
                </body>
                </html>
                """.trimIndent()
        )
    }

    private fun renderReview(request: HttpServletRequest, response: HttpServletResponse, user: User) {
        val provider = resolveProvider(request, user)
        if (!provider.isAuthorized(user)) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            renderError(response, "You are not authorized to use this payment method. Please contact support.")
            return
        }
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
            "<tr><th>Payment method</th><td><em>${provider.name} (self-service)</em></td></tr>"
        }
        val confirmLabel = if (provider.requiresPayment) "Proceed to Payment &rarr;" else "Confirm &amp; Apply Credits"
        val licenseAgreementBlock = """
              <div class="license-agreement">
                  <h3>License Agreement</h3>
                  <div class="license-summary">
                      <p>By confirming this purchase, you acknowledge and agree to the
                      <a href="/LICENSE.html" target="_blank" rel="noopener">Cognotik Software License Agreement</a>.
                      Key terms regarding credits:</p>
                      <ul>
                          <li><strong>Non-refundable:</strong> All credit purchases are final. See <a href="/LICENSE.html#82-api-credits-for-the-cloud-hosted-version" target="_blank" rel="noopener">Section 8.2(b)</a>.</li>
                          <li><strong>No cash value:</strong> Credits cannot be redeemed for cash, transferred, or inherited. See <a href="/LICENSE.html#82-api-credits-for-the-cloud-hosted-version" target="_blank" rel="noopener">Section 8.2(a)</a>.</li>
                          <li><strong>Consumption risk:</strong> Credits may be consumed rapidly or unpredictably. See <a href="/LICENSE.html#82-api-credits-for-the-cloud-hosted-version" target="_blank" rel="noopener">Section 8.2(c)</a>.</li>
                          <li><strong>No data retention guarantee:</strong> See <a href="/LICENSE.html#81-cloud-hosted-version-eg-hostedcognotikcom" target="_blank" rel="noopener">Section 8.1</a>.</li>
                          <li><strong>AI Output:</strong> May contain errors or fabrications and must be independently verified. See <a href="/LICENSE.html#4-ai-generated-content-disclaimer" target="_blank" rel="noopener">Section 4</a>.</li>
                      </ul>
                  </div>
                  <label class="license-checkbox">
                      <input type="checkbox" name="accept_license" id="accept_license" required/>
                      I have read and agree to the <a href="/LICENSE.html" target="_blank" rel="noopener">Cognotik Software License Agreement</a>, including the non-refundable nature of API Credits.
                  </label>
              </div>
              """.trimIndent()


        response.writer.write(
            """
                <html>
                <head>
                    <title>Review Purchase</title>
                    <link rel="icon" type="image/svg+xml" href="/favicon.svg"/>
                    ${commonStyles()}
                   <script src="/modules/theme.js"></script>
                </head>
                <body>
                <div class="container">
                    <h1>Review Your Order</h1>
                    <div class="scope">Account: ${user.email}</div>
                    ${navBar("credits")}
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
                     $licenseAgreementBlock
                    <form method="post" action="">
                        <input type="hidden" name="amount" value="$capped"/>
                         <input type="hidden" name="provider" value="${provider.name}"/>
                        <div class="actions">
                             <button type="submit" class="btn-primary" id="confirm-btn" disabled>$confirmLabel</button>
                            <a href="?" class="btn-link">Back</a>
                        </div>
                    </form>
                </div>
               <script>ThemeManager.init();</script>
                <script>
                    (function() {
                        var checkbox = document.getElementById('accept_license');
                        var btn = document.getElementById('confirm-btn');
                        if (checkbox && btn) {
                            checkbox.addEventListener('change', function() {
                                btn.disabled = !checkbox.checked;
                            });
                        }
                    })();
                </script>
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
                   <script src="/modules/theme.js"></script>
                </head>
                <body>
                <div class="container">
                    <h1>✓ Credits Applied</h1>
                    <div class="scope">Account: ${user.email}</div>
                    ${navBar("credits")}
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
                    <div class="notice license-notice">
                        <strong>License Reminder:</strong> These credits are subject to the
                        <a href="/LICENSE.html" target="_blank" rel="noopener">Cognotik Software License Agreement</a>.
                        Credits are non-refundable and have no cash value
                        (<a href="/LICENSE.html#82-api-credits-for-the-cloud-hosted-version" target="_blank" rel="noopener">Section 8.2</a>).
                    </div>
                    <div class="actions">
                        <a href="?" class="btn-primary">Buy more credits</a>
                        <a href="/usage" class="btn-link">View usage</a>
                        <a href="/gifts/" class="btn-link">Gifts</a>
                    </div>
                </div>
               <script>ThemeManager.init();</script>
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
                   <script src="/modules/theme.js"></script>
                </head>
                <body>
                <div class="container">
                    <h1>Unable to complete purchase</h1>
                    <div class="warning">$message</div>
                    <div class="actions"><a href="?" class="btn-primary">Try again</a></div>
                </div>
               <script>ThemeManager.init();</script>
                </body>
                </html>
                """.trimIndent()
        )
    }

    private fun commonStyles(): String = """
            <style>
               :root {
                   --bg-page: #f7f8fa;
                   --bg-container: #ffffff;
                   --bg-muted: #f4f6f9;
                   --bg-nav: #f0f3f8;
                   --bg-nav-hover: #e1e7f1;
                   --text-primary: #333333;
                   --text-secondary: #666666;
                   --text-muted: #777777;
                   --text-hint: #888888;
                   --border-color: #dddddd;
                   --border-table: #e1e4e8;
                   --accent: #4a6fa5;
                   --accent-hover: #3a5a8c;
                   --accent-bg: #f4f7fc;
                   --success-bg: #eef7ee;
                   --success-border: #4a8;
                   --notice-bg: #fffbe6;
                   --notice-border: #e0b500;
                   --warning-bg: #fdecea;
                   --warning-border: #c0392b;
                   --shadow: 0 1px 4px rgba(0,0,0,0.08);
               }
               html[data-theme="dark"] {
                   --bg-page: #1a1d23;
                   --bg-container: #252932;
                   --bg-muted: #2f3440;
                   --bg-nav: #2a2e38;
                   --bg-nav-hover: #353a47;
                   --text-primary: #e4e6eb;
                   --text-secondary: #b0b3b8;
                   --text-muted: #9a9da3;
                   --text-hint: #8a8d93;
                   --border-color: #3a3f4b;
                   --border-table: #3a3f4b;
                   --accent: #6b8fc7;
                   --accent-hover: #8aa8db;
                   --accent-bg: #2d3340;
                   --success-bg: #2a3a2e;
                   --success-border: #5ab;
                   --notice-bg: #3a3520;
                   --notice-border: #d4a818;
                   --warning-bg: #3a2624;
                   --warning-border: #d4544a;
                   --shadow: 0 1px 4px rgba(0,0,0,0.4);
               }
               body { font-family: Arial, sans-serif; margin: 0; background: var(--bg-page); color: var(--text-primary); }
               .container { max-width: 760px; margin: 30px auto; padding: 24px; background: var(--bg-container);
                            border-radius: 8px; box-shadow: var(--shadow); }
               h1, h2 { color: var(--text-primary); }
                h2 { margin-top: 24px; font-size: 1.1em; }
               .scope { color: var(--text-secondary); margin-bottom: 12px; }
               .nav-bar { display: flex; gap: 8px; padding: 10px 12px; background: var(--bg-nav);
                           border-radius: 6px; margin-bottom: 16px; flex-wrap: wrap; }
               .nav-bar a { color: var(--accent); text-decoration: none; padding: 6px 12px;
                             border-radius: 4px; font-size: 0.95em; }
               .nav-bar a:hover { background: var(--bg-nav-hover); text-decoration: none; }
               .nav-bar a.active { background: var(--accent); color: #fff; font-weight: 600; }
               .budget { padding: 10px 14px; background: var(--success-bg); border-left: 4px solid var(--success-border);
                          margin-bottom: 16px; border-radius: 4px; }
               .notice { padding: 10px 14px; background: var(--notice-bg); border-left: 4px solid var(--notice-border);
                          margin: 14px 0; border-radius: 4px; font-size: 0.95em; }
               .warning { padding: 10px 14px; background: var(--warning-bg); border-left: 4px solid var(--warning-border);
                           margin: 14px 0; border-radius: 4px; }
                .pkg-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                            gap: 12px; margin: 12px 0; }
               .pkg-card { display: block; border: 2px solid var(--border-color); border-radius: 6px; padding: 12px;
                            cursor: pointer; transition: border-color 0.15s, background 0.15s; }
               .pkg-card:hover { border-color: var(--accent); background: var(--accent-bg); }
                .pkg-card input[type=radio] { margin-right: 6px; }
                .pkg-title { font-weight: bold; margin-top: 6px; }
               .pkg-amount { color: var(--accent); font-size: 1.05em; margin: 4px 0; }
               .pkg-desc { color: var(--text-muted); font-size: 0.85em; }
                .custom-row { display: flex; align-items: center; gap: 8px; margin: 8px 0; }
               .custom-row input[type=number] { padding: 6px; width: 120px; background: var(--bg-container);
                                                color: var(--text-primary); border: 1px solid var(--border-color); border-radius: 4px; }
               .hint { color: var(--text-hint); font-size: 0.85em; }
                .actions { margin-top: 20px; display: flex; gap: 12px; align-items: center; }
               .btn-primary { background: var(--accent); color: #fff; border: none; padding: 10px 18px;
                               border-radius: 4px; cursor: pointer; font-size: 1em; text-decoration: none; }
               .btn-primary:hover { background: var(--accent-hover); }
               .btn-primary:disabled { background: var(--text-muted); cursor: not-allowed; opacity: 0.6; }
               .btn-primary:disabled:hover { background: var(--text-muted); }
               .btn-link { color: var(--accent); text-decoration: none; }
                .btn-link:hover { text-decoration: underline; }
                table.review-table { width: 100%; border-collapse: collapse; margin: 12px 0; }
               table.review-table th, table.review-table td { border: 1px solid var(--border-table);
                                                                padding: 8px 10px; text-align: left; }
               table.review-table th { background: var(--bg-muted); width: 35%; }
               .total-row td, .total-row th { background: var(--success-bg); }
                .receipt { margin: 14px 0; }
               code { background: var(--bg-muted); padding: 2px 6px; border-radius: 3px; }
               .theme-selector-wrap { margin-left: auto; display: flex; align-items: center; gap: 6px; }
               .theme-selector-wrap select { background: var(--bg-container); color: var(--text-primary);
                                              border: 1px solid var(--border-color); border-radius: 4px; padding: 4px 6px; }
               .license-notice { font-size: 0.9em; }
               .license-notice a { color: var(--accent); text-decoration: underline; }
               .license-notice a:hover { color: var(--accent-hover); }
               .license-agreement { margin: 18px 0; padding: 14px; background: var(--bg-muted);
                                    border: 1px solid var(--border-color); border-radius: 6px; }
               .license-agreement h3 { margin-top: 0; font-size: 1.05em; color: var(--text-primary); }
               .license-summary { font-size: 0.9em; color: var(--text-secondary); margin-bottom: 12px; }
               .license-summary ul { margin: 8px 0; padding-left: 22px; }
               .license-summary li { margin: 4px 0; }
               .license-summary a { color: var(--accent); text-decoration: underline; }
               .license-summary a:hover { color: var(--accent-hover); }
               .license-checkbox { display: flex; align-items: flex-start; gap: 8px; padding: 10px;
                                   background: var(--bg-container); border: 2px solid var(--notice-border);
                                   border-radius: 4px; cursor: pointer; font-size: 0.95em; }
               .license-checkbox input[type=checkbox] { margin-top: 3px; flex-shrink: 0; }
               .license-checkbox a { color: var(--accent); text-decoration: underline; font-weight: 600; }
               .license-checkbox a:hover { color: var(--accent-hover); }
            </style>
        """.trimIndent()

    private fun navBar(active: String): String {
        fun cls(name: String) = if (name == active) "active" else ""
        return """
            <nav class="nav-bar">
                <a href="/usage" class="${cls("usage")}">📊 Usage</a>
                <a href="/credits" class="${cls("credits")}">💳 Buy Credits</a>
                <a href="/gifts/" class="${cls("gifts")}">🎁 Gifts</a>
                 <a href="/LICENSE.html" target="_blank" rel="noopener" class="${cls("license")}">📄 License</a>
               <span class="theme-selector-wrap">
                   <label for="theme-selector" style="font-size: 0.9em;">Theme:</label>
                   <select id="theme-selector">
                       <option value="auto">Auto</option>
                       <option value="light">Light</option>
                       <option value="dark">Dark</option>
                   </select>
               </span>
            </nav>
           <script>
               (function() {
                   if (typeof ThemeManager !== 'undefined') {
                       var sel = document.getElementById('theme-selector');
                       if (sel) ThemeManager.bindSelector(sel);
                   }
               })();
           </script>
        """.trimIndent()
    }


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