package com.sharedash.app.model

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32

object Protocol {
    val MAGIC_BYTES = byteArrayOf(0x53, 0x44) // 'S', 'D'
    const val VERSION: Byte = 1
    const val HEADER_LEN = 34

    enum class FrameType(val typeByte: Byte) {
        HELLO(0x01),
        HELLO_RESP(0x02),
        CAPABILITIES(0x03),
        TRANSFER_OFFER(0x06),
        TRANSFER_ACCEPT(0x07),
        CHUNK_REQ(0x0A),
        CHUNK_DATA(0x0B),
        CHUNK_ACK(0x0C),
        CHUNK_REJECT(0x0D),
        BENCHMARK_PROBE(0x0E),
        BENCHMARK_RESP(0x0F),
        PING(0x16),
        PONG(0x17);

        companion object {
            fun fromByte(b: Byte): FrameType? = entries.find { it.typeByte == b }
        }
    }

    data class FrameHeader(
        val frameType: FrameType,
        val flags: Short,
        val transferId: UUID,
        val chunkId: Int,
        val payloadLen: Int,
        val crc32: Long
    )

    data class Frame(
        val header: FrameHeader,
        val payload: ByteArray
    )

    fun encodeFrame(frame: Frame): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_LEN + frame.payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_BYTES)
        buf.put(VERSION)
        buf.put(frame.header.frameType.typeByte)
        buf.putShort(frame.header.flags)

        val uuidBuf = ByteBuffer.allocate(16)
        uuidBuf.putLong(frame.header.transferId.mostSignificantBits)
        uuidBuf.putLong(frame.header.transferId.leastSignificantBits)
        buf.put(uuidBuf.array())

        buf.putInt(frame.header.chunkId)
        buf.putInt(frame.header.payloadLen)

        // Compute CRC32
        val crc = CRC32()
        crc.update(buf.array(), 0, 30)
        val computedCrc = crc.value
        buf.putInt(computedCrc.toInt())

        buf.put(frame.payload)
        return buf.array()
    }

    fun sha256Hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
