@file:Suppress("EXPERIMENTAL_API_USAGE")

package io.ktor.postgres

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import java.net.*
import kotlin.coroutines.*

class PostgresConnection(
    parentCoroutineContext: CoroutineContext,
    val socket: Socket,
    val input: ByteReadChannel,
    val output: ByteWriteChannel,
    val properties: Map<String, String>,
    val monitor: PostgresWireMonitor?
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = parentCoroutineContext + CoroutineName("postgres-context")

    suspend fun close() {
        output.writePostgresPacket(FrontendMessage.TERMINATE) {}
        monitor?.sentTerminate()
        
        @Suppress("BlockingMethodInNonBlockingContext")
        socket.close()
        receiveActor.close()
    }

    // TODO: Change to CoroutineStart.LAZY when join bug is fixed
    private val receiveActor = actor<(suspend (ByteReadChannel) -> Any?)>(capacity = 5, start = CoroutineStart.DEFAULT) {
        for (reader in channel) {
            try {
                reader(input)
            } catch (e: Exception) {
                println(e.toString())
                throw e
            }
        }
    }

    suspend fun <T> receiveAsync(function: suspend (ByteReadChannel) -> T): Deferred<T> {
        val deferred = CompletableDeferred<T>()
        receiveActor.send {
            val value = function(it)
            deferred.complete(value)
        }
        return deferred
    }

    companion object {
        internal const val protocolVersion = 0x0003_0000

        suspend fun create(
            selectorManager: SelectorManager,
            context: CoroutineContext,
            address: InetSocketAddress,
            database: String,
            username: String,
            password: String?,
            monitor: PostgresWireMonitor? = null,
            parameters: Map<String, String> = mapOf()
        ): PostgresConnection {
            val socket = aSocket(selectorManager).tcp().tcpNoDelay().connect(address)
            return socket.handshake(context, database, username, password, parameters, monitor)
        }
    }
}




