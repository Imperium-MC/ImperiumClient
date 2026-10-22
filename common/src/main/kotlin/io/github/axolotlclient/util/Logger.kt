/*
 * Copyright © 2023 moehreag <moehreag@gmail.com> & Contributors
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
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

sealed interface Logger {
    fun info(msg: String, vararg args: Any?)

    fun warn(msg: String, vararg args: Any?)

    fun error(msg: String, vararg args: Any?)

    fun debug(msg: String, vararg args: Any?)

    class Slf4jLogger : Logger {
        private val delegate: org.slf4j.Logger = LoggerFactory.getLogger("ImperiumClient")

        override fun info(msg: String, vararg args: Any?) {
            delegate.info(prefix + msg, *args)
        }

        override fun warn(msg: String, vararg args: Any?) {
            delegate.warn(prefix + msg, *args)
        }

        override fun error(msg: String, vararg args: Any?) {
            delegate.error(prefix + msg, *args)
        }

        override fun debug(msg: String, vararg args: Any?) {
            if (AxolotlClientCommon.getInstance().getConfig().debugLogOutput.get()) {
                delegate.info(prefix + "[DEBUG] " + msg, *args)
            }
        }

        companion object {
            private val prefix = if (FabricLoader.getInstance().isDevelopmentEnvironment) "" else "(AxolotlClient) "
        }
    }
}
