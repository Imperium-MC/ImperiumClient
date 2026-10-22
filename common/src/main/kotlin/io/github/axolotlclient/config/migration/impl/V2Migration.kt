/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.config.migration.impl

import com.google.gson.JsonObject
import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption
import io.github.axolotlclient.config.migration.ConfigMigration

class V2Migration : ConfigMigration {
    override fun version() = 2

    override fun apply(config: JsonObject) {
        getObject(config, "hud").ifPresent { hud ->
            getObject(hud, "keystrokehud").ifPresent { keystrokes ->
                val mousemovement = JsonObject()
                mousemovement.addProperty(
                    "enabled",
                    keystrokes.get("enabled").asBoolean && keystrokes.get("mousemovement").asBoolean
                )
                convertImageData(keystrokes, mousemovement, "mouseMovementIndicator")
                convertImageData(keystrokes, mousemovement, "mouseMovementIndicatorOuter")
                hud.add("mousemovementhud", mousemovement)
            }
            getObject(hud, "crosshairhud").ifPresent { crosshair ->
                convertImageData(crosshair, crosshair, "customTextureGraphics")
            }
        }
    }

    private fun convertImageData(inObj: JsonObject, outObj: JsonObject, name: String) {
        val graphics = inObj.get(name)
        if (graphics != null && graphics.isJsonObject) {
            val data = graphics.asJsonObject.get("data").asJsonArray
            val pix: Array<IntArray> = data.map { row ->
                row.asJsonArray.map { it.asInt }.toIntArray()
            }.toTypedArray()
            // hacky
            val s = GraphicsOption("", pix).toSerializedValue()
            outObj.addProperty(name, s)
        } else if (graphics != null) {
            outObj.addProperty(name, graphics.asString)
        }
    }
}
