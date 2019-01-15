package io.ktor.postgres.tests

import io.ktor.postgres.*
import kotlinx.coroutines.*
import org.junit.*

class CoordinationTests : IntegrationTestBase() {
    @Test
    fun simpleQuery() {
        val monitor = ConsolePostgresWireMonitor()
        withConnection(monitor) {
            val result1 = executeQueryAsync("SELECT 1,'Hello!'") { receiver ->
                repeat(receiver.columns) { index ->
                    val name = receiver.names[index]
                    val typeName = receiver.types[index]?.name
                    val bytes = receiver.datas[index]
                    println("$name: $typeName = ${bytes?.let { String(it) }}")
                }
                receiver.datas[1]?.let { String(it) }
            }
            val result2 = executeQueryAsync("SELECT 2,'Bye!'") { receiver ->
                repeat(receiver.columns) { index ->
                    val name = receiver.names[index]
                    val typeName = receiver.types[index]?.name
                    val bytes = receiver.datas[index]
                    println("$name: $typeName = ${bytes?.let { String(it) }}")
                }
                receiver.datas[1]?.let { String(it) }
            }
            val results = awaitAll(result1, result2)
            println(results)
        }
    }
}

// This class is intended to be pooled, so it is mutable and can be reset
class SimpleQuerySequenceMonitor : GuardedWireMonitor() {
    var complete = false

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

    companion object {
        const val InitialRowSize = 16
    }
}

suspend fun <T> PostgresConnection.executeQueryAsync(
    query: String,
    consumer: (SimpleQuerySequenceMonitor) -> T
): Deferred<T> {
    sendSimpleQuery(query)
    val receiver = SimpleQuerySequenceMonitor()
    while (!receiver.complete)
        input.receiveMessage(receiver)
    return CompletableDeferred(consumer(receiver))
}
