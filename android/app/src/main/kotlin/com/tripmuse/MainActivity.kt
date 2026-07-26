package com.tripmuse

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import com.tripmuse.data.auth.AuthEventManager
import com.tripmuse.data.deeplink.DeepLinkManager
import com.tripmuse.data.presence.PresenceMonitor
import com.tripmuse.data.update.AppUpdateChecker
import com.tripmuse.ui.navigation.TripMuseNavHost
import com.tripmuse.ui.theme.TripMuseTheme
import com.tripmuse.ui.update.UpdateDialog
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

    @Inject
    lateinit var chatUnreadMonitor: com.tripmuse.data.presence.ChatUnreadMonitor

    @Inject
    lateinit var appUpdateChecker: AppUpdateChecker

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

        // heartbeat는 화면에 보이는 동안만 (= 온라인 표시가 실제 사용 중을 뜻하게)
        // 친구 접속 감지는 TripMuseApp에서 프로세스 단위로 계속 돌린다
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                presenceMonitor.startHeartbeat(this)
                // 폴링 주기를 기다리지 않고 복귀 즉시 탭 배지를 맞춘다
                chatUnreadMonitor.refreshNow()
                // 실행할 때마다 최신 버전인지 확인한다 (실패하면 조용히 넘어간다)
                launch { appUpdateChecker.check() }
                try {
                    awaitCancellation()
                } finally {
                    presenceMonitor.stopHeartbeat()
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
                        chatUnreadMonitor = chatUnreadMonitor,
                        onExitApp = { finish() },
                        onNaverLoginClick = { callback ->
                            naverLoginCallback = callback
                            startNaverLogin()
                        },
                        onNavControllerReady = { navController = it }
                    )

                    // 어느 화면에 있든(로그인 전 포함) 위에 뜨도록 NavHost 바깥에 둔다
                    val pendingUpdate by appUpdateChecker.pendingUpdate.collectAsState()
                    val scope = rememberCoroutineScope()
                    pendingUpdate?.let { info ->
                        UpdateDialog(
                            info = info,
                            currentVersionName = BuildConfig.VERSION_NAME,
                            onUpdate = { openStore(info.downloadUrl) },
                            onLater = { scope.launch { appUpdateChecker.snoozeCurrent() } }
                        )
                    }
                }
            }
        }
    }

    /** 스토어 또는 설정된 배포 주소를 연다. 열 수 있는 앱이 없으면 주소를 알려준다. */
    private fun openStore(downloadUrl: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
        } catch (e: ActivityNotFoundException) {
            Log.w("AppUpdate", "스토어를 열 수 없음: $downloadUrl", e)
            Toast.makeText(this, "$downloadUrl 에서 받아주세요", Toast.LENGTH_LONG).show()
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
