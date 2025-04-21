package com.avilapp.streamdeskide

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.avilapp.streamdeskide.presentation.home.HomeView
import com.avilapp.streamdeskide.presentation.home.HomeViewModel
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener

/*fun main() = App()*/
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Custom Stream Deck") {
        val viewModel = remember { HomeViewModel() }

        LaunchedEffect(Unit) {
            try {
                GlobalScreen.registerNativeHook()
                GlobalScreen.addNativeKeyListener(object : NativeKeyListener {
                    override fun nativeKeyPressed(e: NativeKeyEvent) {
                        println("🔑 Código recibido: ${e.keyCode}")
                        viewModel.onKeyPressed(e.keyCode)
                    }

                    override fun nativeKeyReleased(e: NativeKeyEvent) {}
                    override fun nativeKeyTyped(e: NativeKeyEvent) {}
                })
            } catch (e: Exception) {
                println("❌ Error al registrar NativeHook: ${e.message}")
            }
        }

        HomeView(viewModel)
    }
}