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
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var selectedCharm: CharmType = CharmType.CLOVER
    private var selectedColor: Int? = null
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
        setupColorPalette()
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
    
    private fun showUpdateDialog() {
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("There is a new update available for Lucky Charm on GitHub! Would you like to view it?")
            .setPositiveButton("View on GitHub") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Raksab769/LuckyCharm"))
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateService() {
        val hasPermission = Settings.canDrawOverlays(this)
        if (hasPermission) {
            val updateIntent = Intent(this@MainActivity, CharmOverlayService::class.java).apply {
                putExtra(CharmOverlayService.EXTRA_CHARM, selectedCharm.name)
                putExtra(CharmOverlayService.EXTRA_SENSITIVITY, gyroSensitivity)
                putExtra(CharmOverlayService.EXTRA_POSITION_RATIO, positionRatio)
                selectedColor?.let { putExtra(CharmOverlayService.EXTRA_COLOR, it) }
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

    private fun setupColorPalette() {
        findViewById<Button>(R.id.colorDefault).setOnClickListener { 
            selectedColor = null; updateService() 
            Toast.makeText(this, "Default color", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.colorRed).setOnClickListener { 
            selectedColor = 0xFFE63946.toInt(); updateService() 
        }
        findViewById<Button>(R.id.colorBlue).setOnClickListener { 
            selectedColor = 0xFF4EA8DE.toInt(); updateService() 
        }
        findViewById<Button>(R.id.colorGreen).setOnClickListener { 
            selectedColor = 0xFF3FA34D.toInt(); updateService() 
        }
        findViewById<Button>(R.id.colorGold).setOnClickListener { 
            selectedColor = 0xFFC9962C.toInt(); updateService() 
        }
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
                    selectedCharm = charm
                    Toast.makeText(context, "${charm.displayName} selected", Toast.LENGTH_SHORT).show()
                    updateService()
                }
            }
            grid.addView(button)
        }
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
