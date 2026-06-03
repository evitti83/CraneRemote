package com.craneremote.data.remote.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AllenBradleyProtocol @Inject constructor() {

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var connected = false
    private var sessionHandle: Int = 0

    companion object {
        private const val TIMEOUT_MS = 5_000
        private const val DEFAULT_PORT = 44818
        private const val CIP_BOOL:  Short = 0x00C1.toShort()
        private const val CIP_INT:   Short = 0x00C3.toShort()
    }

    suspend fun connect(ipAddress: String, port: Int = DEFAULT_PORT): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val s = Socket()
                s.connect(InetSocketAddress(ipAddress, port), TIMEOUT_MS)
                s.soTimeout = TIMEOUT_MS; s.keepAlive = true
                socket = s; input = s.getInputStream(); output = s.getOutputStream()
                registerSession()
                connected = true
            }.onFailure { connected = false; closeQuietly() }
        }

    private fun registerSession() {
        val req = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0065); putShort(4); putInt(0); putInt(0); putLong(0); putInt(0)
            putShort(1); putShort(0)
        }.array()
        output?.write(req); output?.flush()
        val resp = ByteArray(28); readFull(resp)
        sessionHandle = ByteBuffer.wrap(resp, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    suspend fun writeBool(tagName: String, value: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                checkConnected()
                sendCipWrite(tagName, CIP_BOOL, 1, byteArrayOf(if (value) 0x01 else 0x00, 0x00))
            }
        }

    suspend fun writeInt(tagName: String, value: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                checkConnected()
                val data = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
                sendCipWrite(tagName, CIP_INT, 1, data)
            }
        }

    suspend fun readInt(tagName: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                checkConnected()
                // CIP Read Tag Service 0x4C
                val nameBytes = tagName.toByteArray(Charsets.US_ASCII)
                val paddedName = if (nameBytes.size % 2 != 0) nameBytes + byteArrayOf(0x00) else nameBytes
                val pathSegment = byteArrayOf(0x91.toByte(), nameBytes.size.toByte()) + paddedName
                val buf = ByteBuffer.allocate(2 + pathSegment.size + 2).order(ByteOrder.LITTLE_ENDIAN)
                buf.put(0x4C.toByte()); buf.put((pathSegment.size / 2).toByte())
                buf.put(pathSegment); buf.putShort(1)
                val enip = buildSendRRData(buf.array())
                output?.write(enip); output?.flush()
                val header = ByteArray(24); readFull(header)
                val len = ByteBuffer.wrap(header, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                val body = ByteArray(len); readFull(body)
                // valor INT nos últimos 2 bytes da resposta
                if (body.size >= 2) ByteBuffer.wrap(body, body.size - 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() else 0
            }
        }

    fun disconnect() { connected = false; closeQuietly() }
    fun isConnected() = connected

    private fun sendCipWrite(tagName: String, dataType: Short, elementCount: Int, data: ByteArray) {
        val enip = buildSendRRData(buildCipWriteTag(tagName, dataType, elementCount, data))
        output?.write(enip); output?.flush()
        val header = ByteArray(24); readFull(header)
        val len = ByteBuffer.wrap(header, 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        if (len > 0) { val body = ByteArray(len); readFull(body) }
    }

    private fun buildCipWriteTag(tagName: String, dataType: Short, elementCount: Int, data: ByteArray): ByteArray {
        val nameBytes = tagName.toByteArray(Charsets.US_ASCII)
        val paddedName = if (nameBytes.size % 2 != 0) nameBytes + byteArrayOf(0x00) else nameBytes
        val pathSegment = byteArrayOf(0x91.toByte(), nameBytes.size.toByte()) + paddedName
        val buf = ByteBuffer.allocate(2 + pathSegment.size + 2 + 2 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x4D.toByte()); buf.put((pathSegment.size / 2).toByte())
        buf.put(pathSegment); buf.putShort(dataType); buf.putShort(elementCount.toShort()); buf.put(data)
        return buf.array()
    }

    private fun buildSendRRData(cipData: ByteArray): ByteArray {
        val itemPayload = ByteBuffer.allocate(16 + cipData.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0); putShort(10); putShort(2)
            putShort(0x0000); putShort(0)
            putShort(0x00B2.toShort()); putShort(cipData.size.toShort()); put(cipData)
        }.array()
        val total = 24 + itemPayload.size
        return ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x006F); putShort(itemPayload.size.toShort())
            putInt(sessionHandle); putInt(0); putLong(0); putInt(0)
            put(itemPayload)
        }.array()
    }

    private fun readFull(buf: ByteArray) {
        var read = 0
        val inp = input ?: throw NotConnectedException()
        while (read < buf.size) { val n = inp.read(buf, read, buf.size - read); if (n == -1) throw Exception("Conexão encerrada"); read += n }
    }

    private fun checkConnected() { if (!connected) throw NotConnectedException() }
    private fun closeQuietly() { runCatching { socket?.close() }; socket = null; input = null; output = null }

    class NotConnectedException : Exception("Não conectado ao controlador AB")
}
