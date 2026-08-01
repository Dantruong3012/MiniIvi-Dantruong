package com.example.miniivi_dantruong

sealed class Destination(
    val route: String
) {
    object Home : Destination("home")
    object Bluetooth : Destination("bluetooth")
    object Media : Destination("media")
}