package io.ktor.postgres

import kotlinx.coroutines.io.*
import kotlinx.io.core.*

suspend fun ByteReadChannel.receiveMessage(monitor: PostgresWireMonitor?) {
    readPostgresPacket { type, payload ->
        when (type) {
            BackendMessage.ROW_DESCRIPTION -> {
                payload.receiveRowDescription(monitor)
            }
            BackendMessage.PARAMETER_DESCRIPTION -> {
                payload.receiveParameterDescription(monitor)
            }
            BackendMessage.DATA_ROW -> {
                payload.receiveDataRow(monitor)
            }
            BackendMessage.EMPTY_QUERY_RESPONSE -> {
                monitor?.receivedEmptyResponse()
            }
            BackendMessage.PARSE_COMPLETE -> {
                monitor?.receivedGeneric(type.name)
            }
            BackendMessage.BIND_COMPLETE -> {
                monitor?.receivedGeneric(type.name)
            }
            BackendMessage.CLOSE_COMPLETE -> {
                monitor?.receivedGeneric(type.name)
            }
            BackendMessage.COMMAND_COMPLETE -> {
                val info = payload.readCString()
                monitor?.receivedComplete(info)
            }
            BackendMessage.READY_FOR_QUERY -> {
                payload.receiveReadyForQuery(monitor)
            }
            BackendMessage.ERROR_RESPONSE -> {
                val error = payload.receiveError()
                monitor?.receivedError(error)
                //throw error
            }
            else -> monitor?.receivedGeneric(type.name)
        }
    }
}

private fun ByteReadPacket.receiveDataRow(monitor: PostgresWireMonitor?) {
    val size = readShort().toInt() and 0xffff
    monitor?.receivedRow(size)
    repeat(size) { index ->
        val cellSize = readInt()
        val bytes = if (cellSize < 0) null else readBytes(cellSize)
        monitor?.receivedRowItem(index, bytes)
    }
}

private fun ByteReadPacket.receiveParameterDescription(monitor: PostgresWireMonitor?) {
    val size = readShort().toInt() and 0xffff
    monitor?.receivedParameterDescription(size)
    repeat(size) { index ->
        val typeOID = readInt()
        monitor?.receivedParameterDescriptionItem(index, typeOID)
    }
}

private fun ByteReadPacket.receiveRowDescription(monitor: PostgresWireMonitor?) {
    val size = readShort().toInt() and 0xffff
    monitor?.receivedRowDescription(size)
    repeat(size) { index ->
        val name = readCString()
        val tableOID = readInt()
        val attributeID = readShort().toInt() and 0xffff
        val typeOID = readInt()
        val typeSize = readShort().toInt() /* typeSize can be negative */
        val typeMod = readInt()
        // The format code being used for the field. Currently will be zero (text) or one (binary). 
        // In a RowDescription returned from the statement variant of Describe, the format code is not yet known
        // and will always be zero.
        val format = readShort().toInt()
        monitor?.receivedRowDescriptionItem(
            index,
            name,
            tableOID,
            attributeID,
            typeOID,
            typeSize,
            typeMod,
            format
        )
    }
}

internal fun ByteReadPacket.receiveError(): PostgresErrorException {
    var details: MutableMap<Char, String>? = null
    var message: String? = null
    var severity: String? = null

    loop@ while (remaining > 0) {
        val type = readByte().toChar()
        when (type) {
            'M' -> {
                message = readCString()
            }
            'S' -> {
                severity = readCString()
            }
            0.toChar() -> {
                if (remaining != 0L)
                    throw PostgresWireProtocolException("There are some remaining bytes in exception message: $remaining")
                break@loop
            }
            else -> {
                val text = readCString()
                if (details == null)
                    details = mutableMapOf()
                details[type] = text

            }
        }
    }

    return PostgresErrorException(message ?: "No message", severity ?: "UNKNOWN", details)
}
