package com.example.engine

object TransferProtocol {
    const val PORT = 8988
    const val WEB_PORT = 8080

    val MAGIC_HEADER = byteArrayOf(0x58.toByte(), 0x53.toByte(), 0x48.toByte(), 0x52.toByte()) // "XSHR"

    const val TYPE_HANDSHAKE: Byte = 0x01
    const val TYPE_FILE_HEADER: Byte = 0x02
    const val TYPE_FILE_CHUNK: Byte = 0x03
    const val TYPE_FILE_ACK: Byte = 0x04
    const val TYPE_BATCH_COMPLETE: Byte = 0x05
    const val TYPE_CANCEL: Byte = 0x06

    const val BUFFER_SIZE = 64 * 1024 // 64 KB chunk size for maximum wireless socket throughput
}
