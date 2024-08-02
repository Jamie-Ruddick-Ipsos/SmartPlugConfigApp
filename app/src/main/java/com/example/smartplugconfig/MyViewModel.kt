package com.example.smartplugconfig

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartplugconfig.hotspot.UnhiddenSoftApConfigurationBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.Executor

class MainViewModel : ViewModel() {
    private val _ipAddress = mutableStateOf<String?>(null)
    val ipAddress: State<String?> = _ipAddress

    companion object {
        @Volatile private var instance: MainViewModel? = null

        fun getInstance(): MainViewModel =
            instance ?: synchronized(this) {
                instance ?: MainViewModel().also { instance = it }
            }
    }


    fun setIpAddress(ip: String) {
        _ipAddress.value = ip
    }

    fun scanDevices(context: Context, onScanCompleted: (String) -> Unit) {
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

    fun connectToPlugWifi(context: Context): String {
        // Create an Intent to open the Wi-Fi settings
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)

        // Check if there is an activity that can handle this intent
        if (intent.resolveActivity(context.packageManager) != null) {
            // Start the settings activity
            context.startActivity(intent)
            return "Opening Wi-Fi settings..."
        } else {
            return "Unable to open Wi-Fi settings."
        }
    }


//    fun turnOnHotspot(context: Context): String {
//        // Create an Intent to open the hotspot settings
//        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
//
//        // Check if there is an activity that can handle this intent
//        if (intent.resolveActivity(context.packageManager) != null) {
//            // Start the settings activity
//            context.startActivity(intent)
//            return "Opening hotspot settings..."
//        } else {
//            return "Unable to open hotspot settings."
//        }
//    }


    fun sendWifiConfig(
        ssid: String = "Pixel",
        password: String = "intrasonics",
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = sendWifiConfigInternal(ssid, password)
            onResult(result)
        }
    }

    private suspend fun sendWifiConfigInternal(ssid: String, password: String): String {
        //uses default ip for tasmota plug wifi ap
        val urlString =
            "http://192.168.4.1/cm?cmnd=Backlog%20SSID1%20${ssid}%3B%20Password1%20${password}%3B%20WifiConfig%205%3B%20restart%201"
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



    @RequiresApi(33)
    fun turnOnHotspot(context: Context): String {
        if (isLocalOnlyHotspotEnabled(context)) {
            return "Hotspot is already active."
        }
        startLocalOnlyHotspotWithConfig(
            context = context,
            config = UnhiddenSoftApConfigurationBuilder()
                .setSsid("Pixel")
                .setAutoshutdownEnabled(false)
                .setPassphrase(
                    passphrase = "intrasonics",
                    securityType = SoftApConfiguration.SECURITY_TYPE_WPA2_PSK
                )
                .build(),
            executor = null,
            callback = object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(reservation)
                    Log.d("Hotspot", "Hotspot started")
                }

                override fun onStopped() {
                    super.onStopped()
                    Log.d("Hotspot", "Hotspot stopped")
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    Log.d("Hotspot", "Hotspot failed with reason $reason")
                }
            })

        return "starting a newhotspot connection..."
    }

    @SuppressLint("NewApi")
    fun startLocalOnlyHotspotWithConfig(
        context: Context,
        config: SoftApConfiguration,
        executor: Executor?,
        callback: WifiManager.LocalOnlyHotspotCallback
    ) {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        WifiManager::class.java.getMethod(
            "startLocalOnlyHotspot", SoftApConfiguration::class.java, Executor::class.java,
            WifiManager.LocalOnlyHotspotCallback::class.java,
        ).invoke(wifiManager, config, executor, callback)
    }

    fun sendMQTTConfig(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = sendMQTTConfigInternal()
            onResult(result)
        }
    }

    private suspend fun sendMQTTConfigInternal(): String {
        val ip = _ipAddress.value
        val host =
            "192.168.222.252"  //test values for mqtt broker app on my phone
        val topic = "random/Topic"

        val urlString =
            "http://${ip}/cm?cmnd=Backlog%20MqttHost%20$host%3B%20MqttUser%20Test1%3B%20MqttPassword%20Test2%3B%20Topic%20$topic"
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getPowerReading(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = getPowerReadingInternal(context)
            onResult(result)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun getPowerReadingInternal(context: Context): String {
        val ip = _ipAddress.value
        val urlString = "http://${ip}/cm?cmnd=Status%208"
        var attempts = 0

        while (attempts < 3) {
            if (isLocalOnlyHotspotEnabled(context)) {
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
                                val response =
                                    inputStream.bufferedReader().use(BufferedReader::readText)
                                Log.d("getPowerReading", "Response: $response")

                                // Parse the JSON response
                                val jsonObject = JSONObject(response)
                                val statusSNS = jsonObject.getJSONObject("StatusSNS")
                                val energy = statusSNS.getJSONObject("ENERGY")
                                val power = energy.getInt("Power")

                                // Return the formatted string
                                return@withContext "Power: $power Watts"
                            } else {
                                return@withContext "HTTP error code: $responseCode"
                            }
                        }
                    }
                } catch (e: Exception) {
                    val errorMessage = "Error: ${e.localizedMessage ?: "An unknown error occurred"}"
                    Log.e("getPowerReading", errorMessage, e)
                    return errorMessage
                }
            } else {
                turnOnHotspot(context)
                delay(3000)
                attempts++
            }
        }
        return "Unable to send request after 3 attempts"
    }

    //is only actually checking if device has ip but wifi should never be on so i think is ok for now at least for soak testing
    private fun isLocalOnlyHotspotEnabled(context: Context): Boolean {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        Log.d(
                            "isLocalOnlyHotspotEnabled",
                            "Device IP Address: ${address.hostAddress}"
                        )
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("isLocalOnlyHotspotEnabled", "Device does not have an IP address")
            return false
        }
        Log.d("isLocalOnlyHotspotEnabled", "Cannot get IP address")
        return false

    }

}