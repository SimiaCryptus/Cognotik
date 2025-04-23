package com.simiacryptus.util.stm

import org.slf4j.LoggerFactory

class PointerDB : PointerStore {
    private var currentRevision: Int = 0
    private val pointers = mutableMapOf<Int, Int>()
    private val txlog = mutableListOf<Pair<Int, Int>>()

    override fun set(key: Int, value: Int) {
        pointers[key] = value
        txlog += key to value
        log.debug("Write ptr $key: $value")
        currentRevision++
    }

    override fun set(vararg kv: Pair<Int, Int>) {
        kv.forEach { (key, value) ->
            pointers[key] = value
            txlog += key to value
            log.debug("Write ptr $key: $value")
        }
        currentRevision++
    }

    override fun get(key: Int): Int {
        val v = pointers[key]
        log.debug("Read ptr $key: $v")
        return v!!
    }

    override fun newPointer(): Int {
        val id = pointers.size
        pointers[id] = -1
        txlog += id to -1
        log.debug("New ptr: $id")
        return id
    }

    companion object {
        private val log = LoggerFactory.getLogger(PointerDB::class.java)
    }
}