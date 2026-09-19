package com.jb.netshift

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment() {

    private lateinit var switchAutoStart: MaterialSwitch
    private lateinit var switchDarkMode: MaterialSwitch
    private lateinit var switchAutoBlock4G: MaterialSwitch
    private lateinit var btnEnableVpn: MaterialButton
    private lateinit var vpnStatusText: TextView

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
        btnEnableVpn = view.findViewById(R.id.btnEnableVpn)
        vpnStatusText = view.findViewById(R.id.vpnStatusText)

        setupAutoStartToggle()
        setupDarkModeToggle()
        setupAutoBlock4GToggle()
        setupVpnConsentButton()
    }

    override fun onResume() {
        super.onResume()
        updateVpnConsentStatus()
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
            // Already consented
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
