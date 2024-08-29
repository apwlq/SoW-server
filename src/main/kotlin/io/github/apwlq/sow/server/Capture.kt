package io.github.apwlq.sow.server

import java.awt.*
import java.awt.image.BufferedImage

class Capture {
    private val robot: Robot = Robot()

    fun captureScreen(device: GraphicsDevice): BufferedImage? {
        return try {
            val bounds = device.defaultConfiguration.bounds
            robot.createScreenCapture(bounds)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
