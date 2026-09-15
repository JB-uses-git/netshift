package com.jb.netshift

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsFragment : Fragment() {

    private lateinit var switchAutoStart: MaterialSwitch
    private lateinit var switchDarkMode: MaterialSwitch

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

        setupAutoStartToggle()
        setupDarkModeToggle()
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

    companion object {
        const val PREFS_NAME = "netshift_prefs"
        const val KEY_DARK_MODE = "dark_mode"
    }
}
