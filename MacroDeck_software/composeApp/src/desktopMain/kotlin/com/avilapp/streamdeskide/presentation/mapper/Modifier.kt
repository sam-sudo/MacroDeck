package com.avilapp.streamdeskide.presentation.mapper

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.awt.datatransfer.DataFlavor
import java.io.File

fun Modifier.onExternalDragAndDrop(onDrop: (File) -> Unit): Modifier = pointerInput(Unit) {
    coroutineScope {
        while (true) {
            awaitPointerEventScope {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Release) {
                    val transferable = java.awt.Toolkit.getDefaultToolkit()
                        .systemClipboard.getContents(null)
                    if (transferable != null &&
                        transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    ) {
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                        val file = files?.firstOrNull() as? File
                        if (file != null) {
                            launch { onDrop(file) }
                        }
                    }
                }
            }
        }
    }
}
