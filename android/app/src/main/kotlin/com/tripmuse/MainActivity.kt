package com.tripmuse

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import com.tripmuse.data.auth.AuthEventManager
import com.tripmuse.data.deeplink.DeepLinkManager
import com.tripmuse.data.presence.PresenceMonitor
import com.tripmuse.ui.navigation.TripMuseNavHost
import com.tripmuse.ui.theme.TripMuseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authEventManager: AuthEventManager

    @Inject
    lateinit var deepLinkManager: DeepLinkManager

    @Inject
    lateinit var presenceMonitor: PresenceMonitor

    private var naverLoginCallback: ((String?) -> Unit)? = null
    private var navController: NavHostController? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("Presence", "notification permission granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // 앱이 화면에 보이는 동안만 heartbeat/친구 접속 감지를 돌린다
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                presenceMonitor.start(this)
                try {
                    awaitCancellation()
                } finally {
                    presenceMonitor.stop()
                }
            }
        }

        // Initialize Naver Login SDK
        NaverIdLoginSDK.initialize(
            context = this,
            clientId = getString(R.string.naver_client_id),
            clientSecret = getString(R.string.naver_client_secret),
            clientName = getString(R.string.naver_client_name)
        )

        enableEdgeToEdge()
        setContent {
            TripMuseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TripMuseNavHost(
                        authEventManager = authEventManager,
                        deepLinkManager = deepLinkManager,
                        onExitApp = { finish() },
                        onNaverLoginClick = { callback ->
                            naverLoginCallback = callback
                            startNaverLogin()
                        },
                        onNavControllerReady = { navController = it }
                    )
                }
            }
        }
    }

    // singleTask 실행 중 공유 링크를 탭했을 때 딥링크 처리
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navController?.handleDeepLink(intent)
    }

    private fun startNaverLogin() {
        Log.d("NaverLogin", "startNaverLogin called")
        val oauthLoginCallback = object : OAuthLoginCallback {
            override fun onSuccess() {
                val accessToken = NaverIdLoginSDK.getAccessToken()
                Log.d("NaverLogin", "onSuccess - accessToken: ${accessToken?.take(20)}...")
                naverLoginCallback?.invoke(accessToken)
                naverLoginCallback = null
            }

            override fun onFailure(httpStatus: Int, message: String) {
                Log.e("NaverLogin", "onFailure - httpStatus: $httpStatus, message: $message")
                naverLoginCallback?.invoke(null)
                naverLoginCallback = null
            }

            override fun onError(errorCode: Int, message: String) {
                Log.e("NaverLogin", "onError - errorCode: $errorCode, message: $message")
                naverLoginCallback?.invoke(null)
                naverLoginCallback = null
            }
        }

        Log.d("NaverLogin", "Calling NaverIdLoginSDK.authenticate")
        NaverIdLoginSDK.authenticate(this, oauthLoginCallback)
    }
}
