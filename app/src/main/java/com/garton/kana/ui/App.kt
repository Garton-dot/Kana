package com.garton.kana.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@Composable
fun App(vm: KanaViewModel) {
    val pickDeck = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importAnki) }
    val onImport = { pickDeck.launch(arrayOf("*/*")) }

    vm.importMessage?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { vm.importMessage = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { vm.importMessage = null }) { Text("OK") } },
        )
    }

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
                Tab.LEARN -> HomeScreen(vm, padding, onImport)
                Tab.CHART -> ChartScreen(vm, padding)
                Tab.PROFILE -> ProfileScreen(vm, padding, onImport)
            }
        }
    }
}
