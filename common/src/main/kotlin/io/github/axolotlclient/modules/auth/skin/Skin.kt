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
import java.io.OutputStreamWriter
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture

import com.google.common.hash.Hashing
import io.github.axolotlclient.api.util.UUIDHelper
import io.github.axolotlclient.modules.auth.Account
import io.github.axolotlclient.modules.auth.MSApi
import io.github.axolotlclient.util.GsonHelper
import io.github.axolotlclient.util.NetworkUtil

interface Skin : Asset {
    fun classicVariant(): Boolean

    fun classicVariant(classic: Boolean)

    interface Local : Skin, Asset.Local

    interface Online : Skin, Asset.Online

    class LocalSkin(
        private var classic: Boolean,
        private val skinFile: Path,
        private val data: ByteArray,
        private val textureKey: String
    ) : Local {
        override fun classicVariant(): Boolean = classic

        override fun classicVariant(classic: Boolean) {
            if (classic != this.classic) {
                try {
                    val metadata = readMetadata(file()) ?: HashMap()
                    metadata[CLASSIC_METADATA_KEY] = classic
                    writeMetadata(file(), metadata)
                } catch (_: IOException) {
                }
            }
            this.classic = classic
        }

        override fun image(): ByteArray = data

        override fun active(): Boolean = false

        override fun equip(api: MSApi, account: Account): CompletableFuture<MSApi.MCProfile> =
            api.uploadAndSetSkin(account, this)

        override fun file(): Path = skinFile

        override fun sha256(): String = textureKey

        companion object {
            const val META_DIR = ".meta"
            const val METADATA_SUFFIX = ".meta"
            const val CLASSIC_METADATA_KEY = "variant_classic"

            @JvmStatic
            @Throws(IOException::class)
            fun readMetadata(skinFile: Path): MutableMap<String, Any>? {
                val metadataFile = getMetadataFile(skinFile)
                if (!Files.exists(metadataFile)) return null

                Files.newInputStream(metadataFile).use { inputStream ->
                    @Suppress("UNCHECKED_CAST")
                    return GsonHelper.read(inputStream) as MutableMap<String, Any>?
                }
            }

            @JvmStatic
            @Throws(IOException::class)
            fun writeMetadata(skinFile: Path, metadata: Map<String, Any>) {
                val metadataFile = getMetadataFile(skinFile)
                Files.createDirectories(metadataFile.parent)
                OutputStreamWriter(Files.newOutputStream(metadataFile)).use { writer ->
                    GsonHelper.GSON.toJson(metadata, writer)
                }
            }

            @JvmStatic
            @Throws(IOException::class)
            fun deleteMetadata(skinFile: Path) {
                Files.deleteIfExists(getMetadataFile(skinFile))
            }

            private fun getMetadataFile(skinFile: Path): Path =
                skinFile.resolveSibling(META_DIR).resolve(skinFile.fileName.toString() + METADATA_SUFFIX)
        }
    }

    @JvmRecord
    data class Shared(val local: Local, val online: MSApi.MCProfile.OnlineSkin) : Local, Online {
        override fun classicVariant(): Boolean = local.classicVariant()

        override fun classicVariant(classic: Boolean) {
            local.classicVariant(classic)
        }

        override fun image(): ByteArray = local.image()

        override fun active(): Boolean = online.active()

        override fun equip(api: MSApi, account: Account): CompletableFuture<MSApi.MCProfile> =
            online.equip(api, account)

        override fun sha256(): String = online.sha256()

        override fun file(): Path = local.file()

        override fun url(): String = online.url()

        override fun supportsDownload(): Boolean = true
    }

    companion object {
        @JvmStatic
        fun getDefaultSkin(account: Account): Skin =
            getDefaultSkin(UUIDHelper.fromUndashed(account.uuid))

        @JvmStatic
        fun getDefaultSkin(uuid: UUID): Skin = DefaultSkin[uuid]
    }
}

@JvmRecord
data class DefaultSkin(val name: String, val stringUrl: String, val url: URI, val wide: Boolean) {
    constructor(name: String, url: String, wide: Boolean) : this(name, url, URI.create(url), wide)

