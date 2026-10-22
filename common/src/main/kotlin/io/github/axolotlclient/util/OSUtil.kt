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
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.Locale

object OSUtil {

    private var os: OperatingSystem? = null

    @JvmStatic
    fun getOS(): OperatingSystem = os ?: detectOS().also { os = it }

    private fun detectOS(): OperatingSystem {
        val s = System.getProperty("os.name")
        for (o in OperatingSystem.entries) {
            for (v in o.getStrings()) {
                if (s.trim().lowercase(Locale.ROOT).contains(v)) {
                    return o
                }
            }
        }
        return OperatingSystem.OTHER
    }

    enum class OperatingSystem(private vararg val detectionStrings: String) {
        WINDOWS("win") {
            override fun getURLOpenCommand(url: URL): Array<String> =
                arrayOf("rundll32", "url.dll,FileProtocolHandler", url.toString())
        },
        LINUX("nix", "nux", "aix"),
        MAC("mac") {
            override fun getURLOpenCommand(url: URL): Array<String> = arrayOf("open", url.toString())
        },
        OTHER();

        fun getStrings(): Array<out String> = detectionStrings

        fun open(uri: URI) {
            try {
                this.open(uri.toURL())
            } catch (e: MalformedURLException) {
                AxolotlClientCommon.getInstance().logger.error("Couldn't open uri '{}'", uri, e)
            }
        }

        private fun open(url: URL) {
            try {
                val process = Runtime.getRuntime().exec(this.getURLOpenCommand(url))
                process.inputStream.close()
                process.errorStream.close()
                process.outputStream.close()
            } catch (e: IOException) {
                AxolotlClientCommon.getInstance().logger.error("Couldn't open url '{}'", url, e)
            }
        }

        fun open(uri: String) {
            try {
                this.open(URI(uri))
            } catch (e: IllegalArgumentException) {
                AxolotlClientCommon.getInstance().logger.error("Couldn't open uri '{}'", uri, e)
            } catch (e: URISyntaxException) {
                AxolotlClientCommon.getInstance().logger.error("Couldn't open uri '{}'", uri, e)
            }
        }

        protected open fun getURLOpenCommand(url: URL): Array<String> {
            var string = url.toString()
            if ("file" == url.protocol) {
                string = string.replace("file:", "file://")
            }

            return arrayOf("xdg-open", string)
        }
    }
}
