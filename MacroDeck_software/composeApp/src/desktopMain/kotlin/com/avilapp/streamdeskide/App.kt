package com.avilapp.streamdeskide

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.logging.Level
import java.util.logging.Logger
import javax.imageio.ImageIO
import javax.swing.*

@Serializable
sealed class ButtonAction {
    @Serializable @SerialName("exe")
    data class LaunchExe(val path: String) : ButtonAction()

    @Serializable @SerialName("folders")
    data class CreateFolders(val baseDir: String, val folders: List<String>) : ButtonAction()

    @Serializable @SerialName("command")
    data class RunCommand(val command: String) : ButtonAction()
}

@Serializable
data class Config(val buttonMap: Map<Int, ButtonAction>)

val configFilePath = Paths.get("button_config.json")
var config = Config(buttonMap = emptyMap())

fun loadConfig() {
    if (Files.exists(configFilePath)) {
        val text = Files.readString(configFilePath)
        val json = Json { ignoreUnknownKeys = true }
        config = json.decodeFromString(text)
    }
}

fun saveConfig() {
    val text = Json.encodeToString(config)
    Files.writeString(configFilePath, text)
}

fun App() {
    val tray = SystemTray.getSystemTray()
    loadConfig()

    val frame = JFrame("MacroDeck")
    frame.defaultCloseOperation = JFrame.HIDE_ON_CLOSE
    frame.setSize(600, 400)
    frame.layout = GridLayout(2, 4, 10, 10)
    frame.setLocationRelativeTo(null)
    frame.background = Color(24, 24, 24)
    frame.contentPane.background = Color(24, 24, 24)

    val buttons = List(8) { JButton() }
    buttons.forEachIndexed { index, button ->
        styleButton(button, if (index == 0) 91 else 92, frame)
        if (index == 0 || index == 1) {
            button.addActionListener { showConfigDialog(91 + index) }
        } else {
            button.isEnabled = false
        }
        frame.add(button)
    }


    val iconURL = StreamDeskIconLoader::class.java.classLoader.getResource("ic_app.png")
    val trayIcon = TrayIcon(Toolkit.getDefaultToolkit().getImage(iconURL), "MacroDeck")
    trayIcon.isImageAutoSize = true
    trayIcon.popupMenu = PopupMenu().apply {
        add(MenuItem("Mostrar ventana").apply { addActionListener { frame.isVisible = true } })
        addSeparator()
        add(MenuItem("Salir").apply {
            addActionListener {
                GlobalScreen.unregisterNativeHook()
                tray.remove(trayIcon)
                System.exit(0)
            }
        })
    }
    trayIcon.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
            if (e.button == MouseEvent.BUTTON1) {
                frame.isVisible = true
                frame.toFront()
            }
        }
    })
    tray.add(trayIcon)
    frame.isVisible = true

    Logger.getLogger(GlobalScreen::class.java.name).level = Level.OFF
    GlobalScreen.registerNativeHook()
    GlobalScreen.addNativeKeyListener(object : NativeKeyListener {
        override fun nativeKeyPressed(e: NativeKeyEvent) {
            val action = config.buttonMap[e.keyCode] ?: return
            println("✅ Acción encontrada para keyCode ${e.keyCode}")
            when (action) {
                is ButtonAction.LaunchExe -> launchExecutable(File(action.path))
                is ButtonAction.CreateFolders -> {
                    try {
                        action.folders.forEach { folder ->
                            val dir = File(action.baseDir, folder)
                            if (!dir.exists()) dir.mkdirs()
                        }
                        println("✅ Carpetas creadas")
                    } catch (ex: Exception) {
                        println("❌ Error al crear carpetas: ${ex.message}")
                    }
                }
                is ButtonAction.RunCommand -> {
                    try {
                        ProcessBuilder("cmd", "/c", "start", "", action.command).start()
                        println("✅ Comando ejecutado: ${action.command}")
                    } catch (ex: Exception) {
                        println("❌ Error al ejecutar comando: ${ex.message}")
                    }
                }
            }
        }
        override fun nativeKeyReleased(e: NativeKeyEvent) {}
        override fun nativeKeyTyped(e: NativeKeyEvent) {}
    })
}

