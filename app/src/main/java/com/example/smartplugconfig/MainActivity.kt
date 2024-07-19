package com.example.smartplugconfig

//noinspection UsingMaterialAndMaterial3Libraries
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smartplugconfig.ui.theme.SmartPlugConfigTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL


class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(     // Any required permissions
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )

    lateinit var wifiManager: WifiManager
    var mifiNetworks = mutableStateListOf<String>()
    var plugWifiNetworks = mutableStateListOf<String>()
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        initialisation()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartPlugConfigTheme {
                SmartPlugConfigApp(activity = this, plugWifiNetworks = plugWifiNetworks)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                }
            }
        }
    }
    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)} passing\n      in a {@link RequestMultiplePermissions} object for the {@link ActivityResultContract} and\n      handling the result in the {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                // Permission denied
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun wifiManagerInitialisation(){
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        }

    private fun initialisation() {
        wifiManagerInitialisation()
        checkAndRequestPermissions()

    }
    fun turnOnWifi(context: Context): String {
        // Create an Intent to open the hotspot settings
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)

        // Check if there is an activity that can handle this intent
        if (intent.resolveActivity(context.packageManager) != null) {
            // Start the settings activity
            context.startActivity(intent)
            return "Opening wifi settings..."
        } else {
            return "Unable to open wifi settings."
        }
    }
// Establishes all necessary permissions for a wifi scan
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            if (allPermissionsGranted) {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
                // Return back to code

            } else {
                Toast.makeText(
                    this,
                    "All permissions are required to start the hotspot",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            Toast.makeText(this, "All permissions already granted!", Toast.LENGTH_SHORT).show()
            // Return back to code
        }
    }

    // Connect to normal wifi
    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("ServiceCast")
    fun connectToWifi(ssid: String, password: String) {

        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(wifiNetworkSpecifier)
            .build()

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                super.onAvailable(network)
                connectivityManager.bindProcessToNetwork(network)
                Log.d("WifiConnection", "Connected to $ssid")
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.d("WifiConnection", "Connection to $ssid failed")
            }
        })
    }

    // Connect to open wifi (no password) temporary
    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToOpenWifi(ssid: String) {
        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(wifiNetworkSpecifier)
            .build()

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                super.onAvailable(network)
                connectivityManager.bindProcessToNetwork(network)
                Log.d("WifiConnection", "Connected to $ssid")
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.d("WifiConnection", "Connection to $ssid failed")
            }
        })
    }


    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.O)
    fun startLocalOnlyHotspot() {
        wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                super.onStarted(reservation)
                hotspotReservation = reservation
                val config = reservation.wifiConfiguration
                config?.let { config ->
                    Toast.makeText(
                        this@MainActivity,
                        " SSID=${config.SSID} Password=${config.preSharedKey}",
                        Toast.LENGTH_LONG
                    ).show()
                } ?: run {
                    Toast.makeText(
                        this@MainActivity,
                        "Hotspot started, but configuration is null",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onStopped() {
                super.onStopped()
                Toast.makeText(this@MainActivity, "Hotspot stopped", Toast.LENGTH_SHORT).show()
            }

            override fun onFailed(reason: Int) {
                super.onFailed(reason)
                Toast.makeText(this@MainActivity, "Hotspot failed to start: $reason", Toast.LENGTH_SHORT).show()
            }
        }, null)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        super.onDestroy()
        hotspotReservation?.close()
    }
}



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

    @RequiresApi(Build.VERSION_CODES.Q)
    @Composable
    fun connectToPlugWifi(activity: MainActivity, plugWifiNetworks: SnapshotStateList<String>, status: (Int) -> Unit): String {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().background(Color(0xFF00B140))

        ) {
            RefreshWifiButton(activity = activity, status = status)
            activity.DisplayPlugNetworks(activity, plugWifiNetworks, status = status)
            ReturnWifiButton(status = status)
        }
        return "Trying to connect to wifi"
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun turnOnHotspot(context: Context, activity: MainActivity): String {
        // Create an Intent to open the hotspot settings
       val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)

        // Check if there is an activity that can handle this intent
        if (intent.resolveActivity(context.packageManager) != null) {
            // Start the settings activity
            context.startActivity(intent)
            return "Opening hotspot settings..."
        } else {
            return "Unable to open hotspot settings."
        }

        //This here is a test for how to connect through a local only hotspot. I have no idea if there is any
        //Practical way to use it as we only find out the ssid and password after creation
