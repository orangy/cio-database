package io.ktor.postgres.tests

import io.ktor.postgres.*
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

class ProtocolTests : IntegrationTestBase() {

    private fun withReceivingConnection(monitor: TestPostgresWireMonitor, body: suspend PostgresConnection.() -> Unit) =
        withConnection(monitor) {
            val receiveJob = launch {
                while (!monitor.emptyQueryReceived) {
                    input.receiveMessage(monitor)
                }
            }
            body()
            sendSimpleQuery("");
            receiveJob.join()
        }

    @Test
    fun connectionProperties() {
        val monitor = TestPostgresWireMonitor(false)
        withReceivingConnection(monitor) {
            assertEquals("UTF8", properties["client_encoding"])
            assertEquals("UTC", properties["TimeZone"])
        }
    }
    
    @Test
    fun simpleQueryInt() {
        val monitor = TestPostgresWireMonitor()
        withReceivingConnection(monitor) {
            sendSimpleQuery("SELECT 0")
        }
        assertEquals(
            """
-> QUERY: SELECT 0
<- ROW_DESCRIPTION: 1 column(s)
<- ROW_DESCRIPTION [#0]: '?column?' : int4 {4 bytes, text}
<- DATA_ROW: 1 cell(s)
<- DATA_ROW [#0]: 30
<- COMMAND_COMPLETE: SELECT 1
<- READY_FOR_QUERY: IDLE
-> TERMINATE
        """.trimIndent(), monitor.result().trim()
        )
    }

    @Test
    fun simpleQueryString() {
        val monitor = TestPostgresWireMonitor()
        withReceivingConnection(monitor) {
            sendSimpleQuery("SELECT 'Hello!'")
        }
        assertEquals(
            """
-> QUERY: SELECT 'Hello!'
<- ROW_DESCRIPTION: 1 column(s)
<- ROW_DESCRIPTION [#0]: '?column?' : text {-1 bytes, text}
<- DATA_ROW: 1 cell(s)
<- DATA_ROW [#0]: 48656c6c6f21
<- COMMAND_COMPLETE: SELECT 1
<- READY_FOR_QUERY: IDLE
-> TERMINATE
        """.trimIndent(), monitor.result().trim()
        )
    }

    @Test
    fun simpleQueryPipeline() {
        val monitor = TestPostgresWireMonitor()
        withReceivingConnection(monitor) {
            sendSimpleQuery("SELECT 44")
            sendSimpleQuery("SELECT 'Hello!'")
        }
        assertEquals(
            """
-> QUERY: SELECT 44
-> QUERY: SELECT 'Hello!'
<- ROW_DESCRIPTION: 1 column(s)
<- ROW_DESCRIPTION [#0]: '?column?' : int4 {4 bytes, text}
<- DATA_ROW: 1 cell(s)
<- DATA_ROW [#0]: 3434
<- COMMAND_COMPLETE: SELECT 1
<- READY_FOR_QUERY: IDLE
<- ROW_DESCRIPTION: 1 column(s)
<- ROW_DESCRIPTION [#0]: '?column?' : text {-1 bytes, text}
<- DATA_ROW: 1 cell(s)
<- DATA_ROW [#0]: 48656c6c6f21
<- COMMAND_COMPLETE: SELECT 1
<- READY_FOR_QUERY: IDLE
-> TERMINATE
        """.trimIndent(), monitor.result().trim()
        )
    }
    
    @Test
    fun simpleQueryMultiple() {
        val monitor = TestPostgresWireMonitor()
        withReceivingConnection(monitor) {
            sendSimpleQuery("SELECT 44; SELECT 'Hello!'")
        }
        assertEquals(
            """
-> QUERY: SELECT 44; SELECT 'Hello!'
<- ROW_DESCRIPTION: 1 column(s)
<- ROW_DESCRIPTION [#0]: '?column?' : int4 {4 bytes, text}
<- DATA_ROW: 1 cell(s)
<- DATA_ROW [#0]: 3434
<- COMMAND_COMPLETE: SELECT 1
<- ROW_DESCRIPTION: 1 column(s)
<- ROW_DESCRIPTION [#0]: '?column?' : text {-1 bytes, text}
<- DATA_ROW: 1 cell(s)
<- DATA_ROW [#0]: 48656c6c6f21
<- COMMAND_COMPLETE: SELECT 1
<- READY_FOR_QUERY: IDLE
-> TERMINATE
        """.trimIndent(), monitor.result().trim()
        )
    }

    @Test
    fun preparedStatementTest() {
        val monitor = TestPostgresWireMonitor()
        withReceivingConnection(monitor) {
            sendParse("stmt", "SELECT $1", intArrayOf(PostgresType.getType("int4").oid))
            sendDescribeStatement("stmt")
            sendBind(
                "",
                "stmt",
                arrayOf(byteArrayOf(0x30, 0x31, 0x32, 0x33)),
                inParametersFormats = intArrayOf(1),
                outParametersFormats = intArrayOf(1)
            )
            sendDescribePortal("")
            sendExecute("")
            sendSync()
        }


        assertEquals(
            """
-> PARSE 'stmt' (int4), Query: SELECT ${'$'}1
-> DESCRIBE STATEMENT 'stmt'
-> BIND PORTAL '' from STATEMENT 'stmt'
-> DESCRIBE PORTAL ''
-> EXECUTE ''
-> SYNC
<- PARSE_COMPLETE (…)
<- PARAMETER_DESCRIPTION: 1 parameter(s)
<- PARAMETER_DESCRIPTION [#0]: int4
<- ROW_DESCRIPTION: 1 column(s)
<- ROW_DESCRIPTION [#0]: '?column?' : int4 {4 bytes, text}
<- BIND_COMPLETE (…)
<- ROW_DESCRIPTION: 1 column(s)
<- ROW_DESCRIPTION [#0]: '?column?' : int4 {4 bytes, binary}
<- DATA_ROW: 1 cell(s)
<- DATA_ROW [#0]: 30313233
<- COMMAND_COMPLETE: SELECT 1
<- READY_FOR_QUERY: IDLE
-> TERMINATE
            """.trimIndent(), monitor.result().trim()
        )
    }
}
