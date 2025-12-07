package com.tripmuse

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.navercorp.nid.NaverIdLoginSDK
import com.navercorp.nid.oauth.OAuthLoginCallback
import com.tripmuse.data.auth.AuthEventManager
import com.tripmuse.ui.navigation.TripMuseNavHost
import com.tripmuse.ui.theme.TripMuseTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authEventManager: AuthEventManager

    private var naverLoginCallback: ((String?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                        onExitApp = { finish() },
                        onNaverLoginClick = { callback ->
                            naverLoginCallback = callback
                            startNaverLogin()
                        }
                    )
                }
            }
        }
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
