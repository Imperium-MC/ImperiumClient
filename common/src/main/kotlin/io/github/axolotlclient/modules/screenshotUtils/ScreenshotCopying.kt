/*
 * Copyright © 2024 moehreag <moehreag@gmail.com> & Contributors
 *
 * This file is part of AxolotlClient.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * For more information, see the LICENSE file.
 */

package io.github.axolotlclient.modules.screenshotUtils

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

import io.github.axolotlclient.AxolotlClientCommon
import org.lwjgl.glfw.GLFW
import org.lwjgl.sdl.SDLClipboard
import org.lwjgl.sdl.SDL_ClipboardCleanupCallback
import org.lwjgl.sdl.SDL_ClipboardDataCallback
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

object ScreenshotCopying {
    private val SDL_AVAILABLE: Boolean
    private val IS_WAYLAND: Boolean

    init {
        var sdl = false
        try {
            Class.forName("org.lwjgl.sdl.SDL")
            sdl = true
        } catch (_: Throwable) {
        }
        SDL_AVAILABLE = sdl
        var wayland = false
        if (!sdl) {
            try {
                wayland = GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND ||
                    System.getenv("WAYLAND_DISPLAY") != null
            } catch (_: Throwable) {
            }
        }
        IS_WAYLAND = wayland
    }

    @JvmStatic
    fun copy(file: Path) {
        if (!SDL_AVAILABLE || !copySdl(file)) {
            if (IS_WAYLAND) {
                copyWayland(file)
            } else {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(FileTransferable(file.toFile()), null)
            }
        }
    }

    private fun copyWayland(f: Path) {
        try {
            val builder = ProcessBuilder("bash", "-c", "wl-copy -t image/png < '" + f.toAbsolutePath() + "'")
            val p = builder.start()
            p.waitFor()
        } catch (_: Exception) {
            AxolotlClientCommon.getInstance().logger
                .error("Failed to invoke 'wl-copy'!\nMake sure 'wl-clipboard' is installed and accessible!")
        }
    }

    @JvmStatic
    fun copy(image: ByteArray) {
        if (!SDL_AVAILABLE || !SDLFence.copySdl(image)) {
            if (IS_WAYLAND) {
                try {
                    val i = Files.createTempFile("axolotlclient_screenshot", ".png")
                    Files.write(i, image)
                    copyWayland(i)
                    Files.delete(i)
                } catch (_: IOException) {
                    AxolotlClientCommon.getInstance().logger.error("Failed to copy image using temporary file!")
                }
            } else {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(image), null)
            }
        }
    }

    private fun copySdl(p: Path): Boolean =
        try {
            SDLFence.copySdl(Files.readAllBytes(p))
        } catch (e: IOException) {
            AxolotlClientCommon.getInstance().logger.error("Failed to read screenshot!", e)
            false
        }

    // Classloading fence...
    private object SDLFence {
        fun copySdl(data: ByteArray): Boolean =
            MemoryStack.stackPush().use { stack ->
                val mimetypesBuf = stack.UTF8("image/png")
                val mimetypes = stack.pointers(mimetypesBuf)
                val dataBuf = ByteBuffer.allocateDirect(data.size).put(data).flip()
                val pointer = MemoryUtil.memAddress(dataBuf)
                SDLClipboard.SDL_SetClipboardData(
                    SDL_ClipboardDataCallback.create { userdata: Long, mime_type: Long, size: Long ->
                        val sizeBuf = MemoryUtil.memLongBuffer(size, 8)
                        sizeBuf.put(0, data.size.toLong())
                        userdata
                    },
                    SDL_ClipboardCleanupCallback.create { _: Long -> MemoryUtil.memFree(dataBuf) }, pointer, mimetypes
                )
            }
    }

    class ImageTransferable(val image: ByteArray) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = DataFlavor.imageFlavor == flavor

        @Throws(IOException::class)
        override fun getTransferData(flavor: DataFlavor): Any =
            ImageIO.read(ByteArrayInputStream(image))
    }

    class FileTransferable(val file: File) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = DataFlavor.javaFileListFlavor == flavor

        override fun getTransferData(flavor: DataFlavor): Any {
            val files = ArrayList<File>()
            files.add(file)
            return files
        }
    }
}
