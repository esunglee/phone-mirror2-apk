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
import android.os.Bundle
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

    private val REQUEST_CODE_SCREEN_CAPTURE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val button = Button(this).apply { text = "진짜 화면 송출 시작" }
        setContentView(button)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        button.setOnClickListener {
            if (!isStreaming) {
                // 화면 캡처 권한 요청 팝업 띄우기
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
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            isStreaming = true
            startStreaming()
        }
    }

    private fun startStreaming() {
        thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(PC_IP, PC_PORT), 5000)
                val dos = DataOutputStream(socket.getOutputStream())

                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(metrics)
                val width = metrics.widthPixels / 2  // 대역폭을 위해 해상도 1/2로 조절
                val height = metrics.heightPixels / 2
                val density = metrics.densityDpi

                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface, null, null
                )

                while (isStreaming) {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
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

                        // 해상도 맞춤 크롭
                        val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)

                        val stream = ByteArrayOutputStream()
                        cleanBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                        val byteArray = stream.toByteArray()

                        dos.writeInt(byteArray.size)
                        dos.write(byteArray)
                        dos.flush()
                    }
                    Thread.sleep(50) // 초당 약 20프레임 전송
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isStreaming = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isStreaming = false
        virtualDisplay?.release()
        mediaProjection?.stop()
    }
}
