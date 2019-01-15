package io.ktor.postgres.tests

import ch.qos.logback.classic.*
import com.palantir.docker.compose.*
import com.palantir.docker.compose.configuration.*
import com.palantir.docker.compose.connection.waiting.*
import io.ktor.postgres.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Test
import org.junit.rules.*
import org.slf4j.*
import org.slf4j.Logger
import java.net.*
import java.util.concurrent.*
import kotlin.test.*

class IntegrationTest {

    @Rule
    @JvmField
    val timeout = Timeout(10, TimeUnit.HOURS)

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

            /*
                            connection.sendSimpleQuery("SELECT 0")
                            connection.sendSimpleQuery("SELECT 'Hello, World!'")
            */
            sendParse("stmt", "SELECT $1", intArrayOf(PostgresType.getType("int4").oid))
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
-> PARSE stmt(int4), Query: SELECT ${'$'}1
-> BIND  from stmt
-> DESCRIBE PORTAL 
-> EXECUTE 
-> SYNC
<- PARSE_COMPLETE (…)
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

    fun withConnection(monitor: TestPostgresWireMonitor? = null, body: suspend PostgresConnection.() -> Unit) {
        runBlocking {
            PostgresConnection.create(address!!, POSTGRES_SERVICE, POSTGRES_USER, POSTGRES_PASSWORD, monitor).use {
                it.body()
                delay(10)
            }
        }
    }

    companion object {
        val POSTGRES_SERVICE = "postgres"
        val POSTGRES_PORT = 5432
        val POSTGRES_USER = "myuser"
        val POSTGRES_PASSWORD = "hello"

        var address: InetSocketAddress? = null

        @JvmField
        @ClassRule
        val dockerCompose = DockerComposeRule.builder()
            .file("test/resources/compose-postgres.yml") // TODO: point to processed resources folder in Build folder
            .waitingForService(POSTGRES_SERVICE, HealthChecks.toHaveAllPortsOpen())
            .shutdownStrategy(ShutdownStrategy.GRACEFUL)
            .build()!!

        @BeforeClass
        @JvmStatic
        fun init() {
            (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger).apply {
                level = Level.ALL
            }

            val postgres = dockerCompose
                .containers()
                .container(POSTGRES_SERVICE)
                .port(POSTGRES_PORT)!!

            address = InetSocketAddress(postgres.ip, postgres.externalPort)
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            dockerCompose.containers().container(POSTGRES_SERVICE).stop()
            address = null
        }
    }
}
