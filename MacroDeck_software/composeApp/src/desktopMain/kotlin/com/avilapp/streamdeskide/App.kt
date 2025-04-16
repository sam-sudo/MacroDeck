package com.avilapp.streamdeskide

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingConstants

fun App() {
    val exePath = AtomicReference<File?>()
    val tray = SystemTray.getSystemTray()

    // UI
    val frame = JFrame("StreamDesk Launcher")
    val label = JLabel("👉 Arrastra un archivo .exe y luego puedes cerrar esta ventana", SwingConstants.CENTER)

    frame.setSize(500, 150)
    frame.layout = null
    frame.setLocationRelativeTo(null)
    label.setBounds(10, 30, 460, 40)
    frame.add(label)

    // DnD para EXE
    object : DropTarget(frame, object : DropTargetAdapter() {
        override fun drop(dtde: DropTargetDropEvent) {
            dtde.acceptDrop(DnDConstants.ACTION_COPY)
            val transferable = dtde.transferable
            val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
            val file = files.firstOrNull() as? File
            if (file != null && file.extension.lowercase() == "exe") {
                exePath.set(file)
                label.text = "✅ EXE cargado: ${file.name}"
                println("✔️ Ruta guardada: ${file.absolutePath}")
            } else {
                label.text = "❌ Solo se permiten archivos .exe"
            }
        }
    }) {}

    // Bandeja
    val trayIcon = TrayIcon(
        Toolkit.getDefaultToolkit().getImage("C:\\Users\\sam-sudo\\Desktop\\StreamDeckDesktopIDE\\StreamDeckDesktopIDE\\composeApp\\src\\desktopMain\\resources\\ic_app.png"), // puedes poner un icono si quieres
        "StreamDesk IDE"
    )

    val popup = PopupMenu()
    val showItem = MenuItem("Mostrar ventana")
    val exitItem = MenuItem("Salir")

    showItem.addActionListener {
        frame.isVisible = true
    }

    exitItem.addActionListener {
        GlobalScreen.unregisterNativeHook()
        tray.remove(trayIcon)
        System.exit(0)
    }

    popup.add(showItem)
    popup.addSeparator()
    popup.add(exitItem)

    trayIcon.popupMenu = popup
    trayIcon.isImageAutoSize = true

    trayIcon.addMouseListener(object : java.awt.event.MouseAdapter() {
        override fun mouseClicked(e: java.awt.event.MouseEvent) {
            if (e.button == java.awt.event.MouseEvent.BUTTON1) {
                frame.isVisible = true
                frame.state = JFrame.NORMAL
                frame.toFront()
            }
        }
    })

    tray.add(trayIcon)


    // Al cerrar ventana, ocultar pero no cerrar app
    frame.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
    frame.isVisible = true

    // Listener global
    Logger.getLogger(GlobalScreen::class.java.name).level = Level.OFF
    try {
        GlobalScreen.registerNativeHook()
    } catch (e: Exception) {
        println("❌ Error al iniciar hook global: ${e.message}")
        return
    }

    GlobalScreen.addNativeKeyListener(object : NativeKeyListener {
        override fun nativeKeyPressed(e: NativeKeyEvent) {
            if (e.keyCode == 91) {
                println("📥 Tecla 91 detectada.")
                exePath.get()?.let {
                    launchExecutable(it)
                } ?: println("⚠️ No se ha cargado ningún .exe todavía.")
            }
        }

        override fun nativeKeyReleased(e: NativeKeyEvent) {}
        override fun nativeKeyTyped(e: NativeKeyEvent) {}
    })

    println("👂 Escuchando tecla 91 en segundo plano...")
}

fun launchExecutable(file: File) {
    if (!file.exists()) {
        println("❌ El archivo no existe: ${file.absolutePath}")
        return
    }

    try {
        ProcessBuilder(file.absolutePath).start()
        println("✅ Ejecutado: ${file.name}")
    } catch (e: Exception) {
        println("❌ Error al lanzar: ${e.message}")
    }
}