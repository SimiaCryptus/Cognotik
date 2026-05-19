package com.simiacryptus.cognotik.webui.servlet.payment

import com.simiacryptus.cognotik.platform.model.UsageInterface
import com.simiacryptus.cognotik.platform.model.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * No-op payment provider – applies credits immediately without any real
 * payment processing.  This is the original behaviour of [CreditsServlet].
 *
 * Suitable for internal / development deployments where budgeting is
 * enforced by policy rather than by actual monetary transactions.
 */
class NoOpPaymentProvider(private val usageDB: UsageInterface) : PaymentProvider {

    override val name: String = "No-op (self-service)"
    override val requiresPayment: Boolean = false

    override fun initiateCheckout(
        req: HttpServletRequest,
        resp: HttpServletResponse,
        user: User,
        amount: Double,
        orderId: String
    ): PaymentProvider.CheckoutResult {
        val timestamp = Instant.now().toString()
        return try {
            val newBudget = usageDB.creditUser(
                user = user,
                amount = amount,
                comment = "Self-service credit purchase (no-op checkout) order=$orderId",
                metadata = mapOf(
                    "order_id" to orderId,
                    "source" to "self-service-checkout",
                    "applied_amount" to amount.toString(),
                    "timestamp" to timestamp,
                    "user_email" to (user.email ?: "")
                )
            )
            log.info("No-op credit applied: user=${user.email} amount=$amount order=$orderId newBudget=$newBudget")
            PaymentProvider.CheckoutResult.Completed(
                newBudget = newBudget,
                orderId = orderId,
                amount = amount
            )
        } catch (e: Exception) {
            log.error("Failed to credit user ${user.email} via no-op provider", e)
            PaymentProvider.CheckoutResult.Failed(
                "Unable to process credit at this time. Please try again later."
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NoOpPaymentProvider::class.java)
    }
}