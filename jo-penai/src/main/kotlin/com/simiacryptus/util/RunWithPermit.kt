package com.simiacryptus.util

import com.simiacryptus.util.LoggerFactory
import java.util.concurrent.Semaphore

private val log = LoggerFactory.getLogger("RunWithPermitLogger")

fun <T> Semaphore.runWithPermit(function: () -> T): T {
    log.info("Attempting to acquire permit...")
    this.acquire()
    log.info("Permit acquired.")
    try {
        return function()
    } finally {
        log.info("Releasing permit.")
        this.release()
        log.info("Permit released.")
    }
}