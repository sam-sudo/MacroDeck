package com.avilapp.streamdeskide.data.model

import com.avilapp.streamdeskide.domain.model.ButtonAction
import java.awt.Image
import java.awt.image.BufferedImage

data class ButtonState(
    var title: String = "",
    var image: BufferedImage? = null,
    val actions: MutableList<ButtonAction> = mutableListOf()
)