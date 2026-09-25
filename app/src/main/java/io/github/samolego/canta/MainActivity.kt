package io.github.samolego.canta

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import io.github.samolego.canta.ui.CantaApp
import io.github.samolego.canta.ui.theme.CantaTheme

const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
const val APP_NAME = "Cana"
const val packageName = BuildConfig.APPLICATION_ID

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        io.github.samolego.canta.ops.CanaServices.getInstance().onSystemEvent()
        setContent {
            CantaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CantaApp(closeApp = { finishAndRemoveTask() })
                }
            }
        }
    }
}
