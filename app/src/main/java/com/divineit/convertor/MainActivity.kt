package com.divineit.convertor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.divineit.convertor.navigation.COMING_SOON_SCREENS
import com.divineit.convertor.navigation.Routes
import com.divineit.convertor.theme.ConvertorTheme
import com.divineit.convertor.ui.CompareScreen
import com.divineit.convertor.ui.ComingSoonScreen
import com.divineit.convertor.ui.DifferencesScreen
import com.divineit.convertor.ui.HomeScreen
import com.divineit.convertor.ui.ProjectsScreen
import com.divineit.convertor.ui.ReportScreen
import com.divineit.convertor.ui.ReviewScreen
import com.divineit.convertor.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as ConvertorApp
        setContent {
            val themeSetting by app.settings.theme.collectAsState(initial = "system")
            val textScale by app.settings.textScale.collectAsState(initial = 1.0f)
            val darkTheme = when (themeSetting) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val density = LocalDensity.current
            ConvertorTheme(darkTheme = darkTheme) {
                // Font-only scaling: layout density is untouched, so only
                // text sizes follow the Settings slider.
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = density.density,
                        fontScale = density.fontScale * textScale
                    )
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        NavHost(
                            navController = navController,
                            startDestination = Routes.HOME
                        ) {
                            composable(Routes.HOME) {
                                HomeScreen(onNavigate = { navController.navigate(it) })
                            }
                            composable(
                                route = Routes.COMPARE,
                                arguments = listOf(
                                    navArgument("projectId") {
                                        type = NavType.LongType
                                        defaultValue = -1L
                                    }
                                )
                            ) { backStackEntry ->
                                val projectId = backStackEntry.arguments
                                    ?.getLong("projectId")
                                    ?.takeIf { it != -1L }
                                CompareScreen(
                                    projectId = projectId,
                                    onBack = { navController.popBackStack() },
                                    onShowDifferences = {
                                        navController.navigate(Routes.DIFFERENCES)
                                    },
                                    onShowReview = {
                                        navController.navigate(Routes.REVIEW)
                                    },
                                    onShowReport = {
                                        navController.navigate(Routes.REPORT)
                                    }
                                )
                            }
                            composable(Routes.DIFFERENCES) {
                                DifferencesScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(Routes.REVIEW) {
                                ReviewScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(Routes.REPORT) {
                                ReportScreen(
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(Routes.PROJECTS) {
                                ProjectsScreen(
                                    onBack = { navController.popBackStack() },
                                    onOpenProject = { project ->
                                        navController.navigate(
                                            Routes.compareRoute(project.id)
                                        )
                                    }
                                )
                            }
                            composable(Routes.SETTINGS) {
                                SettingsScreen(onBack = { navController.popBackStack() })
                            }
                            composable(Routes.COMING_SOON) { backStackEntry ->
                                val key = backStackEntry.arguments?.getString("key") ?: ""
                                val (title, phase) = COMING_SOON_SCREENS[key]
                                    ?: ("Coming soon" to "a later phase")
                                ComingSoonScreen(
                                    title = title,
                                    phase = phase,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
