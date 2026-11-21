package com.kirana.fefoguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kirana.fefoguard.domain.cart.CartManager
import com.kirana.fefoguard.ui.navigation.FefoGuardNavHost
import com.kirana.fefoguard.ui.theme.FefoGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(
            com.kirana.fefoguard.ui.util.AppLanguage
                .wrap(newBase),
        )
    }

    @Inject
    lateinit var cartManager: CartManager

    @Inject
    lateinit var backupRepository: com.kirana.fefoguard.data.repository.BackupRepository

    @Inject
    lateinit var reminderSettings: com.kirana.fefoguard.data.reminder.ReminderSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps the daily reminder alive across time-zone changes and anything that cleared the alarm.
        reminderSettings.rescheduleIfEnabled()
        // Once a day, when the app is opened, save a backup into the folder the owner chose (if any).
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.kirana.fefoguard.data.repository.AutoBackup
                .runIfDue(appContext, backupRepository)
        }
        setContent {
            FefoGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FefoGuardNavHost(cartManager = cartManager)
                }
            }
        }
    }
}
