package ca.amandeep.path

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.amandeep.path.ui.main.MainScreen
import ca.amandeep.path.ui.main.MainViewModelImpl
import ca.amandeep.ui.core.theme.PATHTheme
import com.github.ajalt.timberkt.d

class MainActivity : ComponentActivity() {
    private val mainViewModelImpl by viewModels<MainViewModelImpl>()

    private lateinit var developerMenuTrigger: DeveloperMenuTrigger
    private lateinit var rootView: View

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()
        setContent {
            val navController = rememberNavController()
            val windowSize = calculateWindowSizeClass(this)
            PATHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(mainViewModelImpl, windowSize)
                        }
                    }
                }
            }
        }

        rootView = findViewById(android.R.id.content)

        // Initialize the trigger
        developerMenuTrigger = DeveloperMenuTrigger(
            context = this,
            onTriggered = {
                d { "Developer menu activated!" }

            }
        )

        // Get screen dimensions *after* the layout is drawn
        rootView.post {
            val width = rootView.width
            val height = rootView.height
            if (width > 0 && height > 0) {
                developerMenuTrigger.setScreenDimensions(width, height)
            } else {
                d { "Could not get valid root view dimensions." }
            }
        }
    }

    // Override dispatchTouchEvent to intercept all touch events before they reach child views
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            developerMenuTrigger.onTouchEvent(ev)
        }
        // Always call super.dispatchTouchEvent to allow normal UI interaction
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        developerMenuTrigger.cleanup() // Important: Cancel coroutines and resources
        super.onDestroy()
    }
}
