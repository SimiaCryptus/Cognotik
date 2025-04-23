package com.simiacryptus.aicoder.demotest

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

object UDPClient {

    val log = LoggerFactory.getLogger(UDPClient::class.java)
    var udpSocket: DatagramSocket? = null
    var isServerRunning = false
    private val messageBuffer = ConcurrentLinkedQueue<String>()
    fun startUdpServer() {
        if (isServerRunning) return
        isServerRunning = true
        thread(isDaemon = true) {
            try {
                udpSocket = DatagramSocket(DemoTestBase.UDP_PORT)
                val buffer = ByteArray(1024)
                log.info("UDP server started on port ${DemoTestBase.UDP_PORT}")
                while (isServerRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket?.receive(packet)
                    val received = String(packet.data, 0, packet.length)
                    log.info("Received UDP message: $received")
                    messageBuffer.offer(received)
                }
            } catch (e: Exception) {
                log.error("Error in UDP server", e)
            } finally {
                isServerRunning = false
                try {
                    udpSocket?.let { socket ->
                        if (!socket.isClosed) {
                            socket.close()
                            log.info("UDP socket closed successfully")
                        }
                    }
                    udpSocket = null
                } catch (e: Exception) {
                    log.error("Error closing UDP socket", e)
                }
            }
        }
    }

    fun getReceivedMessages(): List<String> {
        return messageBuffer.toList()
    }

    fun clearMessageBuffer() {
        messageBuffer.clear()
    }

}