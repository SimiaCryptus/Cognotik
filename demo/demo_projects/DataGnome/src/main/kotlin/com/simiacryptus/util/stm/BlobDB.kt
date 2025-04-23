package com.simiacryptus.util.stm

import org.slf4j.LoggerFactory

class BlobDB : BlobStorage {
    private val data = mutableMapOf<Int, ByteArray>()
    override fun write(json: ByteArray): Int {
        val id = data.count()
        log.debug("Writing $id: ${json.size} bytes: ${String(json)}")
        data[id] = json
        return id
    }

    /*
     This Kotlin function, named `read`, is an overridden method that takes an integer parameter `id`. It retrieves a
     `ByteArray` from a `data` collection using the provided `id` as the key. The function logs a debug message that includes
     the `id`, the size of the byte array, and the string representation of the byte array. Finally, it returns the byte
     array. If the `id` does not exist in the `data` collection, the function returns `null`.
    */
    override fun read(id: Int) = data[id]?.also {
        log.debug("Reading $id: ${it.size} bytes: ${String(it)}")
    }


    companion object {
        val log = LoggerFactory.getLogger(BlobDB::class.java)
    }
}