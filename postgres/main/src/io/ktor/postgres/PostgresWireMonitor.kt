package io.ktor.postgres

interface PostgresWireMonitor {
    fun sentStartup(version: Int, parameters: Map<String, String>)
    fun sentTerminate()
    fun sentAuthenticatePassword()
    fun sentAuthenticateMD5()
    fun sentQuery(query: String)
    fun sentParse(statementName: String, query: String, parametersTypeOIDs: IntArray)
    fun sentGeneric(type: String)
    fun sentBind(portalName: String, statementName: String)
    fun sentExecute(portalName: String, maxRows: Int)
    fun sentDescribePortal(portalName: String)
    fun sentDescribeStatement(statementName: String)
    fun sentSync()

    fun receivedAuthenticateMD5(salt: ByteArray)
    fun receivedAuthenticateCleartext()
    fun receivedAuthenticated()
    fun receivedReadyForQuery(transactionState: Byte)
    fun receivedSessionBackendData(pid: Int, secret: ByteArray)
    fun receivedSessionParameterStatus(key: String, value: String)
    fun receivedError(error: PostgresErrorException)
    fun receivedGeneric(type: String)
    fun receivedComplete(info: String)
    fun receivedRowDescription(count: Int)
    fun receivedRowDescriptionItem(index: Int, name: String, tableOID: Int, attributeID: Int, typeOID: Int, typeSize: Int, typeMod: Int, format: Int)
    fun receivedRow(count: Int)
    fun receivedRowItem(index: Int, bytes: ByteArray?)
    fun receivedEmptyResponse()
    fun receivedParameterDescription(count: Int)
    fun receivedParameterDescriptionItem(index: Int, typeOID: Int)
    fun receivedParseComplete()
    fun receivedBindComplete()
    fun receivedCloseComplete()
}

abstract class TextPostgresWireMonitor() : PostgresWireMonitor {
    abstract fun text(message: String)

    private fun received(message: String) {
        text("<- $message")
    }

    private fun sent(message: String) {
        text("-> $message")
    }

    override fun receivedGeneric(type: String) {
        received("$type (…)")
    }

    override fun sentGeneric(type: String) {
        sent("$type (…)")
    }

    override fun sentQuery(query: String) {
        sent("QUERY: $query")
    }

    override fun receivedSessionBackendData(pid: Int, secret: ByteArray) {
        received("BACKEND_KEY_DATA: pid=$pid key=${secret.toHexString()}")
    }

    override fun receivedSessionParameterStatus(key: String, value: String) {
        received("PARAMETER_STATUS: $key = $value")
    }

    override fun receivedError(error: PostgresErrorException) {
        received("ERROR_RESPONSE: ${error.message}, ${error.parts}")
    }

    override fun receivedReadyForQuery(transactionState: Byte) {
        val c = transactionState.toChar()
        val state = when (c) {
            'I' -> "IDLE"
            'T' -> "TRANSACTION"
            'E' -> "ERROR"
            else -> "UNKNOWN($c)"
        }
        received("READY_FOR_QUERY: $state")
    }

    override fun receivedAuthenticateMD5(salt: ByteArray) {
        received("AUTHENTICATION_REQUEST: MD5 requested with salt ${salt.toHexString()}")
    }

    override fun receivedAuthenticateCleartext() {
        received("AUTHENTICATION_REQUEST: Clear text requested")
    }

    override fun sentAuthenticatePassword() {
        sent("AUTHENTICATION_REQUEST: Sent password: ***")
    }

    override fun sentAuthenticateMD5() {
        sent("AUTHENTICATION_REQUEST: Sent MD5")
    }

    override fun receivedAuthenticated() {
        received("AUTHENTICATION_REQUEST: OK")
    }

    override fun sentStartup(version: Int, parameters: Map<String, String>) {
        sent("STARTUP: v${version shr 16}.${version and 0xFFFF}, $parameters")
    }

    override fun sentTerminate() {
        sent("TERMINATE")
    }


    override fun receivedComplete(info: String) {
        received("COMMAND_COMPLETE: $info")
    }

    override fun receivedEmptyResponse() {
        received("EMPTY_QUERY_RESPONSE")
    }

    override fun receivedRowDescription(count: Int) {
        received("ROW_DESCRIPTION: $count column(s)")
    }
    
    override fun receivedParameterDescription(count: Int) {
        received("PARAMETER_DESCRIPTION: $count parameter(s)")
    }

    override fun receivedParameterDescriptionItem(index: Int, typeOID: Int) {
        val type = PostgresType.findType(typeOID)
        received("PARAMETER_DESCRIPTION [#$index]: ${type?.name ?: typeOID}")
    }

    override fun receivedRowDescriptionItem(
        index: Int,
        name: String,
        tableOID: Int,
        attributeID: Int,
        typeOID: Int,
        typeSize: Int,
        typeMod: Int,
        format: Int
    ) {
        val type = PostgresType.findType(typeOID)
        received(
            buildString {
                append("ROW_DESCRIPTION [#$index]: '$name' : ")
                append("${type?.name ?: typeOID}")
                if (typeMod != -1) {
                    append(" mod:$typeMod")
                }
                append(" {$typeSize bytes, ${if (format == 0) "text" else "binary"}}")
                if (tableOID != 0) {
                    append("Table [$tableOID:$attributeID] ")
                }
            }
        )
    }

    override fun receivedRow(count: Int) {
        received("DATA_ROW: $count cell(s)")
    }

    override fun receivedRowItem(index: Int, bytes: ByteArray?) {
        received("DATA_ROW [#$index]: ${bytes?.toHexString(10)}")
    }

    override fun sentParse(statementName: String, query: String, parametersTypeOIDs: IntArray) {
        sent("PARSE '$statementName' (${parametersTypeOIDs.map {
            PostgresType.findType(it)?.name ?: it
        }.joinToString()}), Query: $query")
    }

    override fun sentBind(portalName: String, statementName: String) {
        sent("BIND PORTAL '$portalName' from STATEMENT '$statementName'")
    }

    override fun sentExecute(portalName: String, maxRows: Int) {
        if (maxRows > 0)
            sent("EXECUTE '$portalName' (max: $maxRows)")
        else
            sent("EXECUTE '$portalName'")
    }

    override fun sentDescribePortal(portalName: String) {
        sent("DESCRIBE PORTAL '$portalName'")
    }

    override fun sentDescribeStatement(statementName: String) {
        sent("DESCRIBE STATEMENT '$statementName'")
    }

    override fun sentSync() {
        sent("SYNC")
    }

    override fun receivedParseComplete() {
        received("PARSE_COMPLETE")
    }

    override fun receivedBindComplete() {
        received("BIND_COMPLETE")
    }

    override fun receivedCloseComplete() {
        received("CLOSE_COMPLETE")
    }
}

class ConsolePostgresWireMonitor() : TextPostgresWireMonitor() {
    override fun text(message: String) {
        println(message)
    }
}

