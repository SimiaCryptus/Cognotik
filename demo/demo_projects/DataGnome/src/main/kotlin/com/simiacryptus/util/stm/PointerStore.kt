package com.simiacryptus.util.stm

interface PointerStore {
    fun set(key: Int, value: Int)
    fun set(vararg kv: Pair<Int, Int>)
    fun get(key: Int): Int
    fun newPointer(): Int
}