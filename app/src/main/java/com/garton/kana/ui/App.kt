package com.garton.kana.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@Composable
fun App(vm: KanaViewModel) {
    when (val screen = vm.screen) {
        Screen.Quiz -> {
            val quiz = vm.quiz
            if (quiz != null) QuizScreen(vm, quiz) else Box(Modifier.fillMaxSize())
        }
        is Screen.Results -> ResultsScreen(vm, screen)
        Screen.Home -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(Tab.LEARN to ("🏠" to "Learn"), Tab.CHART to ("あ" to "Chart"), Tab.PROFILE to ("🏆" to "Profile")).forEach { (tab, look) ->
                        NavigationBarItem(
                            selected = vm.tab == tab,
                            onClick = { vm.tab = tab },
                            icon = { Text(look.first, fontSize = 22.sp) },
                            label = { Text(look.second) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = KanaColors.Sakura.copy(alpha = 0.2f)),
                        )
                    }
                }
            },
        ) { padding ->
            when (vm.tab) {
                Tab.LEARN -> HomeScreen(vm, padding)
                Tab.CHART -> ChartScreen(vm, padding)
                Tab.PROFILE -> ProfileScreen(vm, padding)
            }
        }
    }
}
