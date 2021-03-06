package io.ktor.postgres

import kotlinx.coroutines.io.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

internal suspend fun ByteWriteChannel.writePostgresPacket(type: FrontendMessage, block: BytePacketBuilder.() -> Unit) {
    if (type.code != 0.toChar())
        writeByte(type.code.toByte())
    val packet = buildPacket { block() }

    writeInt(4 + packet.remaining)
    writePacket(packet)
    flush()
}

internal suspend inline fun ByteReadChannel.readPostgresPacket(
    startUp: Boolean = false,
    body: (BackendMessage, ByteReadPacket) -> Unit
) {
    val type = BackendMessage.fromValue(if (!startUp) readByte() else 0)
    val payloadSize = readInt() - 4
    if (payloadSize < 0)
        throw PostgresWireProtocolException("Payload size should be non-negative: type=$type, payloadSize=$payloadSize")
    body(type, readPacket(payloadSize))
}


internal fun BytePacketBuilder.writeCString(value: String, charset: Charset = Charsets.UTF_8) {
    val data = value.toByteArray(charset)
    writeFully(data)
    writeByte(0)
}

internal fun ByteReadPacket.readCString(charset: Charset = Charsets.UTF_8): String = buildPacket {
    readUntilDelimiter(0, this)

    // skip delimiter
    readByte()
}.readText(charset)



