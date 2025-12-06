package com.tripmuse.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.tripmuse.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val hasValidToken by viewModel.hasValidToken.collectAsState()

    LaunchedEffect(hasValidToken) {
        delay(2000)
        when (hasValidToken) {
            true -> onNavigateToHome()
            false -> onNavigateToLogin()
            null -> { /* Still checking */ }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB4D7E8)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top spacer - matches the sky blue at top edge of image
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFB4D7E8))
        )

        // Splash image - fills width, maintains aspect ratio
        Image(
            painter = painterResource(id = R.drawable.splash_image),
            contentDescription = "TripMuse Splash",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )

        // Bottom spacer - matches the pinkish sunset color at bottom edge
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFFE6D4CF))
        )
    }
}
