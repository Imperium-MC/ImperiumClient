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

package io.github.axolotlclient.util

import com.mojang.blaze3d.platform.NativeImage
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.util.Graphics
import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.StringUtil
import org.apache.commons.lang3.StringUtils
import java.io.IOException
import java.util.Locale

object Util {
    private val textures: MutableMap<Identifier, DynamicTexture> = HashMap()

    @JvmStatic
    fun sendChatMessage(msg: String) {
        val message = StringUtil.trimChatMessage(StringUtils.normalizeSpace(msg.trim()))
        val connection = Minecraft.getInstance().player!!.connection
        if (message.startsWith("/")) {
            connection.sendCommand(message.substring(1))
        } else {
            connection.sendChat(message)
        }
    }

    @JvmStatic
    fun addMessageToChatHud(msg: Component) {
        Minecraft.getInstance().gui.hud.chat.addClientSystemMessage(msg)
    }

    @JvmStatic
    fun getTexture(option: GraphicsOption): Identifier {
        return getTexture(option.get(), option.name)
    }

    @JvmStatic
    fun getTexture(graphics: Graphics, name: String): Identifier {
        val id = Identifier.fromNamespaceAndPath(
            AxolotlClientCommon.MODID, "graphics_" + name.lowercase(Locale.ROOT)
        )
        try {
            val texture: DynamicTexture
            var reuse = textures.containsKey(id)
            if (reuse) {
                val img = textures[id]
                reuse = img!!.pixels.width == graphics.width && img.pixels.height == graphics.height
            }
            if (!reuse) {
                texture = DynamicTexture(id::toString, NativeImage.read(graphics.pixelData))
                textures.put(id, texture)?.close()
                Minecraft.getInstance().textureManager.register(id, texture)
            } else {
                texture = textures[id]!!
                for (x in 0 until graphics.width) {
                    for (y in 0 until graphics.height) {
                        texture.pixels.setPixel(x, y, graphics.getPixelColor(x, y))
                    }
                }
            }

            texture.upload()
        } catch (e: IOException) {
            AxolotlClientCommon.getInstance().logger.error("Failed to bind texture for $name: ", e)
        }
        return id
    }
}
