package com.simiacryptus.cognotik.util

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadFactory

class RecordingThreadFactory(
    val session: Session,
    val user: User?
) : ThreadFactory {
    private val inner = ThreadFactoryBuilder().setNameFormat("Session $session; User $user; #%d").build()
    val threads = mutableSetOf<Thread>()
    override fun newThread(r: Runnable): Thread {
        log.debug("Creating new thread for session: {}, user: {}", session, user)
        inner.newThread(r).also {
            threads.add(it)
            return it
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RecordingThreadFactory::class.java)
    }
}