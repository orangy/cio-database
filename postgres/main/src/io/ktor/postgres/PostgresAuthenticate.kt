package io.ktor.postgres

import kotlinx.coroutines.io.*
import java.security.*

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