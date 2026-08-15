package com.retro.grooveplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
}

@Composable
fun MainLayout() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val accentColorHex = PlaybackManager.accentColor
    val accentColor = Color(android.graphics.Color.parseColor(accentColorHex))

    val items = listOf("Library", "Favourites", "Player", "Settings")
    val icons = mapOf(
        "Library" to "🏠",
        "Favourites" to "❤️",
        "Player" to "▶️",
        "Settings" to "⚙️"
    )
    val labels = mapOf(
        "Library" to "Library",
        "Favourites" to "Favourites",
        "Player" to "Now Playing",
        "Settings" to "Settings"
    )

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // AdMob Banner Ad (when not on full Player screen)
                if (currentRoute != "Player") {
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

                NavigationBar(
                    containerColor = BgModalColor,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(72.dp)
                ) {
                    items.forEach { screen ->
                        val isSelected = currentRoute == screen
                        val label = labels[screen] ?: screen
                        val emoji = icons[screen] ?: "🎵"

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
                                Text(
                                    text = emoji,
                                    fontSize = if (isSelected) 20.sp else 18.sp
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    color = if (isSelected) accentColor else TextMutedColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    letterSpacing = 0.2.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent
                            )
                        )
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
