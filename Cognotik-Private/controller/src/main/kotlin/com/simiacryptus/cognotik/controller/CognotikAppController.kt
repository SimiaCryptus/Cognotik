failureRetryCooldownMs = configLong(
"cognotik.workers.failureRetryCooldownMs", default = 30_000L
)!!,
billing = workerBilling,
billingIntervalSeconds = configLong(
"cognotik.workers.billing.intervalSeconds", default = 60L
)!!,
).also { it.start() }
}