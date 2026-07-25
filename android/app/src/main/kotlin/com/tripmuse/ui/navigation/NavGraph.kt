package com.tripmuse.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.hilt.navigation.compose.hiltViewModel
import com.tripmuse.data.auth.AuthEvent
import com.tripmuse.data.auth.AuthEventManager
import com.tripmuse.data.deeplink.DeepLinkManager
import com.tripmuse.ui.chat.ChatListScreen
import com.tripmuse.ui.chat.ChatRoomScreen
import com.tripmuse.ui.share.SharedAlbumEntryScreen
import com.tripmuse.ui.theme.TabAccent
import com.tripmuse.ui.theme.TripMuseAccents
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
import com.tripmuse.ui.settings.SettingsScreen
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
    object Settings : Screen("settings")
    object SharedAlbum : Screen("shared/{token}") {
        fun createRoute(token: String) = "shared/$token"
    }
    object ChatList : Screen("chats")
    object ChatRoom : Screen("chat/{roomId}") {
        fun createRoute(roomId: Long) = "chat/$roomId"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val accent: TabAccent
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "앨범", Icons.Filled.Home, Icons.Outlined.Home, TripMuseAccents.Album),
    BottomNavItem(Screen.Friend, "친구", Icons.Filled.People, Icons.Outlined.People, TripMuseAccents.Friend),
    BottomNavItem(Screen.ChatList, "채팅", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, TripMuseAccents.Chat),
    BottomNavItem(Screen.Recommendation, "추천", Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb, TripMuseAccents.Recommend),
    BottomNavItem(Screen.Profile, "프로필", Icons.Filled.Person, Icons.Outlined.Person, TripMuseAccents.Profile)
)

@Composable
fun TripMuseNavHost(
    authEventManager: AuthEventManager,
    deepLinkManager: DeepLinkManager,
    onExitApp: () -> Unit = {},
    onNaverLoginClick: ((callback: (String?) -> Unit) -> Unit)? = null,
    onNavControllerReady: (NavHostController) -> Unit = {}
) {
    val navController = rememberNavController()
    LaunchedEffect(navController) {
        onNavControllerReady(navController)
    }
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
                    // 로그인 화면 전환 후 인증 에러 상태 초기화
                    authEventManager.clearAuthError()
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

    // 하단 네비게이션: 탭마다 자기 액센트로 물들되(TabAccents), 비선택은 뉴트럴로 통일
    val unselectedColor = TripMuseAccents.Unselected
    val backgroundColor = Color.White

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
                                selectedIconColor = item.accent.deep,
                                selectedTextColor = item.accent.deep,
                                unselectedIconColor = unselectedColor,
                                unselectedTextColor = unselectedColor,
                                indicatorColor = item.accent.container
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
                // 로그인 성공 시: 처리 대기 중인 공유 딥링크가 있으면 그쪽으로, 없으면 홈으로
                val navigateAfterAuth: () -> Unit = {
                    val pendingToken = deepLinkManager.consumePendingShareToken()
                    val destination = if (pendingToken != null) {
                        Screen.SharedAlbum.createRoute(pendingToken)
                    } else {
                        Screen.Home.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
                LaunchedEffect(Unit) {
                    android.util.Log.d("DeepLink", "Login screen: pendingShareToken=${deepLinkManager.pendingShareToken != null}")
                }
                LoginScreen(
                    onAuthSuccess = navigateAfterAuth,
                    onNaverLoginClick = if (onNaverLoginClick != null) {
                        {
                            android.util.Log.d("NaverLogin", "NavGraph: onNaverLoginClick triggered")
                            onNaverLoginClick { accessToken ->
                                android.util.Log.d("NaverLogin", "NavGraph: callback received, accessToken: ${accessToken?.take(20)}...")
                                if (accessToken != null) {
                                    viewModel.authenticateNaver(accessToken) {
                                        navigateAfterAuth()
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
                    onNavigateToAlbum = { albumId ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumId))
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
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Friend.route) {
                FriendScreen(
                    onNavigateToChatRoom = { roomId ->
                        navController.navigate(Screen.ChatRoom.createRoute(roomId))
                    }
                )
            }

            composable(Screen.ChatList.route) {
                ChatListScreen(
                    onRoomClick = { roomId ->
                        navController.navigate(Screen.ChatRoom.createRoute(roomId))
                    }
                )
            }

            composable(
                route = Screen.ChatRoom.route,
                arguments = listOf(navArgument("roomId") { type = NavType.LongType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getLong("roomId") ?: return@composable
                ChatRoomScreen(
                    roomId = roomId,
                    onBackClick = { navController.popBackStack() }
                )
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
                    onMediaClick = { mediaId, allMediaIds ->
                        // Pass media ID list via savedStateHandle
                        navController.currentBackStackEntry?.savedStateHandle?.set("mediaIds", allMediaIds.toLongArray())
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
                // Retrieve media IDs from previous screen's savedStateHandle
                val mediaIds = navController.previousBackStackEntry?.savedStateHandle?.get<LongArray>("mediaIds")?.toList() ?: listOf(mediaId)
                MediaDetailScreen(
                    initialMediaId = mediaId,
                    mediaIds = mediaIds,
                    onBackClick = {
                        // 미디어 상세에서 돌아올 때 앨범 미디어 목록 새로고침 (댓글 읽음 상태 반영)
                        navController.previousBackStackEntry?.savedStateHandle?.set("refreshAlbumKey", System.currentTimeMillis())
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = Screen.SharedAlbum.route,
                arguments = listOf(navArgument("token") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "https://tripmuse-production.up.railway.app/share/{token}" },
                    navDeepLink { uriPattern = "tripmuse://share/{token}" }
                )
            ) { backStackEntry ->
                val token = backStackEntry.arguments?.getString("token") ?: return@composable
                SharedAlbumEntryScreen(
                    token = token,
                    onResolved = { albumId ->
                        navController.navigate(Screen.AlbumDetail.createRoute(albumId)) {
                            popUpTo(Screen.SharedAlbum.route) { inclusive = true }
                        }
                    },
                    onInvalidLink = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.SharedAlbum.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
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
