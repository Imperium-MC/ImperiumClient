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

import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.CompletableFuture

class TFDImpl : DialogProvider {
    override fun save(title: String, origin: Path, filter: String?): CompletableFuture<Optional<Path>> =
        CompletableFuture.supplyAsync {
            MemoryStack.stackPush().use { stack ->
                val pointers = stack.mallocPointer(1)
                pointers.put(stack.UTF8("*$filter"))
                pointers.flip()
                val file = TinyFileDialogs.tinyfd_saveFileDialog(title, origin.toString(), pointers, null)
                Optional.ofNullable(file).map { Path.of(it) }
            }
        }

    override fun open(title: String, origin: Path, filter: String?): CompletableFuture<Optional<Path>> =
        CompletableFuture.supplyAsync {
            MemoryStack.stackPush().use { stack ->
                val pointers = stack.mallocPointer(1)
                pointers.put(stack.UTF8("*$filter"))
                pointers.flip()
                val file = TinyFileDialogs.tinyfd_openFileDialog(title, origin.toString(), pointers, null, false)
                Optional.ofNullable(file).map { Path.of(it) }
            }
        }

    override fun openMultiple(title: String, origin: Path, filter: String?): CompletableFuture<Optional<List<Path>>> =
        CompletableFuture.supplyAsync {
            MemoryStack.stackPush().use { stack ->
                val pointers = stack.mallocPointer(1)
                pointers.put(stack.UTF8("*$filter"))
                pointers.flip()
                val files = TinyFileDialogs.tinyfd_openFileDialog(title, origin.toString(), pointers, null, true)
                Optional.ofNullable(files).map { s -> s.split("\\|").map { Path.of(it) } }
            }
        }
}
