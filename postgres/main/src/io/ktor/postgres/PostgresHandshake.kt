package io.ktor.postgres

import io.ktor.network.sockets.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import kotlin.coroutines.*

suspend fun Socket.handshake(
    context: CoroutineContext,
    database: String,
    username: String,
    password: String?,
    parameters: Map<String, String>,
    monitor: PostgresWireMonitor?
): PostgresConnection {
    val input = openReadChannel()
    val output = openWriteChannel()

    output.sendStartup(username, database, parameters, monitor)

    val receivedProperties = mutableMapOf<String, String>()
    loop@ while (true) {
        input.readPostgresPacket { type, payload ->
            when (type) {
                BackendMessage.AUTHENTICATION_REQUEST -> {
                    output.respondAuthenticationRequest(payload, username, password, monitor)
                }
                BackendMessage.READY_FOR_QUERY -> {
                    payload.receiveReadyForQuery(monitor)
                    return PostgresConnection(
                        context,
                        this@handshake,
                        input,
                        output,
                        receivedProperties,
                        monitor
                    )
                }
                BackendMessage.BACKEND_KEY_DATA -> {
                    val backendPID = payload.readInt()
                    val backendSecret = payload.readBytes(4)
                    monitor?.receivedSessionBackendData(backendPID, backendSecret)

                    receivedProperties["PID"] = backendPID.toString()
                    receivedProperties["Secret"] = backendSecret.toString()
                }
                BackendMessage.PARAMETER_STATUS -> {
                    val key = payload.readCString()
                    val value = payload.readCString()
                    monitor?.receivedSessionParameterStatus(key, value)
                    receivedProperties[key] = value
                }
                BackendMessage.ERROR_RESPONSE -> {
                    val error = payload.receiveError()
                    monitor?.receivedError(error)
                    throw error
                }
                else -> throw PostgresWireProtocolException("Unexpected message $type during handshake.")
            }

            if (payload.remaining != 0L) // TODO: Should we check it? If protocol is extended with extra info, it will break it
                throw PostgresWireProtocolException("Unexpected excessive ${payload.remaining} bytes in message $type.")
        }
    }
}

suspend fun ByteWriteChannel.sendStartup(
    username: String,
    database: String,
    parameters: Map<String, String>,
    monitor: PostgresWireMonitor?
) {
    val sendParameters = mutableMapOf<String, String>().apply {
        put("user", username)
        put("database", database)
        put("application_name", "ktor-cio")
        put("client_encoding", "UTF8")
        putAll(parameters)
    }
    writePostgresPacket(FrontendMessage.STARTUP_MESSAGE) {
        /**
         * The protocol version number.
         * The most significant 16 bits are the major version number (3 for the protocol described here).
         * The least significant 16 bits are the minor version number (0 for the protocol described here).
         */
        writeInt(PostgresConnection.protocolVersion)
        for ((key, value) in sendParameters) {
            writeCString(key)
            writeCString(value)
        }
        writeCString("")
    }
    monitor?.sentStartup(PostgresConnection.protocolVersion, sendParameters)
}

fun ByteReadPacket.receiveReadyForQuery(monitor: PostgresWireMonitor?): Byte {
    val size = remaining.toInt()
    if (size != 1)
        throw PostgresWireProtocolException("READY_FOR_QUERY should have 1 byte of payload, got $size")

    /* ignored in negotiate transaction status indicator */
    val transactionState = readByte()
    monitor?.receivedReadyForQuery(transactionState)
    return transactionState
}
