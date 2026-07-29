package com.example.phonemirror

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val REQUEST_CODE_SCREEN_CAPTURE = 1001
    private val REQUEST_CODE_NOTIFICATION = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val button = Button(this).apply { text = "진짜 화면 송출 시작" }
        setContentView(button)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIFICATION)
            }
        }

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

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                    isStreaming = 
