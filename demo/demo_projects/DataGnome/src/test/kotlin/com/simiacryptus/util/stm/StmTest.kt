package com.simiacryptus.util.stm

import com.simiacryptus.util.stm.Pointer
import com.simiacryptus.util.stm.TransactionRoot
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StmTest {

    @Test
    fun test() {
        val stm = TransactionRoot()
        stm.transact { txn ->
            val root = txn.root<Map<String, Pointer<*>>>()
            root.setValue(HashMap())
        }
        stm.transact { txn ->
            val root = txn.root<MutableMap<String, Pointer<*>>>()
            val newPointer = txn.newPointer<String>()
            val map = root.getValue<MutableMap<String, Pointer<*>>>()
            map["test"] = newPointer
            newPointer.setValue("foo")
        }
        stm.transact { txn ->
            val root = txn.root<MutableMap<String, Pointer<*>>>()
            val map = root.getValue<MutableMap<String, Pointer<*>>>()
            Assertions.assertEquals("foo", map["test"]?.getValue<String>())
        }
        try {
            stm.transact { txn ->
                val root = txn.root<MutableMap<String, Pointer<*>>>()
                val newPointer = txn.newPointer<String>()
                val map = root.getValue<MutableMap<String, Pointer<*>>>()
                map["test"] = newPointer
                newPointer.setValue("bar")
                throw RuntimeException("Rollback Transaction")
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Expected RuntimeException")
        } catch (_: RuntimeException) {
        }
        stm.transact { txn ->
            val root = txn.root<MutableMap<String, Pointer<*>>>()
            val map = root.getValue<MutableMap<String, Pointer<*>>>()
            Assertions.assertEquals("foo", map["test"]?.getValue<String>())
        }
    }
}