package com.avilapp.streamdeskide.presentation.home

import com.avilapp.streamdeskide.data.model.ButtonState

data class HomeState(
    val buttons: Map<Pair<Int, Int>, ButtonState> = emptyMap(),
    val selectedButton: Pair<Int, Int>? = null,
    val isInMoveMode: Boolean = false,
    val moveOrigin: Pair<Int, Int>? = null
)
