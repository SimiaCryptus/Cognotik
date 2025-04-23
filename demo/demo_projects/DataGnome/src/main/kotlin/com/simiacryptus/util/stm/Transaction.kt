package com.simiacryptus.util.stm

import org.slf4j.LoggerFactory

class Transaction(private val parent: Transactional) : Transactional() {

    override val blobs: BlobStorage get() = parent.blobs
    override val serializer: Serialization get() = parent.serializer
    override val pointers: Pointers = Pointers()

    private val attached = mutableSetOf<Pointer<*>>()

    inner class Pointers : PointerStore {
        val pointerSets: MutableMap<Int, Int> = mutableMapOf()
        val pointerGets: MutableMap<Int, Int> = mutableMapOf()

        override fun set(key: Int, value: Int) {
            pointerSets[key] = value
        }

        override fun set(vararg kv: Pair<Int, Int>) {
            pointerSets.putAll(kv)
        }

        override fun get(key: Int): Int {
            if (pointerSets.containsKey(key)) return pointerSets[key]!!
            val get = parent.pointers.get(key)
            pointerGets[key] = get
            return get
        }

        override fun newPointer(): Int {
            val newPointer = parent.pointers.newPointer()
            pointerSets[newPointer] = 0
            return newPointer
        }
    }

    override fun commit() {
        log.debug("Committing transaction")
        attached.forEach { it.check() }
        val parentPointers = parent.pointers
        synchronized(parentPointers) {
            pointers.pointerGets.forEach { (key, value) ->
                require(parentPointers.get(key) == value) { "Pointer $key was modified during transaction" }
            }
            pointers.pointerSets.forEach { (key, value) ->
                parentPointers.set(key, value)
            }
        }
    }

    override fun attach(pointer: Pointer<*>) {
        log.debug("Attaching $pointer")
        attached += pointer
    }

    companion object {
        private val log = LoggerFactory.getLogger(Transaction::class.java)
    }
}