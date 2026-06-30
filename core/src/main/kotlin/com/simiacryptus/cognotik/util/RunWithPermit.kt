package com.simiacryptus.cognotik.util

import org.slf4j.LoggerFactory.getLogger
import java.util.concurrent.Semaphore

private val log = getLogger("RunWithPermitLogger")

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