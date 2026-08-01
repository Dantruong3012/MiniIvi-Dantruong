package com.example.miniivi_dantruong

import android.content.ContextWrapper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.miniivi_dantruong.ui.bluetooth.BluetoothScreen
import com.example.miniivi_dantruong.ui.bluetooth.BluetoothViewModel
import com.example.miniivi_dantruong.ui.home.HomeScreen
import com.example.miniivi_dantruong.ui.media.MediaScreen
import com.example.miniivi_dantruong.ui.media.MediaViewModel

@Composable
fun Navigation(navRootController: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is ComponentActivity) {
                break
            }
            currentContext = currentContext.baseContext
        }
        currentContext as ComponentActivity
    }


    val mediaViewModel: MediaViewModel = hiltViewModel(activity)
    val bluetoothViewModel: BluetoothViewModel = hiltViewModel(activity)

    /* thay vì lấy lifecycle của component hiện tại,
     dùng LocalLifecycleOwner đảm bảo tính nhất quán (idomatic) */
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        Log.d("MiniIviLog", "Navigation: addObserver to lifecycle")
        lifecycleOwner.lifecycle.addObserver(mediaViewModel)
        lifecycleOwner.lifecycle.addObserver(bluetoothViewModel)

        onDispose {
            Log.d("MiniIviLog", "Navigation: removeObserver from lifecycle")
            lifecycleOwner.lifecycle.removeObserver(mediaViewModel)
            lifecycleOwner.lifecycle.removeObserver(bluetoothViewModel)
        }
    }

    NavHost(
        navController = navRootController,
        startDestination = Destination.Home.route,
        modifier = modifier
    ) {
      composable(Destination.Home.route) {
          HomeScreen(
              mediaViewModel = mediaViewModel,
              bluetoothViewModel = bluetoothViewModel,
              navController = navRootController
          )
      }
      composable(Destination.Media.route) {
          MediaScreen(viewModel = mediaViewModel)
      }
      composable(Destination.Bluetooth.route) {
          BluetoothScreen(viewModel = bluetoothViewModel)
      }
    }
}