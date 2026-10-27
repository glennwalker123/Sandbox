package com.glennwalker.bear

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.glennwalker.bear.health.HealthConnectSource
import com.glennwalker.bear.ui.BearApp
import com.glennwalker.bear.ui.KeyStore
import com.glennwalker.bear.ui.theme.Led
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/** The Claude API key, in the app's private storage on this phone. */
class PrefsKeyStore(context: Context) : KeyStore {
    private val prefs = context.getSharedPreferences("bear", Context.MODE_PRIVATE)
    override var claudeKey: String?
        get() = prefs.getString("claude_key", null)
        set(value) = prefs.edit().putString("claude_key", value).apply()
}

class MainActivity : ComponentActivity() {
    private val askHealth = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        health.onChanged?.invoke()
    }
    private val health by lazy { HealthConnectSource.get(this) }

    override fun onResume() {
        super.onResume()
        // Steps may have gone up while the app was in the background.
        health.onChanged?.invoke()
    }

    override fun onDestroy() {
        health.ask = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Led.Voice = FontFamily(Font(R.font.doto_black))
        val keys = PrefsKeyStore(applicationContext)
        health.ask = { askHealth.launch(it) }
        setContent { BearApp(keys, health) }
    }
}
