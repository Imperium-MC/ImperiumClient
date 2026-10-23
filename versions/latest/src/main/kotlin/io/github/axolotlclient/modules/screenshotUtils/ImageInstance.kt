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

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale

import com.google.common.hash.Hashing
import com.mojang.blaze3d.platform.NativeImage
import io.github.axolotlclient.AxolotlClientCommon
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier

interface ImageInstance {

    val id: Identifier

    val image: NativeImage

    val filename: String

    interface Local : ImageInstance {
        val location: Path

        fun toShared(url: String, uploader: String, sharedAt: Instant): ImageInstance =
            SharedImpl(id, image, filename, location, url, uploader, sharedAt)
    }

    interface Remote : ImageInstance {
        val url: String

        // nullable: the API download body omits uploader/shared_at when absent (ImageData carries Instant?/String?)
        val uploader: String?

        val sharedAt: Instant?

        fun toShared(saved: Path): ImageInstance = SharedImpl(id, image, filename, saved, url, uploader, sharedAt)
    }

    @JvmRecord
    data class LocalImpl(
        override val id: Identifier, override val image: NativeImage, override val filename: String,
        override val location: Path
    ) : Local {
        constructor(image: NativeImage, filename: String, location: Path) : this(
            Identifier.fromNamespaceAndPath(
                AxolotlClientCommon.MODID,
                "gallery_local_" + Hashing.sha256().hashUnencodedChars(
                    location.toString().lowercase(Locale.ROOT).replace(Regex("[./]"), "_")
                )
            ), image, filename, location
        ) {
            register(id, image)
        }

        constructor(p: Path) : this(NativeImage.read(Files.newInputStream(p)), p.fileName.toString(), p)
    }

    @JvmRecord
    data class SharedImpl(
        override val id: Identifier, override val image: NativeImage, override val filename: String,
        override val location: Path, override val url: String, override val uploader: String?,
        override val sharedAt: Instant?
    ) : Local, Remote

    @JvmRecord
    data class RemoteImpl(
        override val id: Identifier, override val image: NativeImage, override val filename: String,
        override val uploader: String?, override val sharedAt: Instant?, override val url: String
    ) : Remote {
        constructor(image: NativeImage, filename: String, uploader: String?, sharedAt: Instant?, url: String) : this(
            Identifier.fromNamespaceAndPath(
                AxolotlClientCommon.MODID,
                "gallery_remote_" + Hashing.sha256().hashUnencodedChars(
                    url.lowercase(Locale.ROOT).replace(Regex("[./]"), "_")
                )
            ), image, filename, uploader, sharedAt, url
        ) {
            register(id, image)
        }
    }

    @JvmRecord
    data class Memory(override val id: Identifier, override val image: NativeImage, override val filename: String) :
        ImageInstance {
        constructor(image: NativeImage) : this(image, net.minecraft.util.Util.getFilenameFormattedDateTime() + ".png")

        constructor(image: NativeImage, name: String) : this(
            Identifier.fromNamespaceAndPath(
                AxolotlClientCommon.MODID,
                "gallery_memory_" + Hashing.sha256().hashUnencodedChars(
                    name.lowercase(Locale.ROOT).replace(Regex("[./]"), "_")
                )
            ), image, name
        ) {
            register(id, image)
        }
    }

    companion object {
        private fun register(id: Identifier, img: NativeImage) {
            Minecraft.getInstance().execute(Runnable {
                Minecraft.getInstance().textureManager.register(id, DynamicTexture({ id.toString() }, img))
            })
        }
    }
}
