package com.simiacryptus.util.stm

import org.slf4j.LoggerFactory

class TransactionRoot(
) : Transactional() {
    override val blobs: BlobStorage = BlobDB()
    override val pointers: PointerStore = PointerDB()
    override val serializer = Serialization()

    override fun commit() {
        throw UnsupportedOperationException()
    }

    override fun attach(pointer: Pointer<*>) {
        throw UnsupportedOperationException()
    }

    companion object {
        val log = LoggerFactory.getLogger(TransactionRoot::class.java)
    }
}