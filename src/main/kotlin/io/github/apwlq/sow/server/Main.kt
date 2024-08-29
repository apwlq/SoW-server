package io.github.apwlq.sow.server

import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // java -jar runner.jar --version 을 입력하면 현재 버전을 출력 후 종료
    val jarFilePath = System.getProperty("java.class.path").split(":").firstOrNull { it.endsWith(".jar") }
    val version = jarFilePath?.let {
        try {
            JarFile(it).use { jar ->
                val manifest = jar.manifest
                manifest.mainAttributes.getValue("Implementation-Version") ?: "unknown"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "unknown"
        }
    } ?: "unknown"
    if (args.isNotEmpty() && args[0] == "--version") {
        println(version)
        stop()
    }

    val multicastGroup = InetAddress.getByName("230.0.0.0")  // 멀티캐스트 주소
    val port = 4446  // 멀티캐스트 포트 번호
    val mousePort = 4447 // 마우스 위치 전송을 위한 포트 번호

    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val screens = graphicsEnvironment.screenDevices

    // 모니터 인덱스 선택 (예를 들어 첫 번째 모니터를 선택)
    var selectedMonitorIndex = args.getOrNull(0)?.toIntOrNull() ?: 0

    while (selectedMonitorIndex < 0 || selectedMonitorIndex >= screens.size) {
        println("Invalid monitor index: $selectedMonitorIndex")
        selectedMonitorIndex -= 1
        println("Change monitor index: $selectedMonitorIndex")
    }

    val selectedMonitor = screens[selectedMonitorIndex]
    val monitorBounds = selectedMonitor.defaultConfiguration.bounds

    // 스레드 풀 생성 (디스플레이 및 마우스 위치 처리를 위한)
    val executor = Executors.newFixedThreadPool(2) // 한 개의 디스플레이와 마우스 위치 처리

    // 화면 캡처를 위한 스레드
    executor.submit {
        val displayExecutor = Executors.newSingleThreadScheduledExecutor()
        displayExecutor.scheduleAtFixedRate({
            try {
                // 스크린 캡처
                val image = Capture().captureScreen(selectedMonitor)
                if (image != null) {
                    val imageData = Send().imageToByteArray(image)
                    if (imageData != null) {
                        Send().sendMulticast(imageData, multicastGroup, port)
                    }
                }
            } catch (e: Exception) {
                println("Error in capture thread for monitor $selectedMonitorIndex: ${e.message}")
            }
            println("")
        }, 0, 1000/10, TimeUnit.MILLISECONDS) // 특정 간격으로 캡처 전송

        // 프로그램 종료 시 스레드 종료
        Runtime.getRuntime().addShutdownHook(Thread {
            executor.shutdown()
            try {
                executor.awaitTermination(1, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        })
    }

    // 마우스 위치 전송을 위한 스레드
    executor.submit {
        val mouseExecutor = Executors.newSingleThreadScheduledExecutor()
        mouseExecutor.scheduleAtFixedRate({
            try {
                // 마우스 위치 가져오기
                val mousePosition = MouseInfo.getPointerInfo().location
                val mousePositionOnMonitor = Point(
                    mousePosition.x - monitorBounds.x,
                    mousePosition.y - monitorBounds.y
                )

                // 화면 크기를 100으로 설정하여 비율 계산 (소수점 포함)
                val widthRatio = (mousePositionOnMonitor.x.toDouble() / monitorBounds.width) * 100
                val heightRatio = (mousePositionOnMonitor.y.toDouble() / monitorBounds.height) * 100

                // 비율을 포함하여 전송 (소수점 20자리까지)
                val message = "${String.format("%.20f", widthRatio)},${String.format("%.20f", heightRatio)}"
                Send().sendMulticast(message.toByteArray(), multicastGroup, mousePort)
//                println("Send mouse position ratio and sequence number: ($message)")
            } catch (e: Exception) {
                println("Error sending mouse position: ${e.message}")
            }
            print(".")
        }, 0, 1000/60, TimeUnit.MILLISECONDS) // 60FPS 간격으로 마우스 위치 전송

        // 프로그램 종료 시 스레드 종료
        Runtime.getRuntime().addShutdownHook(Thread {
            executor.shutdown()
            try {
                executor.awaitTermination(1, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        })
    }
}

fun stop() {
    exitProcess(0)
}
