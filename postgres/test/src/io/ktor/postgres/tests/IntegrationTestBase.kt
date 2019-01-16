package io.ktor.postgres.tests

import ch.qos.logback.classic.*
import com.jayway.awaitility.*
import com.palantir.docker.compose.*
import com.palantir.docker.compose.configuration.*
import com.palantir.docker.compose.connection.waiting.*
import io.ktor.network.selector.*
import io.ktor.postgres.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.*
import org.junit.*
import org.junit.rules.*
import org.slf4j.*
import org.slf4j.Logger
import java.net.*
import java.util.concurrent.*

abstract class IntegrationTestBase {

    @Rule
    @JvmField
    val timeout = Timeout(10, TimeUnit.HOURS)

    fun withConnection(monitor: PostgresWireMonitor? = null, body: suspend PostgresConnection.() -> Unit) {
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        val job = GlobalScope.async(Dispatchers.IO) {
            val connection = PostgresConnection.create(
                selectorManager,
                coroutineContext,
                address!!,
                POSTGRES_SERVICE,
                POSTGRES_USER,
                POSTGRES_PASSWORD,
                monitor
            )
            try {
                connection.body()
            } finally {
                connection.close()
                selectorManager.close()
            }
        }
        runBlocking {
            job.await()
        }
    }

    companion object {
        val POSTGRES_SERVICE = "postgres"
        val POSTGRES_PORT = 5432
        val POSTGRES_USER = "myuser"
        val POSTGRES_PASSWORD = "hello"

        var address: InetSocketAddress? = null

        init {
            Awaitility.doNotCatchUncaughtExceptionsByDefault()
        }
        
        @JvmField
        @ClassRule
        val dockerCompose = DockerComposeRule.builder()
            .file("test/resources/compose-postgres.yml") // TODO: point to processed resources folder in Build folder
            .waitingForService(
                POSTGRES_SERVICE,
                HealthChecks.toHaveAllPortsOpen()
            )
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
            DebugProbes.install()
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            DebugProbes.uninstall()
            dockerCompose.containers().container(POSTGRES_SERVICE).stop()
            address = null
        }
    }
}