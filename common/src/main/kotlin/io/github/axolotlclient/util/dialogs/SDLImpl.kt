/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util.dialogs

import io.github.axolotlclient.AxolotlClientCommon
import org.lwjgl.sdl.SDLDialog
import org.lwjgl.sdl.SDLError
import org.lwjgl.sdl.SDL_DialogFileCallbackI
import org.lwjgl.sdl.SDL_DialogFileFilter
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.CompletableFuture

class SDLImpl : DialogProvider {

    override fun save(title: String, origin: Path, filter: String?): CompletableFuture<Optional<Path>> {
        val future = CompletableFuture<Optional<Path>>()
        MemoryStack.stackPush().use { stack ->
            val filters = SDL_DialogFileFilter.create(if (filter != null) 1 else 0)
            if (filter != null) {
                filters.name(stack.UTF8("*$filter"))
                    .pattern(stack.UTF8(if (filter[0] == '.') filter.substring(1) else filter))
            }
            SDLDialog.SDL_ShowSaveFileDialog(
                SDL_DialogFileCallbackI { _: Long, filelist: Long, _: Int ->
                    if (filelist == 0L) {
                        future.complete(Optional.empty())
                        return@SDL_DialogFileCallbackI
                    }
                    val deref = MemoryUtil.memGetAddress(filelist)
                    if (deref == 0L) {
                        future.complete(Optional.empty())
                        return@SDL_DialogFileCallbackI
                    }

                    val str = MemoryUtil.memUTF8(deref)
                    future.complete(Optional.of(Path.of(str)))
                },
                0L,
                0L,
                filters,
                origin.toString()
            )
        }
        return future
    }

    override fun open(title: String, origin: Path, filter: String?): CompletableFuture<Optional<Path>> {
        val future = CompletableFuture<Optional<Path>>()
        MemoryStack.stackPush().use { stack ->
            val filters = SDL_DialogFileFilter.create(if (filter != null) 1 else 0)
            if (filter != null) {
                filters.name(stack.UTF8("*$filter"))
                    .pattern(stack.UTF8(if (filter[0] == '.') filter.substring(1) else filter))
            }
            SDLDialog.SDL_ShowOpenFileDialog(
                SDL_DialogFileCallbackI { _: Long, filelist: Long, _: Int ->
                    if (filelist == 0L) {
                        future.complete(Optional.empty())
                        return@SDL_DialogFileCallbackI
                    }
                    val deref = MemoryUtil.memGetAddress(filelist)
                    if (deref == 0L) {
                        future.complete(Optional.empty())
                        return@SDL_DialogFileCallbackI
                    }

                    val str = MemoryUtil.memUTF8(deref)
                    future.complete(Optional.of(Path.of(str)))
                },
                0L,
                0L,
                filters,
                origin.toString(),
                false
            )
        }
        return future
    }

    override fun openMultiple(title: String, origin: Path, filter: String?): CompletableFuture<Optional<List<Path>>> {
        val future = CompletableFuture<Optional<List<Path>>>()
        MemoryStack.stackPush().use { stack ->
            val filters = SDL_DialogFileFilter.create(if (filter != null) 1 else 0)
            if (filter != null) {
                filters.name(stack.UTF8("*$filter"))
                    .pattern(stack.UTF8(if (filter[0] == '.') filter.substring(1) else filter))
            }
            SDLDialog.SDL_ShowOpenFileDialog(
                SDL_DialogFileCallbackI { _: Long, filelist: Long, _: Int ->
                    if (filelist == 0L) {
                        AxolotlClientCommon.getInstance().logger.warn(SDLError.SDL_GetError() ?: "")
                        future.complete(Optional.empty())
                        return@SDL_DialogFileCallbackI
                    }
                    val deref = MemoryUtil.memGetAddress(filelist)
                    if (deref == 0L) {
                        future.complete(Optional.empty())
                        return@SDL_DialogFileCallbackI
                    }

                    val str = MemoryUtil.memUTF8(deref)
                    future.complete(Optional.of(str.split("\u0000").map { Path.of(it) }))
                },
                0L,
                0L,
                filters,
                origin.toString(),
                true
            )
        }
        return future
    }
}
