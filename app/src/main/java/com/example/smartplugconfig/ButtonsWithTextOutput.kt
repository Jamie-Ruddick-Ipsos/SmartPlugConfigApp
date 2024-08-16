package com.example.smartplugconfig

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ButtonsWithTextOutput(
    textToDisplay: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    activity: MainActivity,
    plugWifiNetworks: SnapshotStateList<String>
) {
    var mifiSsid by remember { mutableStateOf("ssid")}
    var status by remember { mutableIntStateOf(1) }
    var scanCounter by remember { mutableIntStateOf(1) }

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
                modifier = modifier
                    .fillMaxSize()
                    .padding(0.dp)
                    .background(ipsosGreen), // Set green background
                horizontalAlignment = Alignment.CenterHorizontally

            ) {

                Spacer(modifier = Modifier.height(250.dp))
                Button(onClick = {
                    Log.d("hi - should be 1", status.toString())
                    activity.updateWifiScan()
                    status = 2
                    Log.d("bye - should be 2", status.toString())
                },
                    colors = ButtonDefaults.buttonColors(containerColor = ipsosBlue) // Set button color
                ) {
                    Text("Connect to Plug", color = Color.White)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {

                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ipsosBlue)
                ) {
                    Text("1.3", color = Color.White)
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


        2 -> {Log.d("Status", "Status = $status")      // Allow connections to the plug wifi
            viewModel.ConnectToPlugWifi(
                activity = activity,
                plugWifiNetworks = plugWifiNetworks,
            ) { result ->
                if (result != null) {
                    when (result) {
                        "Success" -> {
                            status = 4
                        }

                        "Failure" -> {
                            status = 2
                        }

                        "ReturnHome" -> {
                            status = 1
                        }
                    }
                }
            }
        }
        3 -> {Log.d("Status", "Status = $status")
            status = 2
        }
        4 -> {Log.d("Status", "Status = $status")
            // Choose MiFi Network
            viewModel.ChooseMifiNetwork(
                activity = activity,
                mifiNetwork =  {mifiSsid = it}
            ) { result ->
                if (result != null) {
                    when (result) {
                        "ConnectToMifi" -> {
                            status = 5
                        }
                        "ReturnHome" -> {
                            status = 1
                        }
                    }
                }
            }

        }
        5 -> {Log.d("Status", "Status = $status")
            // Send plug the mifi details

            Text(
                text = "Connecting plug to MiFi Device",
                fontSize = 20.sp, // Increase text size
                fontWeight = FontWeight.Bold, // Make text bold
                color = Color.Black // Text color
            )

            viewModel.connectPlugToMifi(
                ssid = mifiSsid,
                password = "1234567890"
            ) { result ->
                if (result != null) {
                    when (result) {
                        "ConnectionSuccess" -> {
                            status = 6
                        }
                        "ConnectionFailed" -> {
                            status = 1
                        }
                    }
                }
            }


        }
        6 -> {Log.d("Status", "Status = $status")
            // Connect to mifi device
            Text(
                text = "Connecting phone to MiFi device",
                fontSize = 20.sp, // Increase text size
                fontWeight = FontWeight.Bold, // Make text bold
                color = Color.Black // Text color
            )
            activity.connectToWifi(
                ssid = mifiSsid,
                password = "1234567890",
            ) { result ->
                if (result!= null){
                    when (result) {
                        "Success" -> {
                            status = 8
                        }
                        "Failure" -> {
                            status = 7
                        }

                    }
                }
            }


        }
        7-> {Log.d("Status", "Status = $status")
            status = 4
        }
        8->{Log.d("Status", "Status = $status")

            isScanning = true
            viewModel.scanDevices { result ->
                isScanning = false
                if (result != null) {
                    if (result != "No devices found"){
                        result.let { ip -> viewModel.setIpAddress(ip) } // Set the IP address in the ViewModel.
                        status = 10
                        scanCounter = 0
                    }else{
                        status = 9
                    }
                }
            }
        }
        9->{Log.d("Status", "Status = $status")
            status = if (scanCounter<3){
                8
            }else {
                2
            }
        }
        10 -> {Log.d("Status", "Status = $status")


            activity.DataCycle()

        }



        else -> {Log.d("Status", "Status = $status")}
    }
}

