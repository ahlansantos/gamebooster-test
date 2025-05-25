package com.rootworksgroup.gamebooster


import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var isOptimized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0F2027),
                                    Color(0xFF203A43),
                                    Color(0xFF2C5364)
                                )
                            )
                        )
                ) {
                    GameBoosterUI()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GameBoosterUI() {
        var status by remember { mutableStateOf("NORMAL") }
        var cpuInfo by remember { mutableStateOf("N/A") }
        var temp by remember { mutableStateOf("N/A") }
        var selectedMode by remember { mutableStateOf("Normal") }
        var games by remember { mutableStateOf(listOf<String>()) }

        val modes = listOf("Battery Saver", "Normal", "Pro", "Diablo")

        LaunchedEffect(Unit) {
            while (true) {
                cpuInfo = executeCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                    ?.dropLast(3) ?: "N/A"
                temp = executeCommand("cat /sys/class/thermal/thermal_zone0/temp")
                    ?.take(2) ?: "N/A"
                delay(1000)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("Status: $status", color = Color.White)
                Text("CPU: $cpuInfo MHz", color = Color.White)
                Text("Temp: $temp°C", color = Color.White)
            }

            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selectedMode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Mode") },
                    modifier = Modifier.menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    modes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode) },
                            onClick = {
                                selectedMode = mode
                                expanded = false
                                applyMode(mode)
                                status = mode.uppercase()
                                showToast("Mode: $mode")
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        isOptimized = false
                        revertOptimizations()
                        status = "NORMAL"
                        showToast("Reverted")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Revert")
                }

                Button(
                    onClick = {
                        showToast("Add game clicked")
                        games = games + "Game ${games.size + 1}"
                    }
                ) {
                    Text("Add Game")
                }
            }

            Text("Your Games", color = Color.White, style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(games.size) { index ->
                    Text(
                        text = games[index],
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    private fun applyMode(mode: String) {
        when (mode) {
            "Battery Saver" -> {
                executeCommand("echo powersave > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                executeCommand("echo 1200000 > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq")
            }

            "Normal" -> {
                revertOptimizations()
            }

            "Pro" -> {
                executeCommand("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                executeCommand("echo 2400000 > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq")
            }

            "Diablo" -> {
                executeCommand("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
                executeCommand("echo 3000000 > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq")
                for (i in 0..7) {
                    executeCommand("echo 1 > /sys/devices/system/cpu/cpu$i/online")
                }
                executeCommand("echo 0 > /sys/class/kgsl/kgsl-3d0/bus_split")
                executeCommand("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on")
            }
        }
    }

    private fun revertOptimizations() {
        executeCommand("echo ondemand > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        executeCommand("echo 2200000 > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq")
        executeCommand("echo 1 > /sys/class/kgsl/kgsl-3d0/bus_split")
        executeCommand("echo 0 > /sys/class/kgsl/kgsl-3d0/force_clk_on")
    }

    private fun executeCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                result.append(line).append("\n")
            }
            process.waitFor()
            result.toString().trim()
        } catch (e: IOException) {
            showToast("Exec error: ${e.message}")
            null
        } catch (e: InterruptedException) {
            showToast("Interrupted")
            null
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
