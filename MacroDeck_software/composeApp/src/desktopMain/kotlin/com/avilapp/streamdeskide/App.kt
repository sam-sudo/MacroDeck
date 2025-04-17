package com.avilapp.streamdeskide

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.fazecast.jSerialComm.SerialPort
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.logging.Level
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
data class Config(val buttonMap: Map<Int, List<ButtonAction>>)

val configFilePath = Paths.get("button_config.json")
var config = Config(buttonMap = emptyMap())

fun loadConfig() {
    if (Files.exists(configFilePath)) {
        val text = Files.readString(configFilePath)
        config = Json { ignoreUnknownKeys = true }.decodeFromString(text)
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
    frame.setSize(800, 600)
    frame.layout = GridLayout(3, 4, 10, 10)
    frame.setLocationRelativeTo(null)
    frame.background = Color(24, 24, 24)
    frame.contentPane.background = Color(24, 24, 24)

    val buttons = List(12) { JPanel(BorderLayout()) }
    buttons.forEachIndexed { index, panel ->
        val id = 91 + index
        val button = JButton()
        val titleField = JTextField()

        styleButton(button, id, frame)
        titleField.text = ""
        titleField.addActionListener {
            sendTitleToPico(titleField.text, "TITLE${id - 90}")
        }

        val contentPanel = JPanel(BorderLayout())
        contentPanel.add(button, BorderLayout.CENTER)
        contentPanel.add(titleField, BorderLayout.SOUTH)

        val uploadButton = JButton("Subir imagen")
        uploadButton.addActionListener {
            val fileChooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
            }
            if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                val file = fileChooser.selectedFile
                if (file.extension.lowercase() in listOf("png", "jpg", "jpeg")) {
                    val imageRGB = convertToRGB565Enhanced(file)
                    sendIconToPico(imageRGB, "ICON${id - 90}")
                }
            }
        }

        val wrapper = JPanel(BorderLayout())
        wrapper.add(contentPanel, BorderLayout.CENTER)
        wrapper.add(uploadButton, BorderLayout.SOUTH)

        panel.add(wrapper, BorderLayout.CENTER)
        button.addActionListener { showConfigDialog(id) }
        frame.add(panel)
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

    GlobalScreen.registerNativeHook()
    GlobalScreen.addNativeKeyListener(object : NativeKeyListener {
        override fun nativeKeyPressed(e: NativeKeyEvent) {
            println("✅ Ejecutado: ${e.keyCode}")
            val actions = config.buttonMap[e.keyCode] ?: return
            actions.forEach { action ->
                when (action) {
                    is ButtonAction.LaunchExe -> launchExecutable(File(action.path))
                    is ButtonAction.CreateFolders -> action.folders.forEach {
                        File(action.baseDir, it).mkdirs()
                    }
                    is ButtonAction.RunCommand -> ProcessBuilder("cmd", "/c", "start", "", action.command).start()
                }
            }
        }
        override fun nativeKeyReleased(e: NativeKeyEvent) {}
        override fun nativeKeyTyped(e: NativeKeyEvent) {}
    })
}

fun showConfigDialog(buttonId: Int) {
    val panel = JPanel(GridLayout(0, 1))
    val actionList = JTextArea(6, 30)
    actionList.isEditable = false
    val currentActions = config.buttonMap[buttonId]?.toMutableList() ?: mutableListOf()
    actionList.text = currentActions.joinToString("\n") {
        when (it) {
            is ButtonAction.LaunchExe -> "Abrir: ${it.path}"
            is ButtonAction.CreateFolders -> "Crear carpetas en ${it.baseDir}: ${it.folders.joinToString()}"
            is ButtonAction.RunCommand -> "Comando: ${it.command}"
        }
    }

    val addButton = JButton("Añadir acción")
    val removeButton = JButton("Eliminar acción")
    val saveButton = JButton("Guardar")

    var hasChanges = false

    addButton.addActionListener {
        val options = arrayOf("Ejecutar .exe", "Crear carpetas", "Ejecutar comando Win+R")
        val choice = JOptionPane.showOptionDialog(null, "Nueva acción para el botón $buttonId:", "Añadir Acción",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0])

        when (choice) {
            0 -> {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    if (file.extension.lowercase() == "exe" || file.extension.lowercase() == "lnk") {
                        currentActions.add(ButtonAction.LaunchExe(file.absolutePath))
                        actionList.append("\nAbrir: ${file.absolutePath}")
                        hasChanges = true
                    }
                }
            }
            1 -> {
                val base = JOptionPane.showInputDialog("Ruta base:") ?: return@addActionListener
                val names = JOptionPane.showInputDialog("Nombres de carpetas separados por coma:")?.split(',')?.map { it.trim() } ?: return@addActionListener
                currentActions.add(ButtonAction.CreateFolders(base, names))
                actionList.append("\nCrear carpetas en $base: ${names.joinToString()}")
                hasChanges = true
            }
            2 -> {
                val cmd = JOptionPane.showInputDialog("Comando Win+R:") ?: return@addActionListener
                currentActions.add(ButtonAction.RunCommand(cmd))
                actionList.append("\nComando: $cmd")
                hasChanges = true
            }
        }
    }

    removeButton.addActionListener {
        if (currentActions.isEmpty()) return@addActionListener
        val options = currentActions.mapIndexed { i, it ->
            when (it) {
                is ButtonAction.LaunchExe -> "$i - Abrir: ${it.path}"
                is ButtonAction.CreateFolders -> "$i - Crear carpetas en ${it.baseDir}: ${it.folders.joinToString()}"
                is ButtonAction.RunCommand -> "$i - Comando: ${it.command}"
            }
        }.toTypedArray()
        val selected = JOptionPane.showInputDialog(null, "Selecciona acción a eliminar:", "Eliminar Acción",
            JOptionPane.PLAIN_MESSAGE, null, options, options.firstOrNull()) ?: return@addActionListener
        val index = selected.toString().split(" - ").first().toIntOrNull() ?: return@addActionListener
        if (index in currentActions.indices) {
            currentActions.removeAt(index)
            actionList.text = currentActions.joinToString("\n") {
                when (it) {
                    is ButtonAction.LaunchExe -> "Abrir: ${it.path}"
                    is ButtonAction.CreateFolders -> "Crear carpetas en ${it.baseDir}: ${it.folders.joinToString()}"
                    is ButtonAction.RunCommand -> "Comando: ${it.command}"
                }
            }
            hasChanges = true
        }
    }

    saveButton.addActionListener {
        if (hasChanges) {
            config = config.copy(buttonMap = config.buttonMap + (buttonId to currentActions))
            saveConfig()
        }
    }

    panel.add(JLabel("Acciones actuales:"))
    panel.add(JScrollPane(actionList))
    panel.add(addButton)
    panel.add(removeButton)
    panel.add(saveButton)

    JOptionPane.showMessageDialog(null, panel, "Configuración Botón $buttonId", JOptionPane.PLAIN_MESSAGE)
}

