package com.example.phonemirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val button = Button(this).apply { text = "USB 화면 송출 시작" }
        setContentView(button)

        button.setOnClickListener {
            if (!isStreaming) {
                isStreaming = true
                button.text = "연결 시도 중..."
                startStreaming(button)
            }
        }
    }

    private fun startStreaming(button: Button) {
        thread {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(PC_IP, PC_PORT), 5000) // 5초 타임아웃
                val dos = DataOutputStream(socket.getOutputStream())

                runOnUiThread { button.text = "송출 중!" }

                val width = 360
                val height = 640
                var count = 0

                while (isStreaming) {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.DKGRAY)
                    
                    val paint = Paint().apply {
                        color = Color.GREEN
                        textSize = 40f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("Phone Mirroring... ${count++}", (width / 2).toFloat(), (height / 2).toFloat(), paint)

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                    val byteArray = stream.toByteArray()

                    dos.writeInt(byteArray.size)
                    dos.write(byteArray)
                    dos.flush()

                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isStreaming = false
                runOnUiThread { 
                    button.text = "연결 실패: ${e.message}" 
                }
            }
        }
    }
}
