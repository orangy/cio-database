package io.ktor.postgres

import kotlinx.io.core.*

suspend fun PostgresConnection.sendSimpleQuery(query: String) {
    output.writePostgresPacket(FrontendMessage.QUERY) {
        writeCString(query)
    }
    monitor?.sentQuery(query)
}

suspend fun PostgresConnection.sendParse(statementName: String, query: String, parametersTypeOIDs: IntArray) {
    output.writePostgresPacket(FrontendMessage.PARSE) {
        writeCString(statementName)
        writeCString(query)
        writeShort(parametersTypeOIDs.size.toShort())
        parametersTypeOIDs.forEach { writeInt(it) }
    }
    monitor?.sentParse(statementName, query, parametersTypeOIDs)
}

private val DefaultFormat = intArrayOf()

suspend fun PostgresConnection.sendBind(
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

suspend fun PostgresConnection.sendExecute(portalName: String, maxRows: Int = 0) {
    output.writePostgresPacket(FrontendMessage.EXECUTE) {
        writeCString(portalName)
        writeInt(maxRows)
    }
    monitor?.sentExecute(portalName, maxRows)
}

suspend fun PostgresConnection.sendDescribePortal(portalName: String) {
    output.writePostgresPacket(FrontendMessage.DESCRIBE) {
        writeByte('P'.toByte())
        writeCString(portalName)
    }
    monitor?.sentDescribePortal(portalName)
}

suspend fun PostgresConnection.sendDescribeStatement(statementName: String) {
    output.writePostgresPacket(FrontendMessage.DESCRIBE) {
        writeByte('S'.toByte())
        writeCString(statementName)
    }
    monitor?.sentDescribeStatement(statementName)
}

suspend fun PostgresConnection.sendSync() {
    output.writePostgresPacket(FrontendMessage.SYNC) {}
    monitor?.sentSync()
}
