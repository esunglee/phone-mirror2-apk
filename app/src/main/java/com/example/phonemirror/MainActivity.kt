package com.example.phonemirror

import android.graphics.Bitmap
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val button = Button(this).apply { text = "USB 화면 송출 시작" }
        setContentView(button)

        button.setOnClickListener {
            startStreaming()
        }
    }

    private fun startStreaming() {
        thread {
            try {
                val socket = Socket(PC_IP, PC_PORT)
                val dos = DataOutputStream(socket.getOutputStream())

                val metrics = resources.displayMetrics
                val width = metrics.widthPixels / 2
                val height = metrics.heightPixels / 2

                while (true) {
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                    val byteArray = stream.toByteArray()

                    dos.writeInt(byteArray.size)
                    dos.write(byteArray)
                    dos.flush()

                    Thread.sleep(33)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
