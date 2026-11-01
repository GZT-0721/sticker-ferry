package com.stickerferry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stickerferry.ui.*

/** 底部导航项 */
private enum class Tab(val route: String, val label: String, val icon: String) {
    LIBRARY("library", "表情库", "🗂️"),
    STUDIO("studio", "AI 工坊", "✨"),
    TRANSFER("transfer", "投放", "🔀"),
    SETTINGS("settings", "设置", "⚙️"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StickerFerryTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // 主页面才显示底部导航
    val showBar = Tab.entries.any { it.route == current }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                ) {
                    Tab.entries.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    nav.navigate(tab.route) {
                                        popUpTo(Tab.LIBRARY.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Text(tab.icon, fontSize = 18.sp) },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            NavHost(navController = nav, startDestination = Tab.LIBRARY.route) {
                composable(Tab.LIBRARY.route) {
                    LibraryScreen(vm, onGoStudio = { nav.navigate(Tab.STUDIO.route) })
                }
                composable(Tab.STUDIO.route) {
                    StudioScreen(vm, onGoConfig = { nav.navigate("aicfg") })
                }
                composable(Tab.TRANSFER.route) {
                    TransferScreen(vm, onGoStudio = { nav.navigate(Tab.STUDIO.route) })
                }
                composable(Tab.SETTINGS.route) {
                    SettingsScreen(vm, onGoAiConfig = { nav.navigate("aicfg") })
                }
                composable("aicfg") {
                    AiConfigScreen(vm, onDone = { nav.popBackStack() })
                }
            }
        }
    }
}
