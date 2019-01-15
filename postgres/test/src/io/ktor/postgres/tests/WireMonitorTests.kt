package io.ktor.postgres.tests

import io.ktor.postgres.*
import org.junit.*
import org.slf4j.*

@Ignore("These are not really tests, just a way to see if it works good")
class WireMonitorTests : IntegrationTestBase() {
    @Test
    fun loggerMonitor() {
        val logger = LoggerFactory.getLogger("PROTOCOL")
        val monitor = LoggerPostgresWireMonitor(logger)
        withConnection(monitor) {}
    }

    @Test
    fun consoleMonitor() {
        val monitor = ConsolePostgresWireMonitor()
        withConnection(monitor) {}
    }
}