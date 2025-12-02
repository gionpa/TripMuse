package com.tripmuse.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tripmuse.ui.album.AlbumCreateScreen
import com.tripmuse.ui.album.AlbumDetailScreen
import com.tripmuse.ui.album.AlbumEditScreen
import com.tripmuse.ui.gallery.GalleryScreen
import com.tripmuse.ui.home.HomeScreen
import com.tripmuse.ui.media.MediaDetailScreen
import com.tripmuse.ui.profile.ProfileScreen
import com.tripmuse.ui.recommendation.RecommendationScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Gallery : Screen("gallery")
    object Recommendation : Screen("recommendation")
    object Profile : Screen("profile")
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
    BottomNavItem(Screen.Home, "홈", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Gallery, "갤러리", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    BottomNavItem(Screen.Recommendation, "추천", Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb),
    BottomNavItem(Screen.Profile, "프로필", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun TripMuseNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
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

            composable(Screen.Gallery.route) {
                GalleryScreen()
            }

            composable(Screen.Recommendation.route) {
                RecommendationScreen(
                    onCreateAlbumClick = {
                        navController.navigate(Screen.AlbumCreate.route)
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen()
            }

            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType })
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
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
                    }
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
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
