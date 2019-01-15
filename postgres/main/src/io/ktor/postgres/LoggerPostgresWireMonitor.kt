package io.ktor.postgres

import org.slf4j.*

class LoggerPostgresWireMonitor(val logger: Logger) : TextPostgresWireMonitor() {
    override fun text(message: String) {
        logger.trace(message)
    }
}