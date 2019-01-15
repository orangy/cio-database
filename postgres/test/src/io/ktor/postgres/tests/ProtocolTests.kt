package io.ktor.postgres.tests

import io.ktor.postgres.*
import org.junit.Test
import kotlin.test.*

class ProtocolTests : IntegrationTestBase() {
    @Test
    fun simpleQueryInt() {
        val monitor = TestPostgresWireMonitor()
        withConnection(monitor) {
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
        withConnection(monitor) {
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
    fun simpleQueryMultiple() {
        val monitor = TestPostgresWireMonitor()
        withConnection(monitor) {
            sendSimpleQuery("SELECT 42, 'Hello!'")
        }
        assertEquals(
            """
-> QUERY: SELECT 42, 'Hello!'
<- ROW_DESCRIPTION: 2 column(s)
<- ROW_DESCRIPTION [#0]: '?column?' : int4 {4 bytes, text}
<- ROW_DESCRIPTION [#1]: '?column?' : text {-1 bytes, text}
<- DATA_ROW: 2 cell(s)
<- DATA_ROW [#0]: 3432
<- DATA_ROW [#1]: 48656c6c6f21
<- COMMAND_COMPLETE: SELECT 1
<- READY_FOR_QUERY: IDLE
-> TERMINATE
        """.trimIndent(), monitor.result().trim()
        )
    }

    @Test
    fun preparedStatementTest() {
        val monitor = TestPostgresWireMonitor()
        withConnection(monitor) {
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
