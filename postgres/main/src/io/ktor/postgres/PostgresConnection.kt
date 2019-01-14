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
                                val typeOID =
                                    payload.readInt() // https://github.com/postgres/postgres/blob/master/src/include/catalog/pg_type.dat
                                val typeSize = payload.readShort().toInt() /* typeSize can be negative */
                                val typeMod = payload.readInt()
                                // The format code being used for the field. Currently will be zero (text) or one (binary). 
                                // In a RowDescription returned from the statement variant of Describe, the format code is not yet known
                                // and will always be zero.
                                val format = payload.readShort().toInt()
                                monitor?.receivedDescriptionColumn(
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
                        BackendMessage.DATA_ROW -> {
                            val size = payload.readShort().toInt() and 0xffff
                            monitor?.receivedRow(size)
                            repeat(size) { index ->
                                val cellSize = payload.readInt()
                                val bytes = if (cellSize < 0) null else payload.readBytes(cellSize)
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
                        BackendMessage.ERROR_RESPONSE -> {
                            val error = payload.readError()
                            monitor?.receivedError(error)
                            //throw error
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


    suspend fun sendSimpleQuery(query: String) {
        output.writePostgresPacket(FrontendMessage.QUERY) {
            writeCString(query)
        }
        monitor?.sentQuery(query)
    }

    suspend fun sendParse(statementName: String, query: String, parametersTypeOIDs: IntArray) {
        output.writePostgresPacket(FrontendMessage.PARSE) {
            writeCString(statementName)
            writeCString(query)
            writeShort(parametersTypeOIDs.size.toShort())
            parametersTypeOIDs.forEach { writeInt(it) }
        }
        monitor?.sentParse(statementName, query, parametersTypeOIDs)
    }

    private val DefaultFormat = intArrayOf()
    suspend fun sendBind(
        portalName: String,
        statementName: String,
        values: Array<ByteArray?>,
        inParametersFormats: IntArray = DefaultFormat,
        outParametersFormats: IntArray = DefaultFormat
    ) {
        output.writePostgresPacket(FrontendMessage.BIND) {
            writeCString(portalName)
            writeCString(statementName)
            writeShort(inParametersFormats.size.toShort())
            inParametersFormats.forEach { writeShort(it.toShort()) }
            writeShort(values.size.toShort())
            values.forEach {
                if (it == null)
                    writeInt(-1)
                else {
                    writeInt(it.size)
                    writeFully(it)
                }

            }
            writeShort(outParametersFormats.size.toShort())
            outParametersFormats.forEach { writeShort(it.toShort()) }
        }
        monitor?.sentBind(portalName, statementName)
    }

    suspend fun sendExecute(portalName: String, maxRows: Int = 0) {
        output.writePostgresPacket(FrontendMessage.EXECUTE) {
            writeCString(portalName)
            writeInt(maxRows)
        }
        monitor?.sentExecute(portalName, maxRows)
    }
    
    suspend fun sendDescribePortal(portalName: String) {
        output.writePostgresPacket(FrontendMessage.DESCRIBE) {
            writeByte('P'.toByte())
            writeCString(portalName)
        }
        monitor?.sentDescribePortal(portalName)
    }
    
    suspend fun sendDescribeStatement(statementName: String) {
        output.writePostgresPacket(FrontendMessage.DESCRIBE) {
            writeByte('S'.toByte())
            writeCString(statementName)
        }
        monitor?.sentDescribeStatement(statementName)
    }

    suspend fun sendSync() {
        output.writePostgresPacket(FrontendMessage.SYNC) {}
        monitor?.sentSync()
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