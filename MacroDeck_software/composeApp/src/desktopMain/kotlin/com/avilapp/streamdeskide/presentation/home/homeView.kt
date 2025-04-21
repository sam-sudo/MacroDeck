package com.avilapp.streamdeskide.presentation.home

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.unit.dp
import com.avilapp.streamdeskide.domain.model.ButtonAction
import com.avilapp.streamdeskide.presentation.mapper.onExternalDragAndDrop
import com.fazecast.jSerialComm.SerialPort
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

@Composable
@Preview
fun HomeView(viewModel: HomeViewModel) {
    val state = viewModel.state

    var showDeleteDialog by remember { mutableStateOf(false) }
    val moveOrigin = state.moveOrigin

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            DeckGrid(
                viewModel = viewModel,
                rows = 3,
                columns = 4,
                state = state,
                moveOrigin = moveOrigin,
                isInMoveMode = state.isInMoveMode,
                onClick = {
                    println("seleccionado $it")
                    if (state.isInMoveMode && moveOrigin != it) {
                        viewModel.completeMove(it)
                        viewModel.selectButton(it) // Asegura selección tras mover
                    } else {
                        viewModel.selectButton(it)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    showDeleteDialog = true
                }) {
                    Text("Delete")
                }
                Button(
                    onClick = {
                        if (!state.isInMoveMode) viewModel.startMove()
                        else viewModel.cancelMove()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isInMoveMode) Color(0xFF4444FF) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (state.isInMoveMode) "Cancel Move" else "Move")
                }
            }

            if (showDeleteDialog) {
                val selectedKey = state.selectedButton
                val actions = selectedKey?.let { state.buttons[it]?.actions } ?: emptyList()

                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Eliminar acción") },
                    text = {
                        if (actions.isEmpty()) {
                            Text("Este botón no tiene acciones asignadas.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                actions.forEachIndexed { index, action ->
                                    val label = when (action) {
                                        is ButtonAction.LaunchExe -> "Abrir: ${action.path}"
                                        is ButtonAction.RunCommand -> "Comando: ${action.command}"
                                        is ButtonAction.CreateFolders -> "Carpetas: ${action.folders.joinToString()} en ${action.baseDir}"
                                    }
                                    Button(
                                        onClick = {
                                            selectedKey?.let {
                                                viewModel.removeActionAt(it, index)
                                                showDeleteDialog = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(label)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cerrar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

        }

        Spacer(modifier = Modifier.width(24.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("Presets", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))
            ActionList(
                listOf(
                    "Ejecutar programa",
                    "Ejecutar comando",
                    "Crear estructura de carpetas"
                ),
                onClick = { action ->
                    state.selectedButton?.let {
                        viewModel.addAction(it, action)
                    }
                }
            )
        }
    }
}


@Composable
fun DeckGrid(
    viewModel: HomeViewModel,
    rows: Int,
    columns: Int,
    state: HomeState,
    moveOrigin: Pair<Int, Int>?,
    isInMoveMode: Boolean,
    onClick: (Pair<Int, Int>) -> Unit
) {

    Box(
        modifier = Modifier
            .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(16.dp))
            .padding(24.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(columns) { col ->
                        val key = Pair(row, col)
                        val button = state.buttons[key]

                        DeckButton(
                            isSelected = state.selectedButton == key,
                            isMoveOrigin = moveOrigin == key,
                            isInMoveMode = isInMoveMode,
                            title = button?.title ?: "",
                            image = button?.image,
                            onClick = {onClick(key)},
                            onImageDropped = { image ->
                                viewModel.setImage(key, image)
                                image?.let { viewModel.sendImageToPico(it) }
                            },
                            onRequestDeleteImage = {
                                viewModel.setImage(key, null)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DeckButton(
    isSelected: Boolean,
    isMoveOrigin: Boolean,
    isInMoveMode: Boolean,
    title: String,
    image: BufferedImage?,
    onClick: () -> Unit,
    onImageDropped: (BufferedImage?) -> Unit,
    onRequestDeleteImage: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }

    if (showContextMenu) {
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = { Text("Opciones de imagen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val dialog = FileDialog(null as Frame?, "Seleccionar imagen")
                            dialog.isVisible = true
                            dialog.files.firstOrNull()?.let { file ->
                                val img = ImageIO.read(file) as BufferedImage
                                onImageDropped(img)
                                sendIconToPico(convertToRGB565Enhanced(file), "ICON1")
                            }
                            showContextMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cambiar imagen")
                    }

                    Button(
                        onClick = {
                            onRequestDeleteImage()
                            showContextMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Eliminar imagen")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContextMenu = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .background(Color.Black, shape = RoundedCornerShape(6.dp))
            .border(
                width = when {
                    isMoveOrigin -> 3.dp
                    isSelected -> 2.dp
                    isHovered -> 1.dp
                    else -> 0.dp
                },
                color = when {
                    isMoveOrigin -> Color(0xFF4488FF)
                    isSelected -> Color.White
                    isHovered -> if (isInMoveMode) Color(0xFF8888FF) else Color.White
                    else -> Color.Transparent
                })
                .pointerMoveFilter(
                onEnter = {
                    isHovered = true
                    false
                },
                onExit = {
                    isHovered = false
                    false
                }
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val buttons = event.buttons

                        if (buttons.isPrimaryPressed) {
                            onClick()
                        }

                        if (buttons.isSecondaryPressed && !isInMoveMode) {
                            showContextMenu = true
                        }
                    }
                }
            }
            .onExternalDragAndDrop(
                onDrop = { file ->
                    if (!isInMoveMode && file.extension.lowercase() in listOf("png", "jpg", "jpeg")) {
                        val img = ImageIO.read(file) as BufferedImage
                        onImageDropped(img)
                        sendIconToPico(convertToRGB565Enhanced(file), "ICON1")
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        image?.let {
            androidx.compose.foundation.Image(
                bitmap = it.toComposeImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp)
            )
        } ?: Text(title, color = Color.White)
    }
}


fun convertToRGB565Enhanced(file: File): UShortArray {
    val original = ImageIO.read(file)
    val scaled = BufferedImage(90, 90, BufferedImage.TYPE_INT_RGB)
    val g = scaled.createGraphics()
    g.drawImage(original, 0, 0, 90, 90, null)
    g.dispose()

    val brightnessFactor = 1.4
    val contrastFactor = 1.3

    return UShortArray(90 * 90) { i ->
        val x = i % 90
        val y = i / 90
        val rgb = scaled.getRGB(x, y)

        var r = (rgb shr 16) and 0xFF
        var g = (rgb shr 8) and 0xFF
        var b = rgb and 0xFF

        r = (r * brightnessFactor).coerceAtMost(255.0).toInt()
        g = (g * brightnessFactor).coerceAtMost(255.0).toInt()
        b = (b * brightnessFactor).coerceAtMost(255.0).toInt()

        r = ((r - 128) * contrastFactor + 128).coerceIn(0.0, 255.0).toInt()
        g = ((g - 128) * contrastFactor + 128).coerceIn(0.0, 255.0).toInt()
        b = ((b - 128) * contrastFactor + 128).coerceIn(0.0, 255.0).toInt()

        ((r shr 3) shl 11 or (g shr 2) shl 5 or (b shr 3)).toUShort()
    }
}

@Composable
fun ActionList(actions: List<String>, onClick: (ButtonAction) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(actions) { label ->
            Button(onClick = {
                when (label) {
                    "Ejecutar programa" -> {
                        val dialog = FileDialog(null as Frame?, "Seleccionar ejecutable")
                        dialog.isVisible = true
                        val file = dialog.files.firstOrNull()
                        if (file != null) {
                            onClick(ButtonAction.LaunchExe(file.absolutePath))
                        }
                    }

                    "Ejecutar comando" -> {
                        onClick(ButtonAction.RunCommand("calc"))
                    }

                    "Crear estructura de carpetas" -> {
                        onClick(ButtonAction.CreateFolders("C:/temp", listOf("A", "B")))
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(label)
            }
        }
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

fun findPicoPort(): SerialPort? {
    return SerialPort.getCommPorts().firstOrNull {
        it.descriptivePortName.contains("USB", ignoreCase = true) ||
                it.descriptivePortName.contains("Pico", ignoreCase = true)
    }
}
