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
                button.text = "송출 중..."
                startStreaming()
            }
        }
    }

    private fun startStreaming() {
        thread {
            try {
                val socket = Socket(PC_IP, PC_PORT)
                val dos = DataOutputStream(socket.getOutputStream())

                val width = 360
                val height = 640
                var count = 0

                while (isStreaming) {
                    // PC 화면 송출 테스트를 위해 동적으로 바뀌는 테스트 비트맵 생성
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

                    // 데이터 크기 전송 + 이미지 전송
                    dos.writeInt(byteArray.size)
                    dos.write(byteArray)
                    dos.flush()

                    Thread.sleep(100) // 초당 10프레임 송출
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
