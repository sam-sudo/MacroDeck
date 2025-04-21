package com.avilapp.streamdeskide.data.repository

import com.avilapp.streamdeskide.domain.model.ButtonAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Paths

private val configFilePath = Paths.get("button_config.json")

@Serializable
data class ButtonConfig(val buttonMap: Map<String, List<ButtonAction>>)

class ConfigRepositoryImpl {

    fun save(map: Map<Pair<Int, Int>, List<ButtonAction>>) {
        val serializableMap = map.mapKeys { "${it.key.first},${it.key.second}" }
        val config = ButtonConfig(serializableMap)
        val text = Json.encodeToString(config)
        Files.writeString(configFilePath, text)
    }

    fun load(): Map<Pair<Int, Int>, List<ButtonAction>> {
        if (!Files.exists(configFilePath)) return emptyMap()
        val text = Files.readString(configFilePath)
        val config = Json.decodeFromString<ButtonConfig>(text)
        return config.buttonMap.mapNotNull { (k, v) ->
            val parts = k.split(',')
            if (parts.size == 2) {
                val row = parts[0].toIntOrNull()
                val col = parts[1].toIntOrNull()
                if (row != null && col != null) {
                    Pair(row, col) to v
                } else null
            } else null
        }.toMap()
    }

}