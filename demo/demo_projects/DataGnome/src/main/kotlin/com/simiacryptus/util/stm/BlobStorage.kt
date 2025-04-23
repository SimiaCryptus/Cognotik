package com.simiacryptus.util.stm

import com.fasterxml.jackson.core.type.TypeReference

interface BlobStorage {

    fun write(json: ByteArray): Int
    fun read(id: Int): ByteArray?

}

inline fun <reified T : Any> BlobStorage.get(id: Int, stm: Transactional): T {
    val bytes = read(id)
    val fromJson = stm.serializer.fromJson(bytes!!, object : TypeReference<T>() {})
    return fromJson
}