fun styleButton(btn: JButton, id: Int, frame: JFrame) {
    btn.text = null
    btn.background = Color(0x1E1E1E)
    btn.border = BorderFactory.createLineBorder(Color.DARK_GRAY, 2)
    btn.isContentAreaFilled = false
    btn.isFocusPainted = false
    btn.horizontalAlignment = SwingConstants.CENTER
    btn.verticalAlignment = SwingConstants.CENTER

    // Imagen por defecto redimensionada
    val iconURL = StreamDeskIconLoader::class.java.classLoader.getResource("ic_app.png")
    val rawIcon = ImageIcon(iconURL)
    val scaledIcon = ImageIcon(rawIcon.image.getScaledInstance(128, 128, Image.SCALE_SMOOTH))
    btn.icon = scaledIcon

    // Drag & drop para cambiar la imagen
    btn.transferHandler = object : TransferHandler() {
        override fun importData(comp: JComponent, t: Transferable): Boolean {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                val fileList = t.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                val file = fileList.firstOrNull() as? File ?: return false
                val image = ImageIcon(file.absolutePath).image.getScaledInstance(128, 128, Image.SCALE_SMOOTH)
                btn.icon = ImageIcon(image)
                // Aquí podrías guardar el path en la config si quieres persistir el icono
                return true
            }
            return false
        }

        override fun canImport(support: TransferSupport): Boolean {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        }
    }

    // Click derecho para configurar
    btn.addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            if (e.button == MouseEvent.BUTTON3) {
                showConfigDialog(id)
            }
        }
    })
}

fun showConfigDialog(buttonId: Int) {
    val dialog = JDialog()
    dialog.title = "Configurar Botón $buttonId"
    dialog.layout = GridLayout(5, 1)
    dialog.setSize(400, 300)
    dialog.setLocationRelativeTo(null)

    val typeSelector = JComboBox(arrayOf("Ejecutar .exe", "Crear carpetas", "Ejecutar comando Win+R"))
    val exeField = JTextField()
    val folderPathField = JTextField()
    val folderNamesField = JTextField()
    val commandField = JTextField()

    val exeDropPanel = JPanel(BorderLayout())
    exeDropPanel.border = BorderFactory.createTitledBorder("Arrastra un .exe o .lnk aquí")
    exeDropPanel.background = Color.LIGHT_GRAY
    exeDropPanel.add(exeField, BorderLayout.CENTER)
    exeDropPanel.transferHandler = object : TransferHandler() {
        override fun importData(support: TransferHandler.TransferSupport): Boolean {
            if (!support.isDrop) return false
            val files = support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return false
            val file = files.firstOrNull() as? File ?: return false
            exeField.text = file.absolutePath
            return true
        }

        override fun canImport(support: TransferHandler.TransferSupport): Boolean {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        }
    }

    val folderPanel = JPanel(GridLayout(2, 1))
    folderPanel.border = BorderFactory.createTitledBorder("Carpetas")
    folderPanel.add(folderPathField)
    folderPanel.add(folderNamesField)

    val commandPanel = JPanel(BorderLayout())
    commandPanel.border = BorderFactory.createTitledBorder("Comando Win+R")
    commandPanel.add(commandField, BorderLayout.CENTER)

    val actionButton = JButton("Guardar")

    dialog.add(typeSelector)
    dialog.add(exeDropPanel)
    dialog.add(folderPanel)
    dialog.add(commandPanel)
    dialog.add(actionButton)

    fun updateVisibility() {
        exeDropPanel.isVisible = typeSelector.selectedIndex == 0
        folderPanel.isVisible = typeSelector.selectedIndex == 1
        commandPanel.isVisible = typeSelector.selectedIndex == 2
    }

    updateVisibility()
    typeSelector.addActionListener { updateVisibility() }

    actionButton.addActionListener {
        when (typeSelector.selectedIndex) {
            0 -> {
                val path = exeField.text
                if (path.isNotBlank()) {
                    config = config.copy(buttonMap = config.buttonMap + (buttonId to ButtonAction.LaunchExe(path)))
                }
            }
            1 -> {
                val baseDir = folderPathField.text
                val folders = folderNamesField.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (baseDir.isNotBlank() && folders.isNotEmpty()) {
                    config = config.copy(buttonMap = config.buttonMap + (buttonId to ButtonAction.CreateFolders(baseDir, folders)))
                }
            }
            2 -> {
                val cmd = commandField.text
                if (cmd.isNotBlank()) {
                    config = config.copy(buttonMap = config.buttonMap + (buttonId to ButtonAction.RunCommand(cmd)))
                }
            }
        }
        saveConfig()
        dialog.dispose()
    }

    dialog.isVisible = true
}

fun launchExecutable(file: File) {
    if (!file.exists()) {
        println("❌ El archivo no existe: ${file.absolutePath}")
        return
    }
    try {
        if (file.extension.lowercase() == "lnk") {
            ProcessBuilder("cmd", "/c", "start", "", "\"${file.absolutePath}\"").start()
        } else {
            ProcessBuilder(file.absolutePath).start()
        }
        println("✅ Ejecutado: ${file.name}")
    } catch (e: Exception) {
        println("❌ Error al lanzar: ${e.message}")
    }
}

class StreamDeskIconLoader
