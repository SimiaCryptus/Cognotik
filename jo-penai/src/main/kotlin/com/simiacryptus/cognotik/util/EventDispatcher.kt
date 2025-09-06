package com.simiacryptus.cognotik.util

open class EventDispatcher {
    companion object {
        private val log = LoggerFactory.getLogger(EventDispatcher::class.java)
    }

    private val listeners = mutableListOf<() -> Unit>()
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyListeners() {
        listeners.forEach {
            try {
                it()
            } catch (e: Throwable) {
                log.error(e.message, e)
            }
        }
    }
}