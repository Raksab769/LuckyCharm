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
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.content.res.Configuration
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    private var selectedCharm: CharmType = CharmType.CLOVER
    private var selectedLetter: String? = null
    private var selectedColor: Int? = null
    private var selectedStringColor: Int? = null
    private var gyroSensitivity: Float = 0.05f
    private var positionRatio: Float = 0.5f
    private var isHanging = false
    private var customPhotoPath: String? = null

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
        val checkUpdatesButton = findViewById<Button>(R.id.btnCheckUpdates)
        val charmGrid = findViewById<GridLayout>(R.id.charmsPanel)
        val stringGrid = findViewById<GridLayout>(R.id.stringsPanel)

        buildCharmGrid(charmGrid)
        buildStringGrid(stringGrid)
        setupSensitivityBar()
        setupColorPickers()
        setupPositionButtons()
        setupTabs()

        grantButton.setOnClickListener { requestOverlayPermission() }

        hangButton.setOnClickListener {
            maybeRequestNotificationPermission()
            if (!isHanging) {
                updateService()
                isHanging = true
                hangButton.text = "🔴 REMOVE CHARM"
                hangButton.setBackgroundResource(R.drawable.bg_neon_red)
                Toast.makeText(this, "${selectedCharm.displayName} is hanging up top!", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, CharmOverlayService::class.java).apply {
                    action = CharmOverlayService.ACTION_HIDE
                }
                startService(intent)
                isHanging = false
                hangButton.text = "✨ HANG CHARM"
                hangButton.setBackgroundResource(R.drawable.bg_neon_green)
                Toast.makeText(this, "Charm removed", Toast.LENGTH_SHORT).show()
            }
        }
        
        checkUpdatesButton.setOnClickListener {
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        val btn = findViewById<Button>(R.id.btnCheckUpdates)
        btn.isEnabled = false
        btn.text = "Checking..."

        @Suppress("DEPRECATION")
        val currentVersionCode = try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (_: Exception) { 1 }

        val currentVersionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }

        thread {
            try {
                val timeStamp = System.currentTimeMillis()
                val url = URL("https://raw.githubusercontent.com/Raksab769/LuckyCharm/master/app/build.gradle.kts?nocache=$timeStamp")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "LuckyCharmApp")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.useCaches = false

                if (connection.responseCode == 200) {
                    val gradleContent = connection.inputStream.bufferedReader().readText()
                    val remoteVersionCode = Regex("""versionCode\s*=\s*(\d+)""").find(gradleContent)?.groupValues?.get(1)?.toIntOrNull() ?: currentVersionCode
                    val remoteVersionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(gradleContent)?.groupValues?.get(1) ?: currentVersionName

                    Handler(Looper.getMainLooper()).post {
                        btn.isEnabled = true
                        btn.text = "Check for Updates"
                        if (remoteVersionCode > currentVersionCode) {
                            showUpdateDialog(remoteVersionName)
                        } else {
                            Toast.makeText(this@MainActivity, "You are on the latest version ($currentVersionName)!", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        btn.isEnabled = true
                        btn.text = "Check for Updates"
                        Toast.makeText(this@MainActivity, "Server check failed (${connection.responseCode}). Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    btn.isEnabled = true
                    btn.text = "Check for Updates"
                    Toast.makeText(this@MainActivity, "Check failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private var downloadId: Long = -1L

    private fun showUpdateDialog(newVersionName: String) {
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("Lucky Charm v$newVersionName is now available! Would you like to download and install it now?")
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstallUpdate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun downloadAndInstallUpdate() {
        Toast.makeText(this, "Downloading update...", Toast.LENGTH_SHORT).show()
        val timeStamp = System.currentTimeMillis()
        val apkUrl = "https://raw.githubusercontent.com/Raksab769/LuckyCharm/master/app/release/app-release.apk?nocache=$timeStamp"
        val fileName = "LuckyCharm-update-$timeStamp.apk"

        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir?.listFiles()?.forEach { f ->
                if (f.name.startsWith("LuckyCharm-update")) {
                    try { f.delete() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Lucky Charm Update")
            .setDescription("Downloading latest version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
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
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
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
                putExtra(CharmOverlayService.EXTRA_CUSTOM_PHOTO_PATH, customPhotoPath)
                putExtra(CharmOverlayService.EXTRA_STRING_TYPE, selectedStringType.name)
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

        val btnCharm = findViewById<View>(R.id.btnPickCharmColor)
        val btnString = findViewById<View>(R.id.btnPickStringColor)

        btnCharm?.setOnClickListener {
            showColorPickerDialog(
                "Choose Charm Color",
                selectedColor ?: selectedCharm.baseColor
            ) { newColor ->
                selectedColor = newColor
                updateColorPreviews()
                if (isHanging) updateService()
            }
        }

        btnString?.setOnClickListener {
            showColorPickerDialog(
                "Choose String Color",
                selectedStringColor ?: 0xFFAAAAAA.toInt()
            ) { newColor ->
                selectedStringColor = newColor
                updateColorPreviews()
                if (isHanging) updateService()
            }
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
        val btnLeft = findViewById<Button>(R.id.posLeft)
        val btnCenter = findViewById<Button>(R.id.posCenter)
        val btnRight = findViewById<Button>(R.id.posRight)

        val updatePosButtons = { ratio: Float ->
            positionRatio = ratio
            btnLeft?.setBackgroundResource(if (ratio == 0.08f) R.drawable.bg_neon_purple else R.drawable.bg_neon_subtle)
            btnCenter?.setBackgroundResource(if (ratio == 0.5f) R.drawable.bg_neon_purple else R.drawable.bg_neon_subtle)
            btnRight?.setBackgroundResource(if (ratio == 0.90f) R.drawable.bg_neon_purple else R.drawable.bg_neon_subtle)
            if (isHanging) updateService()
        }

        btnLeft?.setOnClickListener { updatePosButtons(0.08f) }
        btnCenter?.setOnClickListener { updatePosButtons(0.5f) }
        btnRight?.setOnClickListener { updatePosButtons(0.90f) }
    }

    private fun setupTabs() {
        val tabCharms = findViewById<View>(R.id.tabCharms)
        val tabStrings = findViewById<View>(R.id.tabStrings)
        val tabMotion = findViewById<View>(R.id.tabMotion)
        
        val tabCharmsText = findViewById<TextView>(R.id.tabCharmsText)
        val tabStringsText = findViewById<TextView>(R.id.tabStringsText)
        val tabMotionText = findViewById<TextView>(R.id.tabMotionText)

        val indicatorActive = findViewById<View>(R.id.tabIndicatorActive)
        
        val charmsPanel = findViewById<View>(R.id.charmsPanel)
        val stringsPanel = findViewById<View>(R.id.stringsPanel)
        val motionPanel = findViewById<View>(R.id.motionPanel)

        fun selectTab(index: Int) {
            val primaryColor = ContextCompat.getColor(this, R.color.purple_500)
            val secondaryColor = ContextCompat.getColor(this, R.color.text_secondary_dark)
            
            tabCharmsText?.setTextColor(if (index == 0) primaryColor else secondaryColor)
            tabStringsText?.setTextColor(if (index == 1) primaryColor else secondaryColor)
            tabMotionText?.setTextColor(if (index == 2) primaryColor else secondaryColor)

            charmsPanel?.visibility = if (index == 0) View.VISIBLE else View.GONE
            stringsPanel?.visibility = if (index == 1) View.VISIBLE else View.GONE
            motionPanel?.visibility = if (index == 2) View.VISIBLE else View.GONE

            indicatorActive?.animate()?.translationX(
                when (index) {
                    1 -> indicatorActive.width.toFloat()
                    2 -> indicatorActive.width.toFloat() * 2
                    else -> 0f
                }
            )?.setDuration(200)?.start()
        }

        tabCharms?.setOnClickListener { selectTab(0) }
        tabStrings?.setOnClickListener { selectTab(1) }
        tabMotion?.setOnClickListener { selectTab(2) }
        
        // ensure default state is set
        indicatorActive?.post { selectTab(0) }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun View.setNeonStyle(strokeColor: Int) {
        val density = resources.displayMetrics.density
        val cornerRadiusPx = 100f * density
        val strokeWidthPx = (2.5f * density).toInt()

        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(Color.parseColor("#1A182B"))
            setStroke(strokeWidthPx, strokeColor)
        }

        val rippleColor = ColorStateList.valueOf((strokeColor and 0x00FFFFFF) or 0x40000000)
        background = RippleDrawable(rippleColor, shape, null)
    }

    private fun buildCharmGrid(grid: GridLayout) {
        grid.removeAllViews()
        CharmType.entries.forEach { charm ->
            val button = AppCompatButton(this).apply {
                text = charm.displayName
                textSize = 12f
                isAllCaps = false
                setTypeface(null, Typeface.BOLD)
                setNeonStyle(charm.baseColor)
                setTextColor(Color.WHITE)
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (48f * resources.displayMetrics.density).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                layoutParams = params
                setOnClickListener {
                    if (charm == CharmType.ALPHABET) {
                        showAlphabetDialog(charm)
                    } else if (charm == CharmType.CUSTOM_PHOTO) {
                        pickImageLauncher.launch("image/*")
                    } else {
                        selectedCharm = charm
                        selectedLetter = null
                        updateColorPreviews()
                        Toast.makeText(context, "${charm.displayName} selected", Toast.LENGTH_SHORT).show()
                        if (isHanging) updateService()
                    }
                }
            }
            grid.addView(button)
        }
    }

    private var selectedStringType: StringType = StringType.BASIC

    private fun buildStringGrid(grid: GridLayout) {
        grid.removeAllViews()
        StringType.entries.forEach { sType ->
            val button = AppCompatButton(this).apply {
                text = sType.displayName
                textSize = 10f
                isAllCaps = false
                setTypeface(null, Typeface.BOLD)
                setNeonStyle(sType.defaultColor)
                setTextColor(Color.WHITE)
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (48f * resources.displayMetrics.density).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                layoutParams = params
                setOnClickListener {
                    selectedStringType = sType
                    updateColorPreviews()
                    Toast.makeText(context, "${sType.displayName} selected", Toast.LENGTH_SHORT).show()
                    if (isHanging) updateService()
                }
            }
            grid.addView(button)
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                val file = File(filesDir, "custom_charm.png")
                contentResolver.openInputStream(it)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                selectedCharm = CharmType.CUSTOM_PHOTO
                selectedLetter = null
                customPhotoPath = file.absolutePath
                updateColorPreviews()
                Toast.makeText(this, "Custom photo loaded!", Toast.LENGTH_SHORT).show()
                if (isHanging) updateService()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAlphabetDialog(charm: CharmType) {
        val input = EditText(this).apply {
            filters = arrayOf(InputFilter.LengthFilter(1))
            isSingleLine = true
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        }

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
        val permissionCard = findViewById<View>(R.id.permissionCard)
        statusText.text = if (hasPermission) {
            getString(R.string.status_permission_granted)
        } else {
            getString(R.string.status_permission_needed)
        }
        permissionCard?.visibility = if (hasPermission) View.GONE else View.VISIBLE
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
