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

package io.github.axolotlclient.util

import io.github.axolotlclient.AxolotlClientCommon
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

class Watcher(root: Path, private val fileFilter: Predicate<String>) : AutoCloseable {
    private val path: Path = root
    private val watcher: WatchService = root.fileSystem.newWatchService()

    init {
        try {
            watchDir(path)
        } catch (e: Exception) {
            watcher.close()
            throw e
        }
    }

    private fun watchDir(path: Path) {
        path.register(this.watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
    }

    @Throws(IOException::class)
    fun pollForChanges(): Boolean {
        var changed = false

        while (true) {
            val watchKey: WatchKey = watcher.poll() ?: break
            for (watchEvent in watchKey.pollEvents()) {
                if (watchKey.watchable() === path && watchEvent.context() != null) {
                    changed = changed or fileFilter.test((watchEvent.context() as Path).toString())
                }
            }

            watchKey.reset()
        }

        return changed
    }

    @Throws(IOException::class)
    override fun close() {
        this.watcher.close()
    }

    fun safeClose() {
        runCatching { close() }
    }

    companion object {
        private val thread: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

        @JvmStatic
        fun create(path: Path, fileFilter: Predicate<String>): Watcher? =
            try {
                Files.createDirectories(path)
                Watcher(path, fileFilter)
            } catch (e: IOException) {
                AxolotlClientCommon.getInstance().logger.warn("Failed to initialize directory {} monitoring", path, e)
                null
            }

        @JvmStatic
        fun createSelfTicking(path: Path, onUpdate: Runnable): Watcher? = createSelfTicking(path, { _: String -> true }, onUpdate)

        @JvmStatic
        fun createSelfTicking(path: Path, fileFilter: Predicate<String>, onUpdate: Runnable): Watcher? {
            val watcher = create(path, fileFilter)
            if (watcher != null) {
                thread.scheduleAtFixedRate({
                    try {
                        if (watcher.pollForChanges()) {
                            onUpdate.run()
                        }
                    } catch (e: IOException) {
                        watcher.safeClose()
                    }
                }, 100, 100, TimeUnit.MILLISECONDS)
            }
            return watcher
        }

        @JvmStatic
        fun close(watcher: Watcher?) {
            watcher?.safeClose()
        }
    }
}
