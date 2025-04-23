package com.simiacryptus.util.stm

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.type.TypeReference
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException

class Pointer<T : Any>(
    val id: Int = 0,
    @JsonIgnore val stm: Transactional? = Transactional.threadContext.get()
) {
    @JsonIgnore
    private var originalRaw: ByteArray? = null

    @JsonIgnore
    var heapObject: T? = null

//  var v : T get() = getValue<T>()
//    set(value) { setValue(value)}

    inline fun <reified TT : Any> getValue(): TT {
        if (null != heapObject) return heapObject!! as TT
        val typeInfo: TypeReference<TT> = object : TypeReference<TT>() {}
        heapObject = stm?.serializer?.fromJson(raw, typeInfo) as? T
        return (heapObject ?: throw IllegalStateException()) as TT
    }

    fun setValue(value: T) {
        stm ?: throw IllegalStateException()
        raw = stm.serializer.toJson(value)
    }

    fun check() {
        stm ?: throw IllegalStateException()
        log.info("Checking pointer $id")
        when {
            originalRaw == null -> {}
            heapObject == null -> {}
            else -> {
                val bytes = stm.serializer.toJson(heapObject!!)
                if (!bytes.contentEquals(originalRaw)) {
                    log.info("Updating changed blob $id: ${bytes.size} bytes: ${String(bytes)}")
                    val blobId = stm.blobs.write(bytes)
                    stm.pointers.set(id, blobId)
                }
            }
        }
    }


    @get:JsonIgnore
    @set:JsonIgnore
    var raw: ByteArray
        get() {
            stm ?: throw IllegalStateException()
            val blobId = stm.pointers.get(id)
            val bytes = stm.blobs.read(blobId)
            if (null == originalRaw) {
                originalRaw = bytes
                stm.attach(this@Pointer)
            }
            return bytes!!
        }
        set(value) {
            stm ?: throw IllegalStateException()
            val blobID = stm.blobs.write(value)
            stm.pointers.set(id, blobID)
        }


    companion object {
        private val log = LoggerFactory.getLogger(Pointer::class.java)
    }
}