package com.example.luckycharm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var selectedCharm: CharmType = CharmType.CLOVER
    private var selectedLetter: String? = null
    private var selectedColor: Int? = null
    private var selectedStringColor: Int? = null
    private var gyroSensitivity: Float = 0.05f
    private var positionRatio: Float = 0.5f

    private lateinit var statusText: TextView
    private lateinit var hangButton: Button
    private lateinit var sensitivityValueText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        hangButton = findViewById(R.id.hangButton)
        sensitivityValueText = findViewById(R.id.sensitivityValueText)
        
        val grantButton = findViewById<Button>(R.id.grantPermissionButton)
        val hideButton = findViewById<Button>(R.id.hideButton)
        val checkUpdatesButton = findViewById<Button>(R.id.btnCheckUpdates)
        val charmGrid = findViewById<GridLayout>(R.id.charmGrid)

        buildCharmGrid(charmGrid)
        setupSensitivityBar()
        setupColorPickers()
        setupPositionButtons()

        grantButton.setOnClickListener { requestOverlayPermission() }

        hangButton.setOnClickListener {
            maybeRequestNotificationPermission()
            updateService()
            Toast.makeText(this, "${selectedCharm.displayName} is hanging up top!", Toast.LENGTH_SHORT).show()
        }

        hideButton.setOnClickListener {
            val intent = Intent(this, CharmOverlayService::class.java).apply {
                action = CharmOverlayService.ACTION_HIDE
            }
            startService(intent)
        }
        
        checkUpdatesButton.setOnClickListener {
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        val btn = findViewById<Button>(R.id.btnCheckUpdates)
        btn.isEnabled = false
        btn.text = "Checking..."
        
        thread {
            try {
                // Checking the latest commit on master branch
                val url = URL("https://api.github.com/repos/Raksab769/LuckyCharm/commits/master")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    // If we successfully fetch the commit info, we pretend there is an update to show the flow
                    Handler(Looper.getMainLooper()).post {
                        btn.isEnabled = true
                        btn.text = "Check for Updates"
                        showUpdateDialog()
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        btn.isEnabled = true
                        btn.text = "Check for Updates"
                        Toast.makeText(this@MainActivity, "No new updates found.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    btn.isEnabled = true
                    btn.text = "Check for Updates"
                    Toast.makeText(this@MainActivity, "Failed to check for updates.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private var downloadId: Long = -1L

    private fun showUpdateDialog() {
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("There is a new update available for Lucky Charm! Would you like to download and install it now?")
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstallUpdate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadAndInstallUpdate() {
        Toast.makeText(this, "Downloading update...", Toast.LENGTH_SHORT).show()
        val apkUrl = "https://raw.githubusercontent.com/Raksab769/LuckyCharm/master/app/release/app-release.apk"
        
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Lucky Charm Update")
            .setDescription("Downloading latest version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LuckyCharm-update.apk")

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        
        // Remove previous downloaded file if exists to prevent rename conflicts
        val file =
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LuckyCharm-update.apk")
        if (file.exists()) file.delete()

        downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try {
                            startActivity(installIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to start installer.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Download failed.", Toast.LENGTH_SHORT).show()
                    }
                    context.unregisterReceiver(this)
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    private fun updateService() {
        val hasPermission = Settings.canDrawOverlays(this)
        if (hasPermission) {
            val updateIntent = Intent(this@MainActivity, CharmOverlayService::class.java).apply {
                putExtra(CharmOverlayService.EXTRA_CHARM, selectedCharm.name)
                putExtra(CharmOverlayService.EXTRA_SENSITIVITY, gyroSensitivity)
                putExtra(CharmOverlayService.EXTRA_POSITION_RATIO, positionRatio)
                putExtra(CharmOverlayService.EXTRA_LETTER, selectedLetter)
                selectedColor?.let { putExtra(CharmOverlayService.EXTRA_COLOR, it) }
                selectedStringColor?.let { putExtra(CharmOverlayService.EXTRA_STRING_COLOR, it) }
            }
            ContextCompat.startForegroundService(this@MainActivity, updateIntent)
        }
    }

    private fun setupSensitivityBar() {
        val seekBar = findViewById<SeekBar>(R.id.gyroSensitivityBar)
        
        // Ensure UI matches the default 5% state initially
        seekBar.progress = 5
        sensitivityValueText.text = "5%"
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                gyroSensitivity = progress / 100f
                sensitivityValueText.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                updateService()
            }
        })
    }

    private fun setupColorPickers() {
        updateColorPreviews()

        val charmPreview = findViewById<View>(R.id.charmColorPreview)
        val stringPreview = findViewById<View>(R.id.stringColorPreview)

        val openCharmPicker = {
            showColorPickerDialog(
                "Choose Charm Color",
                selectedColor ?: selectedCharm.baseColor
            ) { newColor ->
                selectedColor = newColor
                updateColorPreviews()
                updateService()
            }
        }

        charmPreview.setOnClickListener { openCharmPicker() }
        findViewById<Button>(R.id.btnPickCharmColor).setOnClickListener { openCharmPicker() }
        findViewById<Button>(R.id.btnResetCharmColor).setOnClickListener {
            selectedColor = null
            updateColorPreviews()
            updateService()
            Toast.makeText(this, "Charm color reset to default", Toast.LENGTH_SHORT).show()
        }

        val openStringPicker = {
            showColorPickerDialog(
                "Choose String Color",
                selectedStringColor ?: 0xFFAAAAAA.toInt()
            ) { newColor ->
                selectedStringColor = newColor
                updateColorPreviews()
                updateService()
            }
        }

        stringPreview.setOnClickListener { openStringPicker() }
        findViewById<Button>(R.id.btnPickStringColor).setOnClickListener { openStringPicker() }
        findViewById<Button>(R.id.btnResetStringColor).setOnClickListener {
            selectedStringColor = null
            updateColorPreviews()
            updateService()
            Toast.makeText(this, "String color reset to default", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateColorPreviews() {
        val charmPreview = findViewById<View>(R.id.charmColorPreview)
        val stringPreview = findViewById<View>(R.id.stringColorPreview)
        charmPreview?.setBackgroundColor(selectedColor ?: selectedCharm.baseColor)
        stringPreview?.setBackgroundColor(selectedStringColor ?: 0xFFAAAAAA.toInt())
    }

    private fun showColorPickerDialog(
        title: String,
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val colorPickerView = view.findViewById<ColorWheelPickerView>(R.id.colorWheelPicker)
        val swatchView = view.findViewById<View>(R.id.colorPreviewSwatch)
        val hexText = view.findViewById<TextView>(R.id.colorHexText)

        colorPickerView.color = initialColor

        val updateDialogPreview = { c: Int ->
            swatchView.setBackgroundColor(c)
            hexText.text = String.format("#%06X", 0xFFFFFF and c)
        }

        updateDialogPreview(colorPickerView.color)

        colorPickerView.onColorChanged = { newColor ->
            updateDialogPreview(newColor)
            onColorSelected(newColor)
        }

        val originalColor = initialColor

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                onColorSelected(colorPickerView.color)
            }
            .setNegativeButton("Cancel") { _, _ ->
                onColorSelected(originalColor)
            }
            .setOnCancelListener {
                onColorSelected(originalColor)
            }
            .show()
    }

    private fun setupPositionButtons() {
        findViewById<Button>(R.id.posLeft).setOnClickListener { 
            positionRatio = 0.08f; updateService()
        }
        findViewById<Button>(R.id.posCenter).setOnClickListener { 
            positionRatio = 0.5f; updateService() 
        }
        findViewById<Button>(R.id.posRight).setOnClickListener { 
            positionRatio = 0.90f; updateService()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun buildCharmGrid(grid: GridLayout) {
        CharmType.entries.forEach { charm ->
            val button = Button(this).apply {
                text = charm.displayName
                setBackgroundColor(charm.baseColor)
                setTextColor(0xFFFFFFFF.toInt())
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                layoutParams = params
                setOnClickListener {
                    if (charm == CharmType.ALPHABET) {
                        showAlphabetDialog(charm)
                    } else {
                        selectedCharm = charm
                        selectedLetter = null
                        updateColorPreviews()
                        Toast.makeText(context, "${charm.displayName} selected", Toast.LENGTH_SHORT).show()
                        updateService()
                    }
                }
            }
            grid.addView(button)
        }
    }

    private fun showAlphabetDialog(charm: CharmType) {
        val input = EditText(this)
        input.filters = arrayOf(InputFilter.LengthFilter(1))
        input.isSingleLine = true
        input.textAlignment = View.TEXT_ALIGNMENT_CENTER

        AlertDialog.Builder(this)
            .setTitle("Choose a Letter")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val letter = input.text.toString().trim()
                if (letter.isNotEmpty()) {
                    selectedCharm = charm
                    selectedLetter = letter.uppercase()
                    updateColorPreviews()
                    Toast.makeText(this, "${charm.displayName} ($selectedLetter) selected", Toast.LENGTH_SHORT).show()
                    updateService()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshPermissionStatus() {
        val hasPermission = Settings.canDrawOverlays(this)
        statusText.text = if (hasPermission) {
            getString(R.string.status_permission_granted)
        } else {
            getString(R.string.status_permission_needed)
        }
        hangButton.isEnabled = hasPermission
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }
}
