package com.jb.netshift

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private val homeFragment = HomeFragment()
    private val historyFragment = HistoryFragment()
    private val settingsFragment = SettingsFragment()
    private var activeFragment: Fragment = homeFragment

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val home = supportFragmentManager.findFragmentByTag("home") as? HomeFragment
        if (grants.values.all { it }) {
            startWatching()
        } else {
            home?.onServiceStateChanged(isRunning = false)
            home?.updateStatusText("Permissions denied — can't monitor network type.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle edge-to-edge system bars so UI never overlaps the notification/status bar
        val mainLayout = findViewById<android.view.View>(R.id.mainLayout)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(0, statusBars.top, 0, 0)
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, settingsFragment, "settings").hide(settingsFragment)
                .add(R.id.fragmentContainer, historyFragment, "history").hide(historyFragment)
                .add(R.id.fragmentContainer, homeFragment, "home")
                .commit()
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchFragment(homeFragment)
                R.id.nav_history -> switchFragment(historyFragment)
                R.id.nav_settings -> switchFragment(settingsFragment)
                else -> false
            }
        }
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(SettingsFragment.KEY_DARK_MODE)) {
            val isDark = prefs.getBoolean(SettingsFragment.KEY_DARK_MODE, false)
            val mode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun switchFragment(target: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .hide(activeFragment)
            .show(target)
            .commit()
        activeFragment = target
        return true
    }

    fun requestPermissionsAndStart() {
        val needed = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startWatching()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun startWatching() {
        val intent = Intent(this, NetworkWatchService::class.java)
        startForegroundService(intent)
        val home = supportFragmentManager.findFragmentByTag("home") as? HomeFragment
        home?.onServiceStateChanged(isRunning = true)
        requestBatteryExemption()
    }

    fun stopWatching() {
        stopService(Intent(this, NetworkWatchService::class.java))
        if (DataKillSwitchService.isActive) {
            DataKillSwitchService.stop(this)
        }
        val home = supportFragmentManager.findFragmentByTag("home") as? HomeFragment
        home?.onServiceStateChanged(isRunning = false)
    }

    private fun requestBatteryExemption() {
        val prefs = getSharedPreferences(SettingsFragment.PREFS_NAME, Context.MODE_PRIVATE)
        // Check if we have already prompted the user to avoid annoying repeated popups
        val hasPrompted = prefs.getBoolean("has_prompted_battery_opt", false)
        if (hasPrompted) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            prefs.edit().putBoolean("has_prompted_battery_opt", true).apply()
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}