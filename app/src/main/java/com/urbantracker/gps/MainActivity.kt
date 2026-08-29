package com.urbantracker.gps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var ipAddressText: TextView
    private lateinit var cameraIdInput: EditText
    private lateinit var serverInput: EditText
    private lateinit var dashboardUrlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var openDashboardButton: Button

    private val prefs by lazy {
        getSharedPreferences("urbantracker", MODE_PRIVATE)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)   // server may take a while to process the image
        .writeTimeout(30, TimeUnit.SECONDS)  // uploading a JPEG can be slow on cellular
        .build()

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var imageCapture: ImageCapture? = null
    private var isRunning = false
    private var isCapturing = false // guards against overlapping captures

    private val locationCallback =
        object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {

                val location = result.lastLocation ?: return

                val latitude = location.latitude
                val longitude = location.longitude
                val accuracy = location.accuracy

                val timestamp =
                    SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSS",
                        Locale.getDefault()
                    ).format(Date())

                val cameraId = cameraIdInput.text.toString().trim()

                updateStatus(
                    """
                    Camera: $cameraId

                    🟢 GPS FIXED

                    Latitude: $latitude
                    Longitude: $longitude
                    Accuracy: ${"%.2f".format(accuracy)} m
                    Timestamp: $timestamp

                    Capturing photo...
                    """.trimIndent()
                )

                captureAndSend(cameraId, latitude, longitude, accuracy, timestamp)
            }
        }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val locationGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            val cameraGranted =
                permissions[Manifest.permission.CAMERA] == true

            if (locationGranted && cameraGranted) {
                startCamera()
                startLocationUpdates()
            } else {
                updateStatus("🔴 Camera and/or location permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createUI()
        loadSettings()
        checkPermissions()
    }

    private fun createUI() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        val title = TextView(this).apply {
            text = "URBANTRACKER GPS"
            textSize = 26f
        }
        layout.addView(title)

        ipAddressText = TextView(this).apply {
            text = "Device IP: ${getLocalIpAddress()}"
            textSize = 14f
            setPadding(0, 8, 0, 16)
        }
        layout.addView(ipAddressText)

        previewView = PreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                600
            )
        }
        layout.addView(previewView)

        val cameraLabel = TextView(this).apply {
            text = "Camera ID"
            textSize = 16f
        }
        layout.addView(cameraLabel)

        cameraIdInput = EditText(this).apply {
            hint = "CAM_01"
            textSize = 18f
        }
        layout.addView(cameraIdInput)

        val serverLabel = TextView(this).apply {
            text = "Data Server (receives GPS + photo)"
            textSize = 16f
        }
        layout.addView(serverLabel)

        serverInput = EditText(this).apply {
            hint = "http://192.168.1.100:5000"
            textSize = 18f
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(serverInput)

        val dashboardLabel = TextView(this).apply {
            text = "Dashboard / Control URL (optional)"
            textSize = 16f
        }
        layout.addView(dashboardLabel)

        dashboardUrlInput = EditText(this).apply {
            hint = "https://your-dashboard.example.com"
            textSize = 18f
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(dashboardUrlInput)

        openDashboardButton = Button(this).apply {
            text = "OPEN DASHBOARD"
            setOnClickListener { openDashboard() }
        }
        layout.addView(openDashboardButton)

        startButton = Button(this).apply {
            text = "START GPS"
            setOnClickListener {
                saveSettings()
                checkPermissions()
            }
        }
        layout.addView(startButton)

        stopButton = Button(this).apply {
            text = "STOP GPS"
            isEnabled = false
            setOnClickListener { stopLocationUpdates() }
        }
        layout.addView(stopButton)

        statusText = TextView(this).apply {
            text = "Ready"
            textSize = 18f
            setPadding(0, 40, 0, 0)
        }
        layout.addView(statusText)

        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
    }

    private fun loadSettings() {
        cameraIdInput.setText(prefs.getString("camera_id", "CAM_01"))
        serverInput.setText(prefs.getString("server_url", ""))
        dashboardUrlInput.setText(prefs.getString("dashboard_url", ""))
    }

    private fun saveSettings() {
        val cameraId = cameraIdInput.text.toString().trim()
        val server = serverInput.text.toString().trim()
        val dashboard = dashboardUrlInput.text.toString().trim()

        if (cameraId.isEmpty()) {
            Toast.makeText(this, "Enter Camera ID", Toast.LENGTH_SHORT).show()
            return
        }

        if (server.isEmpty()) {
            Toast.makeText(this, "Enter server address", Toast.LENGTH_SHORT).show()
            return
        }

        if (!server.startsWith("https://") && !server.startsWith("http://")) {
            Toast.makeText(
                this,
                "Server URL must start with http:// or https://",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (server.startsWith("http://")) {
            Toast.makeText(
                this,
                "⚠️ Using plain HTTP — data will be unencrypted",
                Toast.LENGTH_LONG
            ).show()
        }

        prefs.edit()
            .putString("camera_id", cameraId)
            .putString("server_url", server)
            .putString("dashboard_url", dashboard)
            .apply()
    }

    private fun openDashboard() {
        val url = dashboardUrlInput.text.toString().trim()

        if (url.isEmpty()) {
            Toast.makeText(this, "Enter a dashboard URL first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            Toast.makeText(this, "URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putString("dashboard_url", url).apply()

        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open that URL", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {

        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val camera = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if ((fine || coarse) && camera) {
            startCamera()
            startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA
                )
            )
        }
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, capture
                )
                imageCapture = capture
            } catch (e: Exception) {
                Log.e("UrbanTracker", "Camera bind failed", e)
                updateStatus("🔴 Camera failed to start")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startLocationUpdates() {

        if (isRunning) return

        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMinUpdateIntervalMillis(3000L)
                .build()

        if (
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(
            request, locationCallback, mainLooper
        )

        isRunning = true
        startButton.isEnabled = false
        stopButton.isEnabled = true

        updateStatus("🟢 GPS tracking started")
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)

        isRunning = false
        startButton.isEnabled = true
        stopButton.isEnabled = false

        updateStatus("⏹ GPS tracking stopped")
    }

    private fun captureAndSend(
        cameraId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        timestamp: String
    ) {

        val capture = imageCapture ?: return

        // Skip this fix if a capture/upload from the previous fix is still in flight.
        if (isCapturing) return
        isCapturing = true

        val photoFile = File(cacheDir, "shot_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    uploadData(cameraId, latitude, longitude, accuracy, timestamp, photoFile)
                }

                override fun onError(exc: ImageCaptureException) {
                    isCapturing = false
                    updateStatusAppend("🔴 Photo capture failed: ${exc.message}")
                }
            }
        )
    }

    private fun uploadData(
        cameraId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        timestamp: String,
        photoFile: File
    ) {

        val server = serverInput.text.toString().trim()

        val url =
            if (server.endsWith("/gps")) server
            else "${server.trimEnd('/')}/gps"

        Thread {

            try {
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("camera_id", cameraId)
                    .addFormDataPart("latitude", latitude.toString())
                    .addFormDataPart("longitude", longitude.toString())
                    .addFormDataPart("accuracy", accuracy.toString())
                    .addFormDataPart("timestamp", timestamp)
                    .addFormDataPart(
                        "image",
                        photoFile.name,
                        photoFile.asRequestBody("image/jpeg".toMediaType())
                    )
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    runOnUiThread {
                        if (response.isSuccessful) {
                            updateStatusAppend("🟢 Server: Connected")
                        } else {
                            updateStatusAppend("🔴 Server error: ${response.code}")
                        }
                    }
                }

            } catch (e: java.net.SocketTimeoutException) {
                Log.e("UrbanTracker", "Upload timed out", e)
                runOnUiThread {
                    updateStatusAppend("🟠 Server: Timed out waiting for response (data may still have been received)")
                }
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                Log.e("UrbanTracker", "TLS handshake failed", e)
                runOnUiThread {
                    updateStatusAppend("🔴 Server: TLS/certificate error — ${e.message}")
                }
            } catch (e: java.io.IOException) {
                Log.e("UrbanTracker", "Upload failed", e)
                runOnUiThread {
                    updateStatusAppend("🔴 Server: ${e.javaClass.simpleName} — ${e.message}")
                }
            } finally {
                photoFile.delete()
                isCapturing = false
            }
        }.start()
    }

    /**
     * Returns the device's current local (LAN) IPv4 address, e.g. on Wi-Fi.
     * Falls back to "Unknown" if no non-loopback IPv4 interface is up
     * (e.g. no Wi-Fi/hotspot connection, or mobile data with carrier-grade NAT
     * where the interface still resolves but the address isn't publicly routable —
     * this always shows the LOCAL address, not any public/WAN IP).
     */
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addresses = Collections.list(intf.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UrbanTracker", "Failed to determine local IP", e)
        }
        return "Unknown"
    }

    private fun refreshIpAddress() {
        ipAddressText.text = "Device IP: ${getLocalIpAddress()}"
    }

    override fun onResume() {
        super.onResume()
        if (::ipAddressText.isInitialized) {
            refreshIpAddress()
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread { statusText.text = message }
    }

    private fun updateStatusAppend(message: String) {
        runOnUiThread { statusText.append("\n$message") }
    }

    override fun onDestroy() {
        stopLocationUpdates()
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }
}