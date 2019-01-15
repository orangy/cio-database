package io.ktor.postgres

class UnexpectedException(message: String? = null) : Exception(message)
fun Unexpected():Nothing = throw UnexpectedException("This function was not expected at this moment")

open class GuardedWireMonitor : PostgresWireMonitor {
    override fun sentStartup(version: Int, parameters: Map<String, String>) : Unit = Unexpected()

    override fun sentTerminate() : Unit = Unexpected()

    override fun sentAuthenticatePassword() : Unit = Unexpected()

    override fun sentAuthenticateMD5() : Unit = Unexpected()

    override fun sentQuery(query: String) : Unit = Unexpected()

    override fun sentParse(statementName: String, query: String, parametersTypeOIDs: IntArray) : Unit = Unexpected()

    override fun sentGeneric(type: String) : Unit = Unexpected()

    override fun sentBind(portalName: String, statementName: String) : Unit = Unexpected()

    override fun sentExecute(portalName: String, maxRows: Int) : Unit = Unexpected()

    override fun sentDescribePortal(portalName: String) : Unit = Unexpected()

    override fun sentDescribeStatement(statementName: String) : Unit = Unexpected()

    override fun sentSync() : Unit = Unexpected()

    override fun receivedAuthenticateMD5(salt: ByteArray) : Unit = Unexpected()

    override fun receivedAuthenticateCleartext() : Unit = Unexpected()

    override fun receivedAuthenticated() : Unit = Unexpected()

    override fun receivedReadyForQuery(transactionState: Byte) : Unit = Unexpected()

    override fun receivedSessionBackendData(pid: Int, secret: ByteArray) : Unit = Unexpected()

    override fun receivedSessionParameterStatus(key: String, value: String) : Unit = Unexpected()

    override fun receivedError(error: PostgresErrorException) : Unit = Unexpected()

    override fun receivedGeneric(type: String) : Unit = Unexpected()

    override fun receivedComplete(info: String) : Unit = Unexpected()

    override fun receivedRowDescription(count: Int) : Unit = Unexpected()

    override fun receivedRowDescriptionItem(
        index: Int,
        name: String,
        tableOID: Int,
        attributeID: Int,
        typeOID: Int,
        typeSize: Int,
        typeMod: Int,
        format: Int
    ) : Unit = Unexpected()

    override fun receivedRow(count: Int) : Unit = Unexpected()

    override fun receivedRowItem(index: Int, bytes: ByteArray?) : Unit = Unexpected()

    override fun receivedEmptyResponse() : Unit = Unexpected()

    override fun receivedParameterDescription(count: Int) : Unit = Unexpected()

    override fun receivedParameterDescriptionItem(index: Int, typeOID: Int) : Unit = Unexpected()
}