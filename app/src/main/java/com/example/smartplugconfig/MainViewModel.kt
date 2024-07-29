package com.example.smartplugconfig

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL


class MainViewModel : ViewModel() {
    private val _ipAddress = mutableStateOf<String?>(null)
    val ipAddress: State<String?> = _ipAddress

    fun setIpAddress(ip: String) {
        _ipAddress.value = ip
    }

    fun scanDevices(context: Context, onScanCompleted: (String?) -> Unit) {
        val deviceScanner = DeviceScanner(context)
        deviceScanner.scanDevices(object : DeviceScanner.ScanCallback {
            override fun onScanCompleted(devices: List<String>) {
                val result = if (devices.isEmpty()) {
                    "No devices found"
                } else {
                    devices.joinToString("\\n")
                }
                _ipAddress.value = result
                onScanCompleted(result)
            }
        })
    }


    @Composable
    fun connectToPlugWifi(activity: MainActivity, plugWifiNetworks: SnapshotStateList<String>, status: (Int) -> Unit, state: Int): String {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF00B140))

        ) {
            RefreshWifiButton(activity = activity, status = status)
            activity.DisplayPlugNetworks(activity, plugWifiNetworks, status = status, state = state)
            ReturnWifiButton(status = status)
        }
        return "Trying to connect to wifi"
    }

    @Composable
    fun ChooseMifiNetwork(activity: MainActivity, status: (Int) -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF00B140))

        ) {
            RefreshMifiButton(activity = activity, status = status)
            activity.DisplayMifiNetworks(activity, status = status)
            ReturnWifiButton(status = status)
        }
    }

    fun connectPlugToMifi(activity: MainActivity, status: (Int) -> Unit, ssid: String, password: String) {
        if (activity.mifiNetworks.size == 1) {
            this.sendWifiConfig(ssid, password){
                    result -> if (result.contains("error", ignoreCase = true)){
                Log.e("Error", "Couldn't connect plug to MiFi")
                status(1)
            }else{
                Log.d("Success", "Plug and MiFi are connected")
                status(6)
            }
            }
        }else{
            Log.e("Error", "Length of MiFi should be one")
        }
    }

    fun sendWifiConfig( ssid: String = "Pixel", password: String = "intrasonics",onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = sendWifiConfigInternal(ssid, password)
            onResult(result)
        }
    }

    private suspend fun sendWifiConfigInternal(ssid: String, password: String): String {
        //uses default ip for tasmota plug wifi ap
        val urlString = "http://192.168.4.1/cm?cmnd=Backlog%20SSID1%20${ssid}%3B%20Password1%20${password}%3B%20WifiConfig%205%3B%20restart%201"
        return try {
            Log.d("sendWifiConfig", "Attempting to send request to $urlString")
            val url = URL(urlString)
            withContext(Dispatchers.IO) {
                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"
                    Log.d("sendWifiConfig", "Request method set to $requestMethod")

                    val responseCode = responseCode
                    Log.d("sendWifiConfig", "Response code: $responseCode")
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = inputStream.bufferedReader().use(BufferedReader::readText)
                        Log.d("sendWifiConfig", "Response: $response")
                        "Response: $response"
                    } else {
                        "HTTP error code: $responseCode"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("sendWifiConfig", "Exception occurred", e)
            "Error: ${e.localizedMessage ?: "An unknown error occurred"}"
        }
    }

    fun turnOnHotspot(context: Context): String {
//         Create an Intent to open the hotspot settings
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)

        // Check if there is an activity that can handle this intent
        if (intent.resolveActivity(context.packageManager) != null) {
            // Start the settings activity
            context.startActivity(intent)
            return "Opening hotspot settings..."
        } else {
            return "Unable to open hotspot settings."
        }
    }

    fun ipScan(): String {
        return "Scanning for IP Address..."
    }

    fun sendMQTTConfig(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = sendMQTTConfigInternal()
            onResult(result)
        }
    }

    private suspend fun sendMQTTConfigInternal(): String {
        val ip = _ipAddress.value
        val urlString = "http://${ip}/cm?cmnd=Backlog%20MqttHost%20testHost%3B%20MqttUser%20Test1%3B%20MqttPassword%20Test2%3B%20Topic%20smartPlugTest"
        return try {
            Log.d("sendMQTTConfig", "Attempting to send request to $urlString")
            val url = URL(urlString)
            withContext(Dispatchers.IO) {
                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"
                    Log.d("sendMQTTConfig", "Request method set to $requestMethod")

                    val responseCode = responseCode
                    Log.d("sendMQTTConfig", "Response code: $responseCode")
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = inputStream.bufferedReader().use(BufferedReader::readText)
                        Log.d("sendMQTTConfig", "Response: $response")
                        "Response: $response"
                    } else {
                        "HTTP error code: $responseCode"
                    }
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Error: ${e.localizedMessage ?: "An unknown error occurred"}"
            Log.e("sendMQTTConfig", errorMessage, e)
            errorMessage
        }
    }

    fun getPowerReading(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = getPowerReadingInternal()
            onResult(result)
        }
    }

    private suspend fun getPowerReadingInternal(): String {
        val ip = _ipAddress.value
        val urlString = "http://${ip}/cm?cmnd=Status%208"
        return try {
            Log.d("getPowerReading", "Attempting to send request to $urlString")
            val url = URL(urlString)
            withContext(Dispatchers.IO) {
                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"
                    Log.d("getPowerReading", "Request method set to $requestMethod")

                    val responseCode = responseCode
                    Log.d("getPowerReading", "Response code: $responseCode")
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = inputStream.bufferedReader().use(BufferedReader::readText)
                        Log.d("getPowerReading", "Response: $response")

                        // Parse the JSON response
                        val jsonObject = JSONObject(response)
                        val statusSNS = jsonObject.getJSONObject("StatusSNS")
                        val energy = statusSNS.getJSONObject("ENERGY")
                        val power = energy.getInt("Power")

                        // Return the formatted string
                        "Power: $power Watts"
                    } else {
                        "HTTP error code: $responseCode"
                    }
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Error: ${e.localizedMessage ?: "An unknown error occurred"}"
            Log.e("getPowerReading", errorMessage, e)
            errorMessage
            return "ConnectionFailure"
        }
    }
}