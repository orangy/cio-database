package io.ktor.postgres

interface PostgresWireMonitor {
    fun sentStartup(version: Int, parameters: Map<String, String>)
    fun receivedAuthenticateMD5(salt: ByteArray)
    fun receivedAuthenticateCleartext()
    fun receivedAuthenticated()
    fun receivedReadyForQuery(transactionState: Byte)
    fun receivedBackendSessionData(pid: Int, secret: ByteArray)
    fun receivedParameter(key: String, value: String)
    fun receivedError(error: PostgresErrorException)
    fun sentTerminate()
    fun sentAuthenticatePassword()
    fun sentAuthenticateMD5()
    fun sentQuery(query: String)
    fun receivedGeneric(type: String)
    fun receivedComplete(info: String)
    fun receivedDescription(count: Int)
    fun receivedDescriptionRow(
        index: Int,
        name: String,
        tableOID: Int,
        attributeID: Int,
        typeOID: Int,
        typeSize: Int,
        typeMod: Int,
        format: Int
    )

    fun receivedRow(count: Int)
    fun receivedRowCell(index: Int, bytes: ByteArray?)
    fun receivedEmptyResponse()
}

class ConsolePostgresWireMonitor() : TextPostgresWireMonitor() {
    override fun text(message: String) {
        println(message)
    }
}

abstract class TextPostgresWireMonitor() : PostgresWireMonitor {
    abstract fun text(message: String)
    
    private fun received(message: String) {
        text("<- $message")
    }

    private fun sent(message: String) {
        text("-> $message")
    }

    override fun sentQuery(query: String) {
        sent("QUERY: $query")
    }

    override fun receivedBackendSessionData(pid: Int, secret: ByteArray) {
        received("BACKEND_KEY_DATA: pid=$pid key=${secret.toHexString()}")
    }

    override fun receivedParameter(key: String, value: String) {
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

    override fun receivedGeneric(type: String) {
        received("$type (…)")
    }
    
    override fun receivedComplete(info: String) {
        received("COMMAND_COMPLETE: $info")
    }

    override fun receivedDescription(count: Int) {
        received("ROW_DESCRIPTION: $count column(s)")
    }

    override fun receivedEmptyResponse() {
        received("EMPTY_QUERY_RESPONSE")
    }

    override fun receivedDescriptionRow(
        index: Int,
        name: String,
        tableOID: Int,
        attributeID: Int,
        typeOID: Int,
        typeSize: Int,
        typeMod: Int,
        format: Int
    ) {
        received("ROW_DESCRIPTION [#$index]: $name Table:$tableOID, Attribute:$attributeID, Type:$typeOID, Mod: $typeMod, Size: $typeSize, Format: $format")
    }

    override fun receivedRow(count: Int) {
        received("DATA_ROW: $count cell(s)")
    }

    override fun receivedRowCell(index: Int, bytes: ByteArray?) {
        received("DATA_ROW [#$index]: ${bytes?.toHexString(10)}")
    }
}
