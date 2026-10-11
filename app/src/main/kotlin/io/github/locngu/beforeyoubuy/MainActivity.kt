package io.github.locngu.beforeyoubuy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.locngu.beforeyoubuy.navigation.AppNavigation
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeforeYouBuyTheme {
                AppNavigation()
            }
        }
    }
}
