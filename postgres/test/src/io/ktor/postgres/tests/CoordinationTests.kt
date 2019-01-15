package io.ktor.postgres.tests

import io.ktor.postgres.*
import kotlinx.coroutines.*
import kotlinx.io.pool.*
import org.junit.*

class CoordinationTests : IntegrationTestBase() {
    @Test
    fun simpleQuery() {
        val monitor = ConsolePostgresWireMonitor()
        withConnection(monitor) {
            val list = List(10) {
                executeQueryAsync("SELECT $it, 'Item #$it'")
            }.awaitAll()
            println(list)
        }
    }
}

// This class is intended to be pooled, so it is mutable and can be reset
class SimpleQuerySequenceMonitor : GuardedWireMonitor() {
    var complete = false

    // TODO: handle multiple result sets for batched queries
    var columns = 0
    var names = arrayOfNulls<String>(InitialRowSize)
    var types = arrayOfNulls<PostgresType>(InitialRowSize)
    var datas = arrayOfNulls<ByteArray>(InitialRowSize)

    fun reset() {
        complete = false
        names.fill(null)
        types.fill(null)
    }

    override fun receivedComplete(info: String) {
        // for multiple SQL statements in a single query this will be called for each statement
        // while receivedReadyForQuery will be called when the whole batch is complete
    }

    override fun receivedReadyForQuery(transactionState: Byte) {
        complete = true
    }

    override fun receivedRowDescription(count: Int) {
        if (names.size < count) {
            names = arrayOfNulls(count)
            types = arrayOfNulls(count)
        }
        columns = count
    }

    override fun receivedRowDescriptionItem(
        index: Int,
        name: String,
        tableOID: Int,
        attributeID: Int,
        typeOID: Int,
        typeSize: Int,
        typeMod: Int,
        format: Int
    ) {
        names[index] = name
        types[index] = PostgresType.findType(typeOID)
    }

    override fun receivedRow(count: Int) {
        if (datas.size < count) {
            datas = arrayOfNulls(count)
        }
    }

    override fun receivedRowItem(index: Int, bytes: ByteArray?) {
        datas[index] = bytes
    }

    // TODO: should a pool be per connection instead for locality and less contention?
    companion object : DefaultPool<SimpleQuerySequenceMonitor>(16) {
        const val InitialRowSize = 16
        
        override fun produceInstance() = SimpleQuerySequenceMonitor()
        override fun clearInstance(instance: SimpleQuerySequenceMonitor) = instance.apply { reset() }
    }
}

suspend fun PostgresConnection.executeQueryAsync(query: String): Deferred<String?> {
    sendSimpleQuery(query)
    return receiveAsync { input ->
        SimpleQuerySequenceMonitor.useInstance { receiver ->
            while (!receiver.complete)
                input.receiveMessage(receiver)

            // Process result
            repeat(receiver.columns) { index ->
                val name = receiver.names[index]
                val typeName = receiver.types[index]?.name
                val bytes = receiver.datas[index]
                println("$name: $typeName = ${bytes?.let { String(it) }}")
            }
            receiver.datas[1]?.let { String(it) }
        }
    }
}
