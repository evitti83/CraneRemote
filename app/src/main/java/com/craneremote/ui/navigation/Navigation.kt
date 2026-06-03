package com.craneremote.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.craneremote.ui.screens.config.DeviceConfigScreen
import com.craneremote.ui.screens.control.ControlScreen
import com.craneremote.ui.screens.device.DeviceListScreen
import com.craneremote.ui.screens.logs.LogsScreen

sealed class Screen(val route: String) {
    object DeviceList   : Screen("device_list")
    object DeviceConfig : Screen("device_config/{deviceId}") {
        fun createRoute(id: String) = "device_config/$id"
        const val NEW = "NEW"
    }
    object Control : Screen("control/{deviceId}") {
        fun createRoute(id: String) = "control/$id"
    }
    object Logs : Screen("logs/{deviceId}") {
        fun createRoute(id: String) = "logs/$id"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.DeviceList.route) {

        composable(Screen.DeviceList.route) {
            DeviceListScreen(navController)
        }

        composable(Screen.DeviceConfig.route) { back ->
            DeviceConfigScreen(
                navController = navController,
                deviceId = back.arguments?.getString("deviceId") ?: Screen.DeviceConfig.NEW
            )
        }

        composable(Screen.Control.route) { back ->
            ControlScreen(
                navController = navController,
                deviceId = back.arguments?.getString("deviceId") ?: ""
            )
        }

        composable(Screen.Logs.route) { back ->
            LogsScreen(
                navController = navController,
                deviceId = back.arguments?.getString("deviceId") ?: ""
            )
        }
    }
}
