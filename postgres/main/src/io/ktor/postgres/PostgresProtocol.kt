package io.ktor.postgres

import io.ktor.network.sockets.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*

suspend fun Socket.handshake(
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
                    output.respondAuthenticate(payload, username, password, monitor)
                }
                BackendMessage.READY_FOR_QUERY -> {
                    val transactionState = payload.receiveReady(monitor)
                    return PostgresConnection(this, input, output, receivedProperties, monitor)
                }
                BackendMessage.BACKEND_KEY_DATA -> {
                    val backendPID = payload.readInt()
                    val backendSecret = payload.readBytes(4)
                    monitor?.receivedBackendSessionData(backendPID, backendSecret)

                    receivedProperties["PID"] = backendPID.toString()
                    receivedProperties["Secret"] = backendSecret.toString()
                }
                BackendMessage.PARAMETER_STATUS -> {
                    val key = payload.readCString()
                    val value = payload.readCString()
                    monitor?.receivedParameter(key, value)
                    receivedProperties[key] = value
                }
                BackendMessage.ERROR_RESPONSE -> {
                    val error = payload.readError()
                    monitor?.receivedError(error)
                    throw error
                }
                else -> throw PostgresWireProtocolException("Unexpected message $type during handshake.")

            }!!

            if (payload.remaining != 0L)
                throw PostgresWireProtocolException("Unexpected excessive ${payload.remaining} bytes in message $type.")
        }
    }
}

suspend fun ByteWriteChannel.respondAuthenticate(
    payload: ByteReadPacket,
    username: String,
    password: String?,
    monitor: PostgresWireMonitor?
): Unit? {
    val authType = AuthenticationType.fromCode(payload.readInt())
    return when (authType) {
        AuthenticationType.OK -> {
            monitor?.receivedAuthenticated()
        }
        AuthenticationType.CLEARTEXT_PASSWORD -> {
            monitor?.receivedAuthenticateCleartext()
            if (password == null)
                throw PostgresAuthenticationException("Password was not provided for clear text authentication.")
            respondAuthPlainText(password)
            monitor?.sentAuthenticatePassword()
        }
        AuthenticationType.MD5_PASSWORD -> {
            if (password == null)
                throw PostgresAuthenticationException("Password was not provided for MD5 authentication.")
            if (payload.remaining != 4L)
                throw PostgresAuthenticationException("MD5 salt size is invalid: expected 4 bytes, received ${payload.remaining}.")

            val salt = payload.readBytes(4)
            monitor?.receivedAuthenticateMD5(salt)
            respondAuthMD5(username, password, salt)
            monitor?.sentAuthenticateMD5()
        }
        else -> throw PostgresAuthenticationException("Unsupported auth format: $authType")
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

fun ByteReadPacket.receiveReady(monitor: PostgresWireMonitor?): Byte {
    val size = remaining.toInt()
    if (size != 1)
        throw PostgresWireProtocolException("READY_FOR_QUERY should have 1 byte of payload, got $size")

    /* ignored in negotiate transaction status indicator */
    val transactionState = readByte()
    monitor?.receivedReadyForQuery(transactionState)
    return transactionState
}
