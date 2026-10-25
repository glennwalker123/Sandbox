package com.glennwalker.bear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.glennwalker.bear.ui.scene.SceneScreen
import com.glennwalker.bear.ui.sheet.CharacterSheetScreen
import com.glennwalker.bear.ui.theme.BearTheme
import com.glennwalker.bear.ui.theme.Led

private object Routes {
    const val SCENE = "scene"
    const val SHEET = "sheet"
}

@Composable
fun BearApp(vm: BearViewModel = viewModel { BearViewModel() }) {
    BearTheme {
        Box(Modifier.fillMaxSize().background(Led.Background)) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = Routes.SCENE) {
                composable(Routes.SCENE) {
                    SceneScreen(vm, onOpenCharacterSheet = { nav.navigate(Routes.SHEET) })
                }
                composable(Routes.SHEET) {
                    CharacterSheetScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
