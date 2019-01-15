package io.ktor.postgres.tests

import io.ktor.postgres.*

class TestPostgresWireMonitor(private var skipHandshake : Boolean = true) : TextPostgresWireMonitor() {
    private val builder = StringBuilder()
    

    fun result() = builder.toString()

    override fun text(message: String) {
        if (!skipHandshake)
            builder.appendln(message)
    }

    override fun receivedReadyForQuery(transactionState: Byte) {
        super.receivedReadyForQuery(transactionState)
        skipHandshake = false
    }
}