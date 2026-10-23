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

package io.github.axolotlclient.modules.auth.skin

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentSkipListSet

import com.google.common.hash.Hashing
import com.mojang.blaze3d.platform.NativeImage
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.mixin.skins.SkinTextureDownloaderAccessor
import io.github.axolotlclient.util.ClientColors
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier

class SkinManager {

    private val loadedTextures = ConcurrentSkipListSet<AxoIdentifier>(compareBy { obj: AxoIdentifier -> obj.toString() })

    fun read(p: java.nio.file.Path): Skin? = read(p, true)

    fun read(p: java.nio.file.Path, fix: Boolean): Skin? {
        var slim: Boolean
        val sha256: String
        try {
            val input = Files.readAllBytes(p)
            sha256 = Hashing.sha256().hashBytes(input).toString()
            NativeImage.read(input).use { img ->
                val width = img.getWidth()
                val height = img.getHeight()
                if (width != 64) return null
                if (height == 32) {
                    if (fix) {
                        SkinTextureDownloaderAccessor.invokeProcessLegacySkin(img, "local").use { img2 ->
                            img2.writeToFile(p)
                        }
                    }
                    slim = false
                } else if (height != 64) {
                    return null
                } else {
                    slim = ClientColors.ARGB.alpha(img.getPixel(50, 16)) == 0
                }
                val metadata = Skin.LocalSkin.readMetadata(p)
                if (metadata != null && metadata.containsKey(Skin.LocalSkin.CLASSIC_METADATA_KEY)) {
                    slim = !(metadata[Skin.LocalSkin.CLASSIC_METADATA_KEY] as Boolean)
                }
            }
            return Skin.LocalSkin(!slim, p, input, sha256)
        } catch (e: Exception) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to probe skin: ", e)
        }
        return null
    }

    fun loadSkin(skin: Skin): AxoIdentifier {
        val rl = AxoIdentifier.of(AxolotlClientCommon.MODID, "skins/" + skin.sha256())
        if (loadedTextures.contains(rl)) {
            return rl
        }

        try {
            val tex = DynamicTexture({ rl.toString() }, NativeImage.read(skin.image()))
            Minecraft.getInstance().textureManager.register(rl as Identifier, tex)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        loadedTextures.add(rl)
        return rl
    }

    fun loadCape(cape: Cape): AxoIdentifier {
        val rl = AxoIdentifier.of(AxolotlClientCommon.MODID, "capes/" + cape.id())
        if (loadedTextures.contains(rl)) {
            return rl
        }

        try {
            val tex = DynamicTexture({ rl.toString() }, NativeImage.read(cape.image()))
            Minecraft.getInstance().textureManager.register(rl as Identifier, tex)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
        loadedTextures.add(rl)
        return rl
    }

    fun releaseAll() {
        loadedTextures.forEach { id -> Minecraft.getInstance().textureManager.release(id as Identifier) }
        loadedTextures.clear()
    }
}