    val skin: Skin
        get() = DEFAULT_SKIN_CACHE.computeIfAbsent(this) {
            val wrapper = object : Skin.Online {
                var wrapped: DefaultSkin = this@DefaultSkin
                var data: ByteArray? = null
                var hash: String? = null

                fun load() {
                    data = client.sendAsync(
                        HttpRequest.newBuilder().GET().uri(wrapped.url).build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                    ).thenApply { obj: HttpResponse<ByteArray> -> obj.body() }.join()
                    hash = Hashing.sha256().hashBytes(data).toString()
                }

                override fun classicVariant(): Boolean = wrapped.wide

                override fun classicVariant(classic: Boolean) {
                    var i = when (this@DefaultSkin.name) {
                        "alex" -> 0
                        "ari" -> 1
                        "efe" -> 2
                        "kai" -> 3
                        "makena" -> 4
                        "noor" -> 5
                        "steve" -> 6
                        "sunny" -> 7
                        "zuri" -> 8
                        else -> throw IllegalStateException()
                    }
                    if (classic) i += 9
                    wrapped = SKINS[i]
                    load()
                }

                override fun image(): ByteArray = data!!

                override fun active(): Boolean = false

                override fun equip(api: MSApi, account: Account): CompletableFuture<MSApi.MCProfile> =
                    api.setSkin(account, this)

                override fun sha256(): String = hash!!

                override fun url(): String = wrapped.stringUrl
            }
            wrapper.load()
            wrapper
        }

    companion object {
        private val client = NetworkUtil.createHttpClient()
        private val DEFAULT_SKIN_CACHE: MutableMap<DefaultSkin, Skin> = HashMap()

        private val SKINS = arrayOf(
            DefaultSkin("alex", "https://minecraft.wiki/images/Alex_%28slim_texture%29_JE3.png", false),
            DefaultSkin("ari", "https://minecraft.wiki/images/Ari_%28slim_texture%29_JE1.png", false),
            DefaultSkin("efe", "https://minecraft.wiki/images/Efe_%28slim_texture%29_JE1.png", false),
            DefaultSkin("kai", "https://minecraft.wiki/images/Kai_%28slim_texture%29_JE1.png", false),
            DefaultSkin("makena", "https://minecraft.wiki/images/Makena_%28slim_texture%29_JE1.png", false),
            DefaultSkin("noor", "https://minecraft.wiki/images/Noor_%28slim_texture%29_JE1.png", false),
            DefaultSkin("steve", "https://minecraft.wiki/images/Steve_%28slim_texture%29_JE2.png", false),
            DefaultSkin("sunny", "https://minecraft.wiki/images/Sunny_%28slim_texture%29_JE1.png", false),
            DefaultSkin("zuri", "https://minecraft.wiki/images/Zuri_%28slim_texture%29_JE1.png", false),
            DefaultSkin("alex", "https://minecraft.wiki/images/Alex_%28classic_texture%29_JE2.png", true),
            DefaultSkin("ari", "https://minecraft.wiki/images/Ari_%28classic_texture%29_JE1.png", true),
            DefaultSkin("efe", "https://minecraft.wiki/images/Efe_%28classic_texture%29_JE1.png", true),
            DefaultSkin("kai", "https://minecraft.wiki/images/Kai_%28classic_texture%29_JE1.png", true),
            DefaultSkin("makena", "https://minecraft.wiki/images/Makena_%28classic_texture%29_JE1.png", true),
            DefaultSkin("noor", "https://minecraft.wiki/images/Noor_%28classic_texture%29_JE1.png", true),
            DefaultSkin("steve", "https://minecraft.wiki/images/Steve_%28classic_texture%29_JE6.png", true),
            DefaultSkin("sunny", "https://minecraft.wiki/images/Sunny_%28classic_texture%29_JE1.png", true),
            DefaultSkin("zuri", "https://minecraft.wiki/images/Zuri_%28classic_texture%29_JE1.png", true)
        )

        @JvmStatic
        operator fun get(uuid: UUID): Skin =
            SKINS[Math.floorMod(uuid.hashCode(), SKINS.size)].skin
    }
}
