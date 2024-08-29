package io.github.apwlq.sow.server

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import javax.imageio.ImageIO

class Send {

    fun imageToByteArray(image: BufferedImage): ByteArray? {
        return try {
            ByteArrayOutputStream().use { baos ->
                ImageIO.write(image, "JPEG", baos)
                baos.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun sendMulticast(data: ByteArray, group: InetAddress, port: Int) {
        val maxPacketSize = 65000
        val numberOfPackets = Math.ceil(data.size / maxPacketSize.toDouble()).toInt()

        try {
            // MulticastSocket을 사용하여 멀티캐스트 패킷 전송
            MulticastSocket().use { socket ->
                // TTL 값 설정
                socket.timeToLive = 32

                for (i in 0 until numberOfPackets) {
                    val start = i * maxPacketSize
                    val end = minOf(start + maxPacketSize, data.size)
                    val packetData = data.sliceArray(start until end)

                    val packet = DatagramPacket(packetData, packetData.size, group, port)
                    socket.send(packet)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
