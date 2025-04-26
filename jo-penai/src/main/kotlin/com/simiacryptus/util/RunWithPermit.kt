package com.simiacryptus.util

import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore

private val log = LoggerFactory.getLogger("RunWithPermitLogger")

fun Semaphore.runWithPermit(function: () -> String): String {
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