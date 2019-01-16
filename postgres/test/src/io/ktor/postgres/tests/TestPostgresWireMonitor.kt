package io.ktor.postgres.tests

import io.ktor.postgres.*

class TestPostgresWireMonitor(private var skipHandshake: Boolean = true) : TextPostgresWireMonitor() {
    private val builder = StringBuilder()
    var emptyQueryReceived = false

    fun result() = builder.toString()

    override fun text(message: String) {
        if (!skipHandshake) {
            builder.appendln(message)
            println(message)
        }
    }

    override fun sentQuery(query: String) {
        // We use empty query to signal end of test session
        if (query.isNotEmpty()) {
            super.sentQuery(query)
        }
    }

    override fun receivedEmptyResponse() {
        emptyQueryReceived = true
    }

    override fun receivedReadyForQuery(transactionState: Byte) {
        super.receivedReadyForQuery(transactionState)
        skipHandshake = false
    }
}