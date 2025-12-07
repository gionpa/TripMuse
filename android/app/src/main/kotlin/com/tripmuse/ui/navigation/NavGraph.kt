package com.tripmuse.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.tripmuse.data.auth.AuthEvent
import com.tripmuse.data.auth.AuthEventManager
import com.tripmuse.ui.album.AlbumViewModel
import com.tripmuse.ui.album.AlbumCreateScreen
import com.tripmuse.ui.album.AlbumDetailScreen
import com.tripmuse.ui.album.AlbumEditScreen
import com.tripmuse.ui.gallery.GalleryScreen
import com.tripmuse.ui.auth.LoginScreen
import com.tripmuse.ui.home.HomeScreen
import com.tripmuse.ui.media.MediaDetailScreen
import com.tripmuse.ui.friend.FriendScreen
import com.tripmuse.ui.profile.ProfileScreen
import com.tripmuse.ui.recommendation.RecommendationScreen
import com.tripmuse.ui.splash.SplashScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Home : Screen("home")
    object Gallery : Screen("gallery")
    object Recommendation : Screen("recommendation")
    object Profile : Screen("profile")
    object Friend : Screen("friend")
    object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    object AlbumCreate : Screen("album/create")
    object AlbumEdit : Screen("album/{albumId}/edit") {
        fun createRoute(albumId: Long) = "album/$albumId/edit"
    }
    object MediaDetail : Screen("media/{mediaId}") {
        fun createRoute(mediaId: Long) = "media/$mediaId"
    }
    object GalleryPicker : Screen("gallery/picker/{albumId}") {
        fun createRoute(albumId: Long) = "gallery/picker/$albumId"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "앨범", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Friend, "친구", Icons.Filled.People, Icons.Outlined.People),
    BottomNavItem(Screen.Recommendation, "추천", Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb),
    BottomNavItem(Screen.Profile, "프로필", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun TripMuseNavHost(
    authEventManager: AuthEventManager,
    onExitApp: () -> Unit = {},
    onNaverLoginClick: ((callback: (String?) -> Unit) -> Unit)? = null
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var showExitDialog by remember { mutableStateOf(false) }

    // Handle auth events globally - navigate to login on unauthorized
    LaunchedEffect(Unit) {
        authEventManager.authEvents.collect { event ->
            when (event) {
                is AuthEvent.Unauthorized -> {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    // Handle back press on main screens (bottom nav destinations)
    val isOnMainScreen = bottomNavItems.any { item ->
        currentDestination?.route == item.screen.route
    }

    BackHandler(enabled = isOnMainScreen) {
        showExitDialog = true
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("앱 종료") },
            text = { Text("TripMuse를 종료하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onExitApp()
                    }
                ) {
                    Text("종료")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // Custom colors for bottom navigation
    val primaryColor = Color(0xFF5B7FFF)  // Modern blue
    val unselectedColor = Color(0xFF9CA3AF)  // Gray
    val backgroundColor = Color.White
    val indicatorColor = Color(0xFFEEF2FF)  // Light blue tint

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .height(72.dp),
                    containerColor = backgroundColor,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = primaryColor,
                                selectedTextColor = primaryColor,
                                unselectedIconColor = unselectedColor,
                                unselectedTextColor = unselectedColor,
                                indicatorColor = indicatorColor
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Login.route) {
                val viewModel: com.tripmuse.ui.auth.AuthViewModel = hiltViewModel()
                LoginScreen(
                    onAuthSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onNaverLoginClick = if (onNaverLoginClick != null) {
                        {
                            android.util.Log.d("NaverLogin", "NavGraph: onNaverLoginClick triggered")
                            onNaverLoginClick { accessToken ->
                                android.util.Log.d("NaverLogin", "NavGraph: callback received, accessToken: ${accessToken?.take(20)}...")
                                if (accessToken != null) {
                                    viewModel.authenticateNaver(accessToken) {
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Login.route) { inclusive = true }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        android.util.Log.d("NaverLogin", "NavGraph: onNaverLoginClick is null!")
                        null
                    },
                    viewModel = viewModel
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onAlbumClick = { albumId ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    },
                    onCreateAlbumClick = {
                        navController.navigate(Screen.AlbumCreate.route)
                    }
                )
            }

            composable(Screen.Recommendation.route) {
                RecommendationScreen(
                    onCreateAlbumClick = {
                        navController.navigate(Screen.AlbumCreate.route)
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Friend.route) {
                FriendScreen()
            }

            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType })
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
                val albumViewModel: AlbumViewModel = hiltViewModel(backStackEntry)

                // 갤러리에서 돌아올 때 refresh 플래그가 있으면 다시 불러오기
                val refreshKey = backStackEntry.savedStateHandle
                    .getStateFlow("refreshAlbumKey", 0L)
                    .collectAsState(initial = 0L)
                LaunchedEffect(refreshKey.value) {
                    if (refreshKey.value > 0L) {
                        albumViewModel.resetFilter()
                        albumViewModel.loadAlbum(albumId)
                    }
                }

                AlbumDetailScreen(
                    albumId = albumId,
                    onBackClick = { navController.popBackStack() },
                    onMediaClick = { mediaId ->
                        navController.navigate(Screen.MediaDetail.createRoute(mediaId))
                    },
                    onAddMediaClick = { id ->
                        navController.navigate(Screen.GalleryPicker.createRoute(id))
                    },
                    onEditAlbumClick = { id ->
                        navController.navigate(Screen.AlbumEdit.createRoute(id))
                    },
                    viewModel = albumViewModel
                )
            }

            composable(Screen.AlbumCreate.route) {
                AlbumCreateScreen(
                    onBackClick = { navController.popBackStack() },
                    onAlbumCreated = { albumId ->
                        navController.popBackStack()
                        navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                    }
                )
            }

            composable(
                route = Screen.AlbumEdit.route,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType })
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
                AlbumEditScreen(
                    albumId = albumId,
                    onBackClick = { navController.popBackStack() },
                    onAlbumUpdated = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.MediaDetail.route,
                arguments = listOf(navArgument("mediaId") { type = NavType.LongType })
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: return@composable
                MediaDetailScreen(
                    mediaId = mediaId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.GalleryPicker.route,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType })
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable

                GalleryScreen(
                    isPickerMode = true,
                    albumId = albumId,
                    onMediaSelected = {
                        // 업로드 완료 후 앨범 화면으로 복귀 시 refreshAlbumKey 설정
                        navController.previousBackStackEntry?.savedStateHandle?.set("refreshAlbumKey", System.currentTimeMillis())
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
