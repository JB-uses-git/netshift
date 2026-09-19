package com.jb.netshift

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.jb.netshift.data.AppDatabase
import java.util.concurrent.Executors

class SettingsFragment : Fragment() {

    private lateinit var switchAutoStart: MaterialSwitch
    private lateinit var switchDarkMode: MaterialSwitch
    private lateinit var switchAutoBlock4G: MaterialSwitch
    private lateinit var switchVibrateAlert: MaterialSwitch
    private lateinit var btnEnableVpn: MaterialButton
    private lateinit var vpnStatusText: TextView

    private lateinit var textQuotaLimitSummary: TextView
    private lateinit var textQuotaLimitValue: TextView

    private val dbExecutor = Executors.newSingleThreadExecutor()

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        updateVpnConsentStatus()
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(requireContext(), "Data protection enabled ✓", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchAutoStart = view.findViewById(R.id.switchAutoStart)
        switchDarkMode = view.findViewById(R.id.switchDarkMode)
        switchAutoBlock4G = view.findViewById(R.id.switchAutoBlock4G)
        switchVibrateAlert = view.findViewById(R.id.switchVibrateAlert)
        btnEnableVpn = view.findViewById(R.id.btnEnableVpn)
        vpnStatusText = view.findViewById(R.id.vpnStatusText)

        textQuotaLimitSummary = view.findViewById(R.id.textQuotaLimitSummary)
        textQuotaLimitValue = view.findViewById(R.id.textQuotaLimitValue)

        setupAutoStartToggle()
        setupDarkModeToggle()
        setupAutoBlock4GToggle()
        setupVpnConsentButton()
        setupQuotaPicker(view)
        setupResetStats(view)
        setupVibrateToggle()
        setupBatteryOptimization(view)
        setupClearHistory(view)
    }

    override fun onResume() {
        super.onResume()
        updateVpnConsentStatus()
        updateQuotaDisplay()
    }

    private fun setupAutoStartToggle() {
        val context = requireContext()
        val componentName = ComponentName(context, BootReceiver::class.java)
        val pm = context.packageManager

        val state = pm.getComponentEnabledSetting(componentName)
        val isEnabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

        switchAutoStart.isChecked = isEnabled

        switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            val newState = if (isChecked) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                componentName,
                newState,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun setupDarkModeToggle() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isSystemDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        val isDarkSaved = prefs.getBoolean(KEY_DARK_MODE, isSystemDark)

        switchDarkMode.isChecked = isDarkSaved

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    private fun setupAutoBlock4GToggle() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(NetworkWatchService.KEY_AUTO_BLOCK_4G, true)

        switchAutoBlock4G.isChecked = isEnabled

        switchAutoBlock4G.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(NetworkWatchService.KEY_AUTO_BLOCK_4G, isChecked).apply()

            // If turning off, also stop any active kill-switch immediately
            if (!isChecked && DataKillSwitchService.isActive) {
                DataKillSwitchService.stop(requireContext())
            }
        }
    }

    private fun setupVibrateToggle() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(NetworkWatchService.KEY_VIBRATE_ALERT, true)
        switchVibrateAlert.isChecked = isEnabled

        switchVibrateAlert.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(NetworkWatchService.KEY_VIBRATE_ALERT, isChecked).apply()
        }
    }

    private fun setupQuotaPicker(view: View) {
        val card = view.findViewById<View>(R.id.cardDailyQuota)
        val options = arrayOf(
            "1.0 GB / day (Light plan)",
            "1.5 GB / day (Standard plan)",
            "2.0 GB / day (Medium plan)",
            "2.5 GB / day (Heavy plan)",
            "3.0 GB / day (Pro plan)"
        )
        val valuesMb = longArrayOf(1024L, 1500L, 2048L, 2560L, 3072L)

        card.setOnClickListener {
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = prefs.getLong(NetworkWatchService.KEY_DAILY_QUOTA_MB, NetworkWatchService.DEFAULT_DAILY_QUOTA_MB)

            var selectedIndex = valuesMb.indexOf(current)
            if (selectedIndex < 0) selectedIndex = 1

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Daily 4G Quota")
                .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                    val chosenMb = valuesMb[which]
                    prefs.edit().putLong(NetworkWatchService.KEY_DAILY_QUOTA_MB, chosenMb).apply()
                    updateQuotaDisplay()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateQuotaDisplay() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentMb = prefs.getLong(NetworkWatchService.KEY_DAILY_QUOTA_MB, NetworkWatchService.DEFAULT_DAILY_QUOTA_MB)

        val gb = currentMb / 1024.0
        textQuotaLimitValue.text = if (currentMb == 1500L) "1.5 GB" else String.format("%.1f GB", gb)
        textQuotaLimitSummary.text = "${textQuotaLimitValue.text} per day (Alerts & tracker scale to this)"
    }

    private fun setupResetStats(view: View) {
        val action = {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset Session Stats?")
                .setMessage("This will zero out current 4G usage counters for this session.")
                .setPositiveButton("Reset") { _, _ ->
                    requireContext().getSharedPreferences(NetworkWatchService.PREFS_STATS, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply()
                    Toast.makeText(requireContext(), "Stats reset to zero", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        view.findViewById<View>(R.id.cardResetStats).setOnClickListener { action() }
        view.findViewById<View>(R.id.btnResetStats).setOnClickListener { action() }
    }

    private fun setupBatteryOptimization(view: View) {
        view.findViewById<View>(R.id.cardBatteryOpt).setOnClickListener {
            val context = requireContext()
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                startActivity(intent)
            } else {
                Toast.makeText(context, "Battery optimizations already disabled ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClearHistory(view: View) {
        val action = {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear Network History?")
                .setMessage("Are you sure you want to permanently erase all logged 4G fallback sessions?")
                .setPositiveButton("Clear All") { _, _ ->
                    dbExecutor.execute {
                        try {
                            val db = AppDatabase.getDatabase(requireContext().applicationContext)
                            db.networkEventDao().clearAll()
                            activity?.runOnUiThread {
                                Toast.makeText(requireContext(), "History erased ✓", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        view.findViewById<View>(R.id.cardClearHistory).setOnClickListener { action() }
        view.findViewById<View>(R.id.btnClearHistory).setOnClickListener { action() }
    }

    private fun setupVpnConsentButton() {
        btnEnableVpn.setOnClickListener {
            val prepareIntent = VpnService.prepare(requireContext())
            if (prepareIntent != null) {
                vpnConsentLauncher.launch(prepareIntent)
            } else {
                Toast.makeText(requireContext(), "Already enabled ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateVpnConsentStatus() {
        val prepareIntent = VpnService.prepare(requireContext())
        if (prepareIntent == null) {
            vpnStatusText.text = "Permission granted ✓"
            btnEnableVpn.text = "Enabled"
            btnEnableVpn.isEnabled = false
        } else {
            vpnStatusText.text = "Required one-time permission for the data blocker to work"
            btnEnableVpn.text = "Enable"
            btnEnableVpn.isEnabled = true
        }
    }

    companion object {
        const val PREFS_NAME = "netshift_prefs"
        const val KEY_DARK_MODE = "dark_mode"
    }
}