fun styleButton(btn: JButton, id: Int, frame: JFrame) {
    btn.text = null
    btn.background = Color(0x1E1E1E)
    btn.border = BorderFactory.createLineBorder(Color.DARK_GRAY, 2)
    btn.isContentAreaFilled = false
    btn.isFocusPainted = false
    btn.horizontalAlignment = SwingConstants.CENTER
    btn.verticalAlignment = SwingConstants.CENTER

    val iconURL = StreamDeskIconLoader::class.java.classLoader.getResource("ic_app.png")
    val rawIcon = ImageIcon(iconURL)
    btn.icon = ImageIcon(rawIcon.image.getScaledInstance(128, 128, Image.SCALE_SMOOTH))

    btn.transferHandler = object : TransferHandler() {
        override fun importData(comp: JComponent, t: Transferable): Boolean {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                val fileList = t.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                val file = fileList.firstOrNull() as? File ?: return false
                val image = ImageIcon(file.absolutePath).image.getScaledInstance(128, 128, Image.SCALE_SMOOTH)
                btn.icon = ImageIcon(image)

                val rgb565 = convertToRGB565Enhanced(file)
                sendIconToPico(rgb565, "ICON${id - 90}")
                return true
            }
            return false
        }
        override fun canImport(support: TransferSupport): Boolean {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        }
    }

    /*btn.addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            if (e.button == MouseEvent.BUTTON3 || e.button == MouseEvent.BUTTON1) {
                showConfigDialog(id)
            }
        }
    })*/
}

fun convertToRGB565Enhanced(file: File): UShortArray {
    val original = ImageIO.read(file)
    val scaled = BufferedImage(90, 90, BufferedImage.TYPE_INT_RGB)
    val g = scaled.createGraphics()
    g.drawImage(original, 0, 0, 90, 90, null)
    g.dispose()

    // Mejora de brillo y contraste manual (simple y efectiva)
    val brightnessFactor = 1.4
    val contrastFactor = 1.3

    return UShortArray(90 * 90) { i ->
        val x = i % 90
        val y = i / 90
        val rgb = scaled.getRGB(x, y)

        var r = (rgb shr 16) and 0xFF
        var g = (rgb shr 8) and 0xFF
        var b = rgb and 0xFF

        // Brillo
        r = (r * brightnessFactor).coerceAtMost(255.0).toInt()
        g = (g * brightnessFactor).coerceAtMost(255.0).toInt()
        b = (b * brightnessFactor).coerceAtMost(255.0).toInt()

        // Contraste
        r = ((r - 128) * contrastFactor + 128).coerceIn(0.0, 255.0).toInt()
        g = ((g - 128) * contrastFactor + 128).coerceIn(0.0, 255.0).toInt()
        b = ((b - 128) * contrastFactor + 128).coerceIn(0.0, 255.0).toInt()

        ((r shr 3) shl 11 or (g shr 2) shl 5 or (b shr 3)).toUShort()
    }
}

fun sendIconToPico(rgb565: UShortArray, iconId: String = "ICON1") {
    val port = findPicoPort() ?: return
    port.baudRate = 115200
    if (!port.openPort()) return
    try {
        val output = port.outputStream
        // Envía cabecera
        output.write("$iconId\n".toByteArray())
        output.flush()

        // Espera para que el Pico se prepare
        Thread.sleep(50)

        rgb565.forEach {
            output.write(it.toInt() and 0xFF)
            output.write((it.toInt() shr 8) and 0xFF)
        }

        // Espera para que el Pico se prepare
        Thread.sleep(50)

        output.write("END\n".toByteArray())
        output.flush()
    } finally {
        port.closePort()
    }
}

fun sendTitleToPico(text: String, titleId: String) {
    val port = findPicoPort() ?: return
    port.baudRate = 115200
    if (!port.openPort()) return
    try {
        val output = port.outputStream
        output.write("$titleId\n".toByteArray())
        output.write("$text\n".toByteArray())
        output.write("END\n".toByteArray())
        output.flush()
        println("✅ Título enviado a $titleId: $text")
    } finally {
        port.closePort()
    }
}

fun findPicoPort(): SerialPort? {
    return SerialPort.getCommPorts().firstOrNull {
        it.descriptivePortName.contains("USB", ignoreCase = true) ||
                it.descriptivePortName.contains("Pico", ignoreCase = true)
    }
}

fun launchExecutable(file: File) {
    if (!file.exists()) return
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
