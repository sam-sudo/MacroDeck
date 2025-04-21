package com.avilapp.streamdeskide.presentation.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.avilapp.streamdeskide.data.model.ButtonState
import com.avilapp.streamdeskide.data.repository.ConfigRepositoryImpl
import com.avilapp.streamdeskide.domain.model.ButtonAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Image
import java.awt.image.BufferedImage

class HomeViewModel(
    private val repository: ConfigRepositoryImpl = ConfigRepositoryImpl()
) {

    companion object {
        private const val ROWS = 3
        private const val COLUMNS = 4
    }

    var state by mutableStateOf(
        HomeState(
            buttons = mutableMapOf<Pair<Int, Int>, ButtonState>().apply {
                for (row in 0 until ROWS) {
                    for (col in 0 until COLUMNS) {
                        this[Pair(row, col)] = ButtonState()
                    }
                }

                // Cargar datos guardados y sobrescribir si hay algo
                val saved = repository.load()
                saved.forEach { (key, actions) ->
                    this[key] = ButtonState(actions = actions.toMutableList())
                }
            }

        )
    )

    fun selectButton(key: Pair<Int, Int>) {
        val currentMap = state.buttons.toMutableMap()
        if (!currentMap.containsKey(key)) {
            println("⛔ El botón $key no existe. Inicializando nuevo.")
            currentMap[key] = ButtonState()
        }
        state = state.copy(buttons = currentMap, selectedButton = key)
    }


    fun clearButton(key: Pair<Int, Int>) {
        val newMap = state.buttons.toMutableMap()
        newMap.remove(key)
        state = state.copy(buttons = newMap)
        save()
    }

    fun addAction(key: Pair<Int, Int>, action: ButtonAction) {
        val newMap = state.buttons.toMutableMap()
        val current = newMap.getOrPut(key) { ButtonState() }
        current.actions.add(action)
        newMap[key] = current
        state = state.copy(buttons = newMap)
        save()
    }

    fun setImage(key: Pair<Int, Int>, image: BufferedImage?) {
        val newMap = state.buttons.toMutableMap()
        val current = newMap.getOrPut(key) { ButtonState() }

        val updated = current.copy(image = image)

        newMap[key] = updated
        state = state.copy(buttons = newMap)
    }

    fun sendImageToPico(image: BufferedImage) {
        CoroutineScope(Dispatchers.IO).launch {
            sendIconToPico(convertToRGB565Enhanced(image), "ICON1")
        }
    }


    fun convertToRGB565Enhanced(image: BufferedImage): UShortArray {
        val scaled = BufferedImage(90, 90, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        g.drawImage(image, 0, 0, 90, 90, null)
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


    fun setTitle(key: Pair<Int, Int>, title: String) {
        val newMap = state.buttons.toMutableMap()
        val current = newMap.getOrPut(key) { ButtonState() }
        current.title = title
        newMap[key] = current
        state = state.copy(buttons = newMap)
    }

    fun removeActionAt(key: Pair<Int, Int>, index: Int) {
        val newMap = state.buttons.toMutableMap()
        val current = newMap[key]
        if (current != null && index in current.actions.indices) {
            current.actions.removeAt(index)
            newMap[key] = current
            state = state.copy(buttons = newMap)
            save()
        }
    }

    fun onKeyPressed(code: Int) {
        val buttonKey = Pair(0, code - 91) // O la forma en que tú mapees HID -> botón
        val actions = state.buttons[buttonKey]?.actions ?: return

        println("🎬 Ejecutando acciones de botón físico $buttonKey")
        actions.forEach { executeAction(it) }
    }

    fun startMove() {
        state.selectedButton?.let {
            state = state.copy(moveOrigin = it, isInMoveMode = true)
        }
    }

    fun completeMove(target: Pair<Int, Int>) {
        val from = state.moveOrigin ?: return
        if (from == target) return

        val newMap = state.buttons.toMutableMap()
        val data = newMap[from] ?: return

        // Crear una copia profunda para forzar recomposición en Compose
        val newData = data.copy(
            actions = data.actions.toMutableList(),
            image = data.image,
            title = data.title
        )

        newMap[target] = newData
        newMap.remove(from)

        // Asegurar que todos los botones estén presentes en el mapa
        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) {
                val key = Pair(row, col)
                if (!newMap.containsKey(key)) {
                    newMap[key] = ButtonState()
                }
            }
        }

        state = state.copy(
            buttons = newMap,
            selectedButton = target,
            moveOrigin = null,
            isInMoveMode = false
        )

        println("✅ Movimiento completado: $from → $target")
    }


    fun cancelMove() {
        state = state.copy(moveOrigin = null,isInMoveMode = false)
    }


    private fun save() {
        repository.save(state.buttons.mapValues { it.value.actions })
    }

    private fun executeAction(action: ButtonAction) {
        when (action) {
            is ButtonAction.LaunchExe -> launchExecutable(action.path)
            is ButtonAction.RunCommand -> ProcessBuilder("cmd", "/c", "start", "", action.command).start()
            is ButtonAction.CreateFolders -> action.folders.forEach {
                java.io.File(action.baseDir, it).mkdirs()
            }
        }
    }

    private fun launchExecutable(path: String) {
        try {
            println("🚀 Ejecutando $path")
            if (path.endsWith(".lnk")) {
                ProcessBuilder("cmd", "/c", "start", "", "\"$path\"").start()
            } else {
                ProcessBuilder(path).start()
            }
        } catch (e: Exception) {
            println("❌ Error al ejecutar: ${e.message}")
        }
    }


}
