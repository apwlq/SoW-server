package io.github.apwlq.sow.server

import java.awt.*
import java.awt.event.ActionListener
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

var selectedMonitor: GraphicsDevice? = null
var monitorBounds: Rectangle? = null
var isCapturing = true

fun main(args: Array<String>) {
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

    val multicastGroup = InetAddress.getByName("230.0.0.0")
    val port = 4446
    val mousePort = 4447

    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val screens = graphicsEnvironment.screenDevices

    if (SystemTray.isSupported()) {
        SwingUtilities.invokeLater {
            createAndShowTray()
        }
    }

    selectedMonitor = screens[0]
    monitorBounds = selectedMonitor?.defaultConfiguration?.bounds

    val executor = Executors.newFixedThreadPool(2)

    executor.submit {
        val displayExecutor = Executors.newSingleThreadScheduledExecutor()
        displayExecutor.scheduleAtFixedRate({
            try {
                selectedMonitor?.let { monitor ->
                    val image = Capture().captureScreen(monitor)
                    if (image != null) {
                        val imageData = Send().imageToByteArray(image)
                        if (imageData != null) {
                            Send().sendMulticast(imageData, multicastGroup, port)
                        }
                    }
                }
            } catch (e: Exception) {
                println("Error in capture thread: ${e.message}")
            }
        }, 0, 1000 / 10, TimeUnit.MILLISECONDS)
    }

    executor.submit {
        val mouseExecutor = Executors.newSingleThreadScheduledExecutor()
        mouseExecutor.scheduleAtFixedRate({
            try {
                val mousePosition = MouseInfo.getPointerInfo().location
                monitorBounds?.let { bounds ->
                    val mousePositionOnMonitor = Point(
                        mousePosition.x - bounds.x,
                        mousePosition.y - bounds.y
                    )

                    val widthRatio = (mousePositionOnMonitor.x.toDouble() / bounds.width) * 100
                    val heightRatio = (mousePositionOnMonitor.y.toDouble() / bounds.height) * 100

                    val message = "${String.format("%.20f", widthRatio)},${String.format("%.20f", heightRatio)}"
                    Send().sendMulticast(message.toByteArray(), multicastGroup, mousePort)
                }
            } catch (e: Exception) {
                println("Error sending mouse position: ${e.message}")
            }
        }, 0, 1000 / 60, TimeUnit.MILLISECONDS)
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        executor.shutdown()
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    })
}

fun stop() {
    exitProcess(0)
}
fun createAndShowTray() {
    val tray = SystemTray.getSystemTray()
    val imageStream = Thread.currentThread().contextClassLoader.getResourceAsStream("logo.png")
    val image = ImageIO.read(imageStream)
    val trayIcon = TrayIcon(image, "SOW Server")
    trayIcon.isImageAutoSize = true

    val popup = PopupMenu()

    // 스크린 선택 메뉴
    val screenMenu = Menu("Select Screen")
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    screens.forEachIndexed { index, screen ->
        val screenItem = MenuItem("Screen $index")
        screenItem.addActionListener {
            selectedMonitor = screen
            monitorBounds = screen.defaultConfiguration.bounds
            trayIcon.displayMessage("Screen Selected", "Now capturing Screen $index", TrayIcon.MessageType.INFO)
        }
        screenMenu.add(screenItem)
    }
    popup.add(screenMenu)

    // 캡처 시작/중지 토글
    val captureItem = MenuItem("Stop Capture")
    captureItem.addActionListener {
        isCapturing = !isCapturing
        captureItem.label = if (isCapturing) "Stop Capture" else "Start Capture"
        val status = if (isCapturing) "started" else "stopped"
        trayIcon.displayMessage("Capture $status", "Screen capture has been $status", TrayIcon.MessageType.INFO)
    }
    popup.add(captureItem)

    // 버전 정보 표시
    val versionItem = MenuItem("Show Version")
    versionItem.addActionListener {
        val version = getVersion()
        trayIcon.displayMessage("Version Info", "SOW Server version: $version", TrayIcon.MessageType.INFO)
    }
    popup.add(versionItem)

    // 구분선
    popup.addSeparator()

    // 종료 메뉴 항목
    val exitItem = MenuItem("Exit")
    exitItem.addActionListener { stop() }
    popup.add(exitItem)

    trayIcon.popupMenu = popup

    // 트레이 아이콘 더블 클릭 이벤트
    trayIcon.addActionListener {
        val message = if (isCapturing) "SOW Server is running and capturing the screen."
        else "SOW Server is running but screen capture is paused."
        trayIcon.displayMessage("SOW Server Status", message, TrayIcon.MessageType.INFO)
    }

    try {
        tray.add(trayIcon)
    } catch (e: AWTException) {
        println("TrayIcon could not be added.")
        return
    }
}

fun getVersion(): String {
    val jarFilePath = System.getProperty("java.class.path").split(":").firstOrNull { it.endsWith(".jar") }
    return jarFilePath?.let {
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
}