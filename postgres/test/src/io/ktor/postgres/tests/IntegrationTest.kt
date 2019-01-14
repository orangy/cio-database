package io.ktor.postgres.tests

import ch.qos.logback.classic.*
import com.palantir.docker.compose.*
import com.palantir.docker.compose.configuration.*
import com.palantir.docker.compose.connection.waiting.*
import io.ktor.postgres.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.rules.*
import org.slf4j.*
import org.slf4j.Logger
import java.net.*
import java.util.concurrent.*

class IntegrationTest {

    @Rule
    @JvmField
    val timeout = Timeout(10, TimeUnit.HOURS)

    @Test
    fun simpleRequestTest() {
        runBlocking {
            PostgresConnection.create(
                address!!,
                POSTGRES_SERVICE,
                POSTGRES_USER,
                POSTGRES_PASSWORD,
                ConsolePostgresWireMonitor()

            ).use { connection ->
                repeat(10) {
                    connection.simpleQuery("SELECT $it")
                    yield()
                }
                delay(100)
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
