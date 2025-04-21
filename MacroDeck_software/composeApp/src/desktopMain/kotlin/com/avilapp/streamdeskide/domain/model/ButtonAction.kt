package com.avilapp.streamdeskide.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ButtonAction {
    @Serializable @SerialName("exe")
    data class LaunchExe(val path: String) : ButtonAction()

    @Serializable @SerialName("folders")
    data class CreateFolders(val baseDir: String, val folders: List<String>) : ButtonAction()

    @Serializable @SerialName("command")
    data class RunCommand(val command: String) : ButtonAction()
}