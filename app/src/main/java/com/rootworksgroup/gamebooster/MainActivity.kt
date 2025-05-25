package com.rootworksgroup.gamebooster

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var isOptimized = false
    private var hasRoot = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasRoot = checkRootAccess()

        setContent {
            GameBoosterUI()
        }
    }

    @Composable
    fun GameBoosterUI() {
        var status by remember { mutableStateOf("NORMAL") }
        var cpuFreq by remember { mutableStateOf("N/A") }
        var temperature by remember { mutableStateOf("N/A") }

        LaunchedEffect(Unit) {
            while (true) {
                cpuFreq = executeCommand("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
                    ?.dropLast(3)?.plus(" MHz") ?: "N/A"
                temperature = executeCommand("cat /sys/class/thermal/thermal_zone0/temp")
                    ?.let {
                        val raw = it.toFloatOrNull() ?: return@let null
                        "%.1f°C".format(raw / 1000)
                    } ?: "N/A"
                delay(1000)
            }
        }

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Status: $status", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "CPU Frequency: $cpuFreq", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Temperature: $temperature", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (hasRoot && !isOptimized) {
                                applyOptimizations()
                                isOptimized = true
                                status = "OPTIMIZED"
                                showToast("Performance tweaks applied!")
                            } else if (!hasRoot) {
                                showToast("Root access is required.")
                            }
                        },
                        enabled = !isOptimized,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Optimize")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (hasRoot) {
                                revertOptimizations()
                                isOptimized = false
                                status = "NORMAL"
                                showToast("Settings reverted.")
                            } else {
                                showToast("Root access is required.")
                            }
                        },
                        enabled = isOptimized,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Revert")
                    }
                }
            }
        }
    }

    private fun checkRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: return false
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    private fun applyOptimizations() {
        executeCommand("echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        executeCommand("echo performance > /sys/devices/system/cpu/cpu4/cpufreq/scaling_governor")
        executeCommand("echo 3000000 > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq")
        for (i in 0..7) {
            executeCommand("echo 1 > /sys/devices/system/cpu/cpu$i/online")
        }
        executeCommand("echo 0 > /sys/class/kgsl/kgsl-3d0/bus_split")
        executeCommand("echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on")
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
            showToast("Execution error: ${e.message}")
            null
        } catch (e: InterruptedException) {
            showToast("Operation interrupted")
            null
        }
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
