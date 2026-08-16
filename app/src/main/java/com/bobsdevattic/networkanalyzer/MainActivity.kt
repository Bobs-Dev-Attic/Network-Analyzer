package com.bobsdevattic.networkanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.networkanalyzer.ui.InterfaceScreen
import com.bobsdevattic.networkanalyzer.ui.InterfaceViewModel
import com.bobsdevattic.networkanalyzer.ui.theme.NetworkAnalyzerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetworkAnalyzerTheme {
                val vm: InterfaceViewModel = viewModel()
                val info by vm.state.collectAsStateWithLifecycle()
                Scaffold { innerPadding ->
                    InterfaceScreen(
                        info = info,
                        onRefresh = vm::refresh,
                        onBind = vm::bindToEthernet,
                        onUnbind = vm::unbind,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