//        if()
//        activity.startLocalHotspot()



        return ("Turning on Hotspot")
    }

fun ipScan(): String {
        return "Scanning for IP Address..."
    }

    fun sendWifiConfig(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = sendWifiConfigInternal()
            onResult(result)
        }
    }

    private suspend fun sendWifiConfigInternal(): String {
        //uses default ip for tasmota plug wifi ap
        val urlString = "http://192.168.4.1/cm?cmnd=Backlog%20SSID1%20Pixel%3B%20Password1%20intrasonics%3B%20WifiConfig%205%3B%20restart%201"
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
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SmartPlugConfigApp(viewModel: MainViewModel = viewModel(), activity: MainActivity, plugWifiNetworks: SnapshotStateList<String>) {
    var currentTextOutput by remember { mutableStateOf("output") }
    val context = LocalContext.current

    ButtonsWithTextOutput(
        textToDisplay = currentTextOutput,
        setCurrentTextOutput = { currentTextOutput = it },
        context = context,
        viewModel = viewModel,
        activity = activity,
        plugWifiNetworks = plugWifiNetworks
    )
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ButtonsWithTextOutput(
    textToDisplay: String,
    setCurrentTextOutput: (String) -> Unit,
    context: Context,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    activity: MainActivity,
    plugWifiNetworks: SnapshotStateList<String>
) {
    val coroutineScope = rememberCoroutineScope()
    var status by remember { mutableIntStateOf(1) }
    val ipsosBlue = Color(0xFF0033A0) // Ipsos Blue color
    val ipsosGreen = Color(0xFF00B140) // Ipsos Green color
    var isScanning by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("Scanning") }
    // LaunchedEffect to animate the loading text
    LaunchedEffect(isScanning) {
        while (isScanning) {
            loadingText = "Scanning"
            delay(500)
            loadingText = "Scanning."
            delay(500)
            loadingText = "Scanning.."
            delay(500)
            loadingText = "Scanning..."
            delay(500)
        }
    }
    when (status) {
        1 -> {  //Default gives you all expected options


            Column(
                modifier = modifier.fillMaxSize().padding(0.dp).background(ipsosGreen), // Set green background
                horizontalAlignment = Alignment.CenterHorizontally
                
            ) {

                Spacer(modifier = Modifier.height(250.dp))
                Button(onClick = {
                    Log.d("hi - should be 1", status.toString())
                    activity.wifiList()
                    status = 2
                    Log.d("bye - should be 2", status.toString())
                },
                    colors = ButtonDefaults.buttonColors(containerColor = ipsosBlue) // Set button color
                ) {
                    Text("Connect to Plug", color = Color.White)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = {
                    viewModel.sendWifiConfig { result ->
                        setCurrentTextOutput(result)
                    }
                },            colors = ButtonDefaults.buttonColors(containerColor = ipsosBlue) // Set button color
                ) {
                    Text("Send Wifi config", color = Color.White)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = {
                    val result = viewModel.turnOnHotspot(context, activity = activity)
                    setCurrentTextOutput(result)
                },
                    colors = ButtonDefaults.buttonColors(containerColor = ipsosBlue) // Set button color
                ) {
                    Text("Switch on Hotspot", color = Color.White)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                    isScanning = true
                    viewModel.scanDevices(context) { result ->
                        isScanning = false
                        if (result != null) {
                            setCurrentTextOutput(result)
                        }
                        result?.let { ip -> viewModel.setIpAddress(ip) } // Set the IP address in the ViewModel.

                    }
                },
                    colors = ButtonDefaults.buttonColors(containerColor = ipsosBlue) // Set button color
                ) {
                    Text("Find IP address of plug", color = Color.White)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                    viewModel.sendMQTTConfig { result ->
                        setCurrentTextOutput(result)
                    }
                },
                    colors = ButtonDefaults.buttonColors(containerColor = ipsosBlue)
                ) {
                    Text("Send MQTT config", color = Color.White)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                    viewModel.getPowerReading { result ->
                        setCurrentTextOutput(result)
                    }
                },
                    colors = ButtonDefaults.buttonColors(containerColor = ipsosBlue)
                ) {
                    Text("Pull power data", color = Color.White)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (isScanning) loadingText else textToDisplay,
                    fontSize = 20.sp, // Increase text size
                    fontWeight = FontWeight.Bold, // Make text bold
                    color = Color.Black // Text color
                )
            }
        }


        2 -> {      // Allow connections to the plug wifi
            viewModel.connectToPlugWifi(
                activity = activity,
                plugWifiNetworks = plugWifiNetworks,
                status = { status = it },
            )
        }
        3 -> {
            status = 2
        }

        else -> {}
    }
}


