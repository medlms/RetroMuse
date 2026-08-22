package com.retro.grooveplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.retro.grooveplayer.playback.PlaybackManager
import com.retro.grooveplayer.ui.components.MiniPlayer
import com.retro.grooveplayer.ui.screens.FavouritesScreen
import com.retro.grooveplayer.ui.screens.LibraryScreen
import com.retro.grooveplayer.ui.screens.PlayerScreen
import com.retro.grooveplayer.ui.screens.SettingsScreen
import com.retro.grooveplayer.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        PlaybackManager.init(this)
        try {
            com.google.android.gms.ads.MobileAds.initialize(this) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permissionLauncher = registerForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { _ -> }
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        maybeAskForReview()

        setContent {
            val themeMode = PlaybackManager.themeMode
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            PlaybackManager.isDarkTheme = isDark

            val accentColor = PlaybackManager.accentColor
            GroovePlayerTheme(accentColorHex = accentColor) {
                MainLayout()
            }
        }
    }

    /**
     * Asks for a Play rating only once the user has actually got value from the app -
     * several sessions and several tracks played. Prompting on first launch is the
     * fastest way to collect one-star reviews.
     */
    private fun maybeAskForReview() {
        if (!PlaybackManager.shouldAskForReview()) return
        try {
            val manager = com.google.android.play.core.review.ReviewManagerFactory.create(this)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    manager.launchReviewFlow(this, task.result)
                    PlaybackManager.markReviewRequested()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun MainLayout() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val accentColorHex = PlaybackManager.accentColor
    val accentColor = Color(android.graphics.Color.parseColor(accentColorHex))

    val items = listOf("Library", "Favourites", "Settings")
    val selectedIcons = mapOf(
        "Library" to Icons.Filled.LibraryMusic,
        "Favourites" to Icons.Filled.Favorite,
        "Settings" to Icons.Filled.Settings
    )
    val unselectedIcons = mapOf(
        "Library" to Icons.Outlined.LibraryMusic,
        "Favourites" to Icons.Outlined.FavoriteBorder,
        "Settings" to Icons.Outlined.Settings
    )

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // Ads are held back for the first couple of sessions - a banner before
                // the user has played anything hurts retention, which feeds ranking.
                if (currentRoute != "Player" && PlaybackManager.shouldShowAds) {
                    com.retro.grooveplayer.ui.components.BannerAdView()
                }

                // Persistent MiniPlayer if there is a current song and we are not on the Player screen
                if (PlaybackManager.currentSong != null && currentRoute != "Player") {
                    MiniPlayer(
                        onMiniPlayerClick = {
                            navController.navigate("Player") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                if (currentRoute != "Player") {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderColor))
                    NavigationBar(
                        containerColor = BgElevatedColor,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(74.dp)
                    ) {
                        items.forEach { screen ->
                            val isSelected = currentRoute == screen
                            val icon = (if (isSelected) selectedIcons[screen] else unselectedIcons[screen])
                                ?: Icons.Outlined.LibraryMusic

                            NavigationBarItem(
                                selected = isSelected,
                                alwaysShowLabel = true,
                                onClick = {
                                    navController.navigate(screen) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = screen,
                                        modifier = Modifier.size(23.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = screen,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        letterSpacing = 0.sp
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accentColor,
                                    selectedTextColor = accentColor,
                                    unselectedIconColor = TextMutedColor,
                                    unselectedTextColor = TextMutedColor,
                                    // Composited to an opaque tint so the pill never
                                    // renders solid and hides the icon inside it.
                                    indicatorColor = accentColor.copy(alpha = 0.13f)
                                        .compositeOver(BgElevatedColor)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "Library",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("Library") {
                LibraryScreen(onSongSelect = { navController.navigate("Player") })
            }
            composable("Favourites") {
                FavouritesScreen(onSongSelect = { navController.navigate("Player") })
            }
            composable("Player") {
                PlayerScreen(onBackClick = { navController.navigateUp() })
            }
            composable("Settings") {
                SettingsScreen()
            }
        }
    }
}
