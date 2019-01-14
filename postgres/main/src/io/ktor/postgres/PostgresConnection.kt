package io.ktor.postgres

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import java.net.*
import kotlin.coroutines.*

class PostgresConnection(
    val socket: Socket,
    val input: ByteReadChannel,
    val output: ByteWriteChannel,
    val properties: Map<String, String>,
    val monitor: PostgresWireMonitor?
) : CoroutineScope, AutoCloseable {
    override val coroutineContext: CoroutineContext = Dispatchers.Default + CoroutineName("ktor-postgres")

    init {
        launch {
            while (!socket.isClosed) {
                input.readPostgresPacket { type, payload ->
                    when (type) {
                        BackendMessage.ROW_DESCRIPTION -> {
                            val size = payload.readShort().toInt() and 0xffff
                            monitor?.receivedDescription(size)
                            repeat(size) { index ->
                                val name = payload.readCString()
                                val tableOID = payload.readInt()
                                val attributeID = payload.readShort().toInt() and 0xffff
                                val typeOID = payload.readInt() // https://github.com/postgres/postgres/blob/master/src/include/catalog/pg_type.dat
                                val typeSize = payload.readShort().toInt() /* typeSize can be negative */
                                val typeMod = payload.readInt()
                                // The format code being used for the field. Currently will be zero (text) or one (binary). 
                                // In a RowDescription returned from the statement variant of Describe, the format code is not yet known
                                // and will always be zero.
                                val format = payload.readShort().toInt()
                                monitor?.receivedDescriptionRow(index, name, tableOID, attributeID, typeOID, typeSize, typeMod, format)
                            }
                        }
                        BackendMessage.DATA_ROW -> {
                            val size = payload.readShort().toInt() and 0xffff
                            monitor?.receivedRow(size)
                            repeat(size) { index ->
                                val cellSize = payload.readInt()
                                val bytes= if (cellSize < 0) null else payload.readBytes(cellSize)
                                monitor?.receivedRowCell(index, bytes)
                            }
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
                            payload.receiveReady(monitor)
                        }
                        else -> monitor?.receivedGeneric(type.name)
                    }

                }
            }
        }
    }


    override fun close() {
        runBlocking {
            // TODO: decide on suspendability of close
            output.writePostgresPacket(FrontendMessage.TERMINATE) {}
        }
        monitor?.sentTerminate()
        socket.close()
    }


    suspend fun simpleQuery(request: String) {
        output.writePostgresPacket(FrontendMessage.QUERY) {
            writeCString(request)
        }
        monitor?.sentQuery(request)
    }

    companion object {
        internal const val protocolVersion = 0x0003_0000

        suspend fun create(
            address: InetSocketAddress,
            database: String,
            username: String,
            password: String?,
            monitor: PostgresWireMonitor? = null,
            parameters: Map<String, String> = mapOf()
        ): PostgresConnection {
            val selectorManager = ActorSelectorManager(coroutineContext + Job())
            val socket = aSocket(selectorManager).tcp().tcpNoDelay().connect(address)
            return socket.handshake(database, username, password, parameters, monitor)
        }
    }
}