package com.craneremote.data.remote.protocol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SiemensS7Protocol @Inject constructor() {

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var connected = false

    companion object {
        private const val TIMEOUT_MS = 5_000
        private const val DEFAULT_PORT = 102
        private const val AREA_INPUTS: Byte  = 0x81.toByte()
        private const val AREA_OUTPUTS: Byte = 0x82.toByte()
        private const val AREA_FLAGS: Byte   = 0x83.toByte()
        private const val AREA_DB: Byte      = 0x84.toByte()
        private const val TRANSPORT_SIZE_BIT:  Byte = 0x01
        private const val TRANSPORT_SIZE_WORD: Byte = 0x04

        private val COTP_CR = byteArrayOf(
            0x03, 0x00, 0x00, 0x16,
            0x11, 0xE0.toByte(), 0x00, 0x00, 0x00, 0x01, 0x00,
            0xC0.toByte(), 0x01, 0x0A,
            0xC1.toByte(), 0x02, 0x01, 0x02,
            0xC2.toByte(), 0x02, 0x01, 0x00
        )

        private val S7_COMM_SETUP = byteArrayOf(
            0x03, 0x00, 0x00, 0x19,
            0x02, 0xF0.toByte(), 0x80.toByte(),
            0x32, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x08, 0x00, 0x00,
            0xF0.toByte(), 0x00, 0x00, 0x01, 0x00, 0x01, 0x01, 0xE0.toByte()
        )
    }

    suspend fun connect(
        ipAddress: String,
        port: Int = DEFAULT_PORT,
        rack: Int = 0,
        slot: Int = 1
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = Socket()
            s.connect(InetSocketAddress(ipAddress, port), TIMEOUT_MS)
            s.soTimeout = TIMEOUT_MS
            s.keepAlive = true
            socket = s
            input  = s.getInputStream()
            output = s.getOutputStream()
            sendRaw(COTP_CR);  readFull(ByteArray(22))
            sendRaw(S7_COMM_SETUP); readFull(ByteArray(27))
            connected = true
        }.onFailure { connected = false; closeQuietly() }
    }

    suspend fun writeBool(address: String, value: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                checkConnected()
                val addr = parseAddress(address)
                sendRaw(buildWritePacket(addr, TRANSPORT_SIZE_BIT, 1, byteArrayOf(if (value) 0x01 else 0x00)))
                val resp = ByteArray(22); readFull(resp)
                if (resp.size < 22 || resp[21] != 0xFF.toByte())
                    throw Exception("PLC erro escrita")
            }
        }

    suspend fun writeInt(address: String, value: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                checkConnected()
                val addr = parseAddress(address)
                val hi = ((value shr 8) and 0xFF).toByte()
                val lo = (value and 0xFF).toByte()
                sendRaw(buildWritePacket(addr, TRANSPORT_SIZE_WORD, 2, byteArrayOf(hi, lo)))
                val resp = ByteArray(22); readFull(resp)
                if (resp.size < 22 || resp[21] != 0xFF.toByte())
                    throw Exception("PLC erro escrita INT")
            }
        }

    suspend fun readInt(address: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                checkConnected()
                val addr = parseAddress(address)
                sendRaw(buildReadPacket(addr, TRANSPORT_SIZE_WORD, 2))
                val resp = ByteArray(31); readFull(resp)
                // dados começam no byte 25 (S7 read response)
                val hi = resp[25].toInt() and 0xFF
                val lo = resp[26].toInt() and 0xFF
                (hi shl 8) or lo
            }
        }

    fun disconnect() { connected = false; closeQuietly() }
    fun isConnected() = connected

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sendRaw(data: ByteArray) {
        output?.write(data) ?: throw NotConnectedException()
        output?.flush()
    }

    private fun readFull(buf: ByteArray) {
        var read = 0
        val inp = input ?: throw NotConnectedException()
        while (read < buf.size) {
            val n = inp.read(buf, read, buf.size - read)
            if (n == -1) throw Exception("PLC fechou conexão")
            read += n
        }
    }

    private fun checkConnected() {
        if (!connected) throw NotConnectedException()
    }

    private fun closeQuietly() {
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }

    private fun parseAddress(address: String): S7Addr {
        val upper = address.uppercase().trim()
        return when {
            upper.startsWith("DB") -> {
                val parts = upper.split(".")
                val dbNum = parts[0].removePrefix("DB").toIntOrNull() ?: 1
                val rest  = parts.drop(1).joinToString(".")
                when {
                    rest.startsWith("DBX") -> { val s = rest.removePrefix("DBX").split("."); S7Addr(AREA_DB, dbNum, s[0].toIntOrNull() ?: 0, s.getOrNull(1)?.toIntOrNull() ?: 0) }
                    rest.startsWith("DBW") -> S7Addr(AREA_DB, dbNum, rest.removePrefix("DBW").toIntOrNull() ?: 0, 0)
                    rest.startsWith("DBD") -> S7Addr(AREA_DB, dbNum, rest.removePrefix("DBD").toIntOrNull() ?: 0, 0)
                    else -> S7Addr(AREA_DB, dbNum, 0, 0)
                }
            }
            upper.startsWith("MW") -> S7Addr(AREA_FLAGS, 0, upper.removePrefix("MW").toIntOrNull() ?: 0, 0)
            upper.startsWith("IW") -> S7Addr(AREA_INPUTS, 0, upper.removePrefix("IW").toIntOrNull() ?: 0, 0)
            upper.startsWith("QW") -> S7Addr(AREA_OUTPUTS, 0, upper.removePrefix("QW").toIntOrNull() ?: 0, 0)
            upper.startsWith("M")  -> { val p = upper.removePrefix("M").split("."); S7Addr(AREA_FLAGS, 0, p[0].toIntOrNull() ?: 0, p.getOrNull(1)?.toIntOrNull() ?: 0) }
            upper.startsWith("I")  -> { val p = upper.removePrefix("I").split("."); S7Addr(AREA_INPUTS, 0, p[0].toIntOrNull() ?: 0, p.getOrNull(1)?.toIntOrNull() ?: 0) }
            upper.startsWith("Q")  -> { val p = upper.removePrefix("Q").split("."); S7Addr(AREA_OUTPUTS, 0, p[0].toIntOrNull() ?: 0, p.getOrNull(1)?.toIntOrNull() ?: 0) }
            else -> S7Addr(AREA_FLAGS, 0, 0, 0)
        }
    }

    private fun buildWritePacket(addr: S7Addr, transportSize: Byte, dataLen: Int, data: ByteArray): ByteArray {
        val bitAddr  = addr.byteAddr * 8 + addr.bitAddr
        val paramLen = 14; val dataLenField = 4 + dataLen; val s7Len = 10 + paramLen + dataLenField
        val total = 4 + 3 + s7Len
        return ByteArray(total).apply {
            this[0] = 0x03; this[1] = 0x00; this[2] = (total shr 8).toByte(); this[3] = total.toByte()
            this[4] = 0x02; this[5] = 0xF0.toByte(); this[6] = 0x80.toByte()
            this[7] = 0x32; this[8] = 0x01; this[9] = 0x00; this[10] = 0x00
            this[11] = 0x00; this[12] = 0x01
            this[13] = (paramLen shr 8).toByte(); this[14] = paramLen.toByte()
            this[15] = (dataLenField shr 8).toByte(); this[16] = dataLenField.toByte()
            this[17] = 0x05; this[18] = 0x01; this[19] = 0x12; this[20] = 0x0A; this[21] = 0x10
            this[22] = transportSize
            this[23] = 0x00; this[24] = dataLen.toByte()
            this[25] = (addr.dbNum shr 8).toByte(); this[26] = addr.dbNum.toByte()
            this[27] = addr.area
            this[28] = (bitAddr shr 16).toByte(); this[29] = (bitAddr shr 8).toByte(); this[30] = bitAddr.toByte()
            this[31] = 0x00; this[32] = transportSize
            this[33] = ((dataLen * 8) shr 8).toByte(); this[34] = (dataLen * 8).toByte()
            data.copyInto(this, 35)
        }
    }

    private fun buildReadPacket(addr: S7Addr, transportSize: Byte, dataLen: Int): ByteArray {
        val bitAddr = addr.byteAddr * 8 + addr.bitAddr
        val paramLen = 14; val total = 4 + 3 + 10 + paramLen
        return ByteArray(total).apply {
            this[0] = 0x03; this[1] = 0x00; this[2] = (total shr 8).toByte(); this[3] = total.toByte()
            this[4] = 0x02; this[5] = 0xF0.toByte(); this[6] = 0x80.toByte()
            this[7] = 0x32; this[8] = 0x01; this[9] = 0x00; this[10] = 0x00
            this[11] = 0x00; this[12] = 0x01
            this[13] = (paramLen shr 8).toByte(); this[14] = paramLen.toByte()
            this[15] = 0x00; this[16] = 0x00
            this[17] = 0x04; this[18] = 0x01; this[19] = 0x12; this[20] = 0x0A; this[21] = 0x10
            this[22] = transportSize
            this[23] = 0x00; this[24] = dataLen.toByte()
            this[25] = (addr.dbNum shr 8).toByte(); this[26] = addr.dbNum.toByte()
            this[27] = addr.area
            this[28] = (bitAddr shr 16).toByte(); this[29] = (bitAddr shr 8).toByte(); this[30] = bitAddr.toByte()
        }
    }

    private data class S7Addr(val area: Byte, val dbNum: Int, val byteAddr: Int, val bitAddr: Int)
    class NotConnectedException : Exception("Não conectado ao PLC")
}