// code from facto, class used in ip scan functionality


class DeviceScanner(private val context: Context) {

    fun scanDevices(callback: ScanCallback?) {
        ScanTask(callback).execute()
    }

    @SuppressLint("StaticFieldLeak")
    inner class ScanTask(private val callback: ScanCallback?) : AsyncTask<Void, Void, List<String>>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg params: Void?): List<String> {
            val deviceList = mutableListOf<String>()
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo

            Log.d("DeviceScanner", "Starting scan in range 192.168.y.z")

            // Scan the range 192.168.y.z where y and z vary from 0 to 255
            for (y in 0..255) {
                for (z in 1..254) { // Skipping 0 and 255 for z as they are typically not used for hosts
                    val hostAddress = "192.168.$y.$z"

                    // Log each host address being scanned
                    Log.d("DeviceScanner", "Scanning IP: $hostAddress")

                    // Check if the specific IP address is being scanned
                    //if (hostAddress == "192.168.240.238") {
                        //Log.d("DeviceScanner", "Specific IP 192.168.240.238 is being scanned")
                    //}

                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(hostAddress, 80), 40) // Increased timeout to 20ms too little think 40ms is best
                        deviceList.add(hostAddress)
                        socket.close()
                    } catch (e: IOException) {
                        Log.d("DeviceScanner", "Failed to connect to $hostAddress: ${e.message}")
                    }
                }
            }
            return deviceList
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(result: List<String>) {
            callback?.onScanCompleted(result)
        }
    }

    interface ScanCallback {
        fun onScanCompleted(devices: List<String>)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun MainActivity.DisplayPlugNetworks(activity: MainActivity, plugWifiNetworks: List<String>, status: (Int) -> Unit){
    Log.d("hi again", "It should be scanning now?")

    // For each network add a button to connect
    plugWifiNetworks .forEach { ssid ->
        Button(onClick = {
            if(wifiManager.isWifiEnabled){
                activity.connectToOpenWifi(ssid)
                Log.d("Initialise", "WiFi is turned on, connecting to plug")
            }else{
                Toast.makeText(
                    this,
                    "WiFi must be turned on",
                    Toast.LENGTH_LONG
                ).show()
                val result = turnOnWifi(this)
                Log.d("Initialise", result)
            }
            Log.d("test", "connect_to_jamie_is_trying: $ssid")
            status(1)
        },colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0033A0))) {
            Text(ssid, color = Color.White)
        }
    }
}

// Adds a button to allow refresh of networks if it doesn't appear
@Composable
fun RefreshWifiButton(activity: MainActivity, status: (Int) -> Unit) {
    Button(onClick = {
        activity.wifiList()
        status(3)   // Sends to section where it then goes back
    },colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0033A0))) {
        Text("Refresh Networks", color = Color.White)
    }
}

// Adds a button to allow return to main menu
@Composable
fun ReturnWifiButton(status: (Int) -> Unit) {
    Button(onClick = {
        status(1)
    },colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0033A0))) {
        Text("Return to home", color = Color.White)
    }
}

@SuppressLint("MissingPermission")
private fun MainActivity.wifiList() {

    val wifiFullnfo = wifiManager.scanResults // Find local networks

    // Find just the SSIDs
    val wifiNetworksList = wifiFullnfo.map { it.SSID }
    var filteredWifiNetworksList = wifiNetworksList.filter {
        it.contains("plug", ignoreCase = true) or it.contains(
            "tasmota",
            ignoreCase = true
        )
    }
    plugWifiNetworks.clear()
    plugWifiNetworks.addAll(filteredWifiNetworksList)

    filteredWifiNetworksList = wifiNetworksList.filter { it.contains("4g", ignoreCase = true) }
    mifiNetworks.clear()
    mifiNetworks.addAll(filteredWifiNetworksList)
    Log.d("test", "wifiManager_jamie_is_trying: $plugWifiNetworks")
}

@RequiresApi(Build.VERSION_CODES.O)
private fun MainActivity.startLocalHotspot(){

    startLocalOnlyHotspot()
}
