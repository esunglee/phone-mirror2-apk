package com.example.phonemirror

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val PC_IP = "127.0.0.1"
    private val PC_PORT = 9999
    private var isStreaming = false

    private lateinit var projectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var dos: DataOutputStream? = null

    private val REQUEST_CODE_SCREEN_CAPTURE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val button = Button(this).apply { text = "진짜 화면 송출 시작" }
        setContentView(button)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        button.setOnClickListener {
            if (!isStreaming) {
                startActivityForResult(
                    projectionManager.createScreenCaptureIntent(),
                    REQUEST_CODE_SCREEN_CAPTURE
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK && data != null) {
            
            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            isStreaming = true
            connectAndStart()
        }
    }

    private fun connectAndStart() {
        thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(PC_IP, PC_PORT), 5000)
                dos = DataOutputStream(socket.getOutputStream())

                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(metrics)
                
                // 가로/세로 해상도를 짝수로 보정
                var width = metrics.widthPixels / 2
                var height = metrics.heightPixels / 2
                if (width % 2 != 0) width -= 1
                if (height % 2 != 0) height -= 1
                
                val density = metrics.densityDpi

                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                
                imageReader?.setOnImageAvailableListener({ reader ->
                    if (!isStreaming) return@setOnImageAvailableListener
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        val stream = ByteArrayOutputStream()
                        cleanBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                        val byteArray = stream.toByteArray()

                        thread {
                            try {
                                dos?.writeInt(byteArray.size)
                                dos?.write(byteArray)
                                dos?.flush()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        image.close()
                    }
                }, Handler(Looper.getMainLooper()))

                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface, null, null
                )

            } catch (e: Exception) {
                e.printStackTrace()
                isStreaming = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isStreaming = false
        stopService(Intent(this, ScreenCaptureService::class.java))
        virtualDisplay?.release()
        mediaProjection?.stop()
    }
}
