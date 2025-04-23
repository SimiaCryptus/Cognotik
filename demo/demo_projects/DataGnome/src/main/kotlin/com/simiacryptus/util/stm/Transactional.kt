package com.simiacryptus.util.stm

import org.slf4j.LoggerFactory

abstract class Transactional {

    abstract val blobs: BlobStorage
    abstract val pointers: PointerStore
    abstract val serializer: Serialization

    abstract fun commit()
    inline fun <reified T : Any> newPointer() = Pointer<T>(pointers.newPointer(), this)
    inline fun <reified T : Any> root() = Pointer<T>(0, this)
    fun <T : Any> transact(fn: (Transactional) -> T): T {
        val prev = threadContext.get()
        try {
            val transaction = Transaction(this)
            threadContext.set(transaction)
            val result = fn(transaction)
            transaction.commit()
            return result
        } finally {
            threadContext.set(prev)
        }
    }

    abstract fun attach(pointer: Pointer<*>)

    companion object {
        val threadContext = ThreadLocal<Transactional?>()
        private val log = LoggerFactory.getLogger(Transactional::class.java)
    }
}