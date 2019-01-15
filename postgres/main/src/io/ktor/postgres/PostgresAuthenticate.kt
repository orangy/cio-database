package io.ktor.postgres

import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import java.security.*

suspend fun ByteWriteChannel.respondAuthenticationRequest(
    payload: ByteReadPacket,
    username: String,
    password: String?,
    monitor: PostgresWireMonitor?
): Unit? {
    val authType = AuthenticationType.fromCode(payload.readInt())
    return when (authType) {
        AuthenticationType.OK -> {
            monitor?.receivedAuthenticated()
        }
        AuthenticationType.CLEARTEXT_PASSWORD -> {
            monitor?.receivedAuthenticateCleartext()
            if (password == null)
                throw PostgresAuthenticationException("Password was not provided for clear text authentication.")
            respondAuthPlainText(password)
            monitor?.sentAuthenticatePassword()
        }
        AuthenticationType.MD5_PASSWORD -> {
            if (password == null)
                throw PostgresAuthenticationException("Password was not provided for MD5 authentication.")
            if (payload.remaining != 4L)
                throw PostgresAuthenticationException("MD5 salt size is invalid: expected 4 bytes, received ${payload.remaining}.")

            val salt = payload.readBytes(4)
            monitor?.receivedAuthenticateMD5(salt)
            respondAuthMD5(username, password, salt)
            monitor?.sentAuthenticateMD5()
        }
        else -> throw PostgresAuthenticationException("Unsupported auth format: $authType")
    }
}

internal suspend fun ByteWriteChannel.respondAuthPlainText(password: String) {
    writePostgresPacket(FrontendMessage.PASSWORD_MESSAGE) {
        writeCString(password)
    }
}

internal suspend fun ByteWriteChannel.respondAuthMD5(user: String, password: String, salt: ByteArray) {
    val encoder = MessageDigest.getInstance("MD5")!!

    fun md5(password: String, salt: ByteArray): String {
        encoder.update(password.toByteArray())
        encoder.update(salt)

        return encoder.digest().toHexString()
    }

    writePostgresPacket(FrontendMessage.PASSWORD_MESSAGE) {
        writeCString("md5${md5(md5(password, user.toByteArray()), salt)}")
    }
}