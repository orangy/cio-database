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
    val socket: Socket,
    val input: ByteReadChannel,
    val output: ByteWriteChannel,
    val properties: Map<String, String>,
    val monitor: PostgresWireMonitor?
) : CoroutineScope, AutoCloseable {
    override val coroutineContext: CoroutineContext = Dispatchers.Default + CoroutineName("ktor-postgres")

    override fun close() {
        runBlocking {
            // TODO: decide on suspendability of close
            output.writePostgresPacket(FrontendMessage.TERMINATE) {}
        }
        monitor?.sentTerminate()
        socket.close()
    }

    private val receiveActor = actor<(suspend (ByteReadChannel) -> Any?)>(start = CoroutineStart.LAZY) {
        for (reader in channel) {
            reader(input)
        }
    }

    suspend fun <T> receiveAsync(function: suspend (ByteReadChannel) -> T): Deferred<T> {
        val deferred = CompletableDeferred<T>()
        receiveActor.send {
            deferred.complete(function(it))
        }
        return deferred
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




