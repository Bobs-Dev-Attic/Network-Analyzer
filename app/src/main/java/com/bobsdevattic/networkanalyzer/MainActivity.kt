package com.bobsdevattic.networkanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.networkanalyzer.ui.DiscoveryScreen
import com.bobsdevattic.networkanalyzer.ui.DiscoveryViewModel
import com.bobsdevattic.networkanalyzer.ui.InterfaceScreen
import com.bobsdevattic.networkanalyzer.ui.InterfaceViewModel
import com.bobsdevattic.networkanalyzer.ui.StatisticsScreen
import com.bobsdevattic.networkanalyzer.ui.StatisticsViewModel
import com.bobsdevattic.networkanalyzer.ui.theme.NetworkAnalyzerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetworkAnalyzerTheme {
                AnalyzerApp()
            }
        }
    }
}

private enum class Screen(val label: String) {
    Link("Link"),
    Stats("Statistics"),
    Hosts("Hosts"),
}

@Composable
private fun AnalyzerApp() {
    val interfaceVm: InterfaceViewModel = viewModel()
    val statsVm: StatisticsViewModel = viewModel()
    val discoveryVm: DiscoveryViewModel = viewModel()

    val info by interfaceVm.state.collectAsStateWithLifecycle()
    val stats by statsVm.state.collectAsStateWithLifecycle()
    val discovery by discoveryVm.state.collectAsStateWithLifecycle()

    // M2 polls continuously while the app is in memory; lifecycle-gating can come
    // later. Kick it off once on first composition.
    androidx.compose.runtime.LaunchedEffect(Unit) { statsVm.start() }

    var selected by remember { mutableIntStateOf(0) }
    val screens = Screen.entries

    Scaffold { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selected) {
                screens.forEachIndexed { index, screen ->
                    Tab(
                        selected = selected == index,
                        onClick = { selected = index },
                        text = { Text(screen.label) },
                    )
                }
            }
            when (screens[selected]) {
                Screen.Link -> InterfaceScreen(
                    info = info,
                    onRefresh = interfaceVm::refresh,
                    onBind = interfaceVm::bindToEthernet,
                    onUnbind = interfaceVm::unbind,
                )
                Screen.Stats -> StatisticsScreen(stats = stats)
                Screen.Hosts -> DiscoveryScreen(
                    state = discovery,
                    onScan = discoveryVm::scan,
                    onCancel = discoveryVm::cancel,
                )
            }
        }
    }
}
