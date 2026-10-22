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
package io.github.axolotlclient.api.types

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.util.GsonHelper
import java.util.Objects

class Status(
    var type: String?,
    private val lastOnline: Instant?,
    var activity: Activity?
) {
    fun isOnline(): Boolean {
        return "online" == type
    }

    val description: String
        get() = if (activity == null || activity!!.description.isEmpty()) "" else AxoI18n.translate(activity!!.description)

    val title: String
        get() {
            if (!isOnline()) {
                return AxoI18n.translate("api.status.title.offline")
            }
            return AxoI18n.translate(if (activity == null || activity!!.title.isEmpty()) "api.status.title.online" else activity!!.title)
        }

    // Note: the lombok getter for the lastOnline Instant field was suppressed in the Java original
    // because this same-named method already existed; only this translated accessor is public API.
    fun getLastOnline(): String? {
        return lastOnline?.let {
            AxoI18n.translate(
                "api.status.last_online",
                it.atZone(ZoneId.systemDefault()).format(AxolotlClientCommon.getInstance().getConfig().dateTimeFormatter)
            )
        }
    }

    fun setOnline(online: Boolean) {
        type = if (online) "online" else "offline"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Status) return false
        return type == other.type && lastOnline == other.lastOnline && activity == other.activity
    }

    override fun hashCode(): Int {
        return Objects.hash(type, lastOnline, activity)
    }

    override fun toString(): String {
        return "Status(type=$type, lastOnline=$lastOnline, activity=$activity)"
    }

    @JvmRecord
    data class Activity(val title: String, val description: String, val metadata: Metadata?, val started: Instant?) {
        constructor(title: String, description: String) : this(title, description, null as Metadata?)

        constructor(title: String, description: String, metadata: Metadata?) :
            this(title, description, metadata, Instant.now())

        constructor(title: String, description: String, attributes: MetadataAttributes?) :
            this(title, description, attributes?.let { Metadata(it) })

        fun hasMetadata(): Boolean {
            return metadata != null
        }

        fun hasMetadata(id: String): Boolean {
            return hasMetadata() && metadata!!.type == id
        }

        fun interface MetadataAttributes {
            fun typeId(): String
        }

        @JsonAdapter(Metadata.MetadataTypeAdapter::class)
        @JvmRecord
        data class Metadata(val type: String, val attributes: MetadataAttributes?) {
            constructor(attributes: MetadataAttributes) : this(attributes.typeId(), attributes)

            class MetadataTypeAdapter : TypeAdapter<Metadata?>() {
                @Throws(IOException::class)
                override fun write(out: JsonWriter, value: Metadata?) {
                    if (value == null) {
                        out.nullValue()
                        return
                    }
                    out.beginObject()
                    out.name("type").value(value.type)
                    out.name("attributes").jsonValue(GsonHelper.GSON.toJson(value.attributes))
                    out.endObject()
                }

                @Throws(IOException::class)
                override fun read(`in`: JsonReader): Metadata? {
                    // faithfully preserves the Java original: a JSON null is detected but not consumed
                    if (`in`.peek() == JsonToken.NULL) {
                        return null
                    }
                    val metadataObj = GsonHelper.GSON.fromJson<JsonObject>(`in`, JsonObject::class.java)
                    val type = metadataObj.get("type")!!.asString
                    val attributes: MetadataAttributes = when (type) {
                        WorldHostMetadata.ID -> GsonHelper.GSON.fromJson(metadataObj.get("attributes"), WorldHostMetadata::class.java)
                        E4mcMetadata.ID -> GsonHelper.GSON.fromJson(metadataObj.get("attributes"), E4mcMetadata::class.java)
                        ExternalServerMetadata.ID -> GsonHelper.GSON.fromJson(metadataObj.get("attributes"), ExternalServerMetadata::class.java)
                        else -> throw IllegalArgumentException("Unsupported attributes id: $type")
                    }
                    return Metadata(type, attributes)
                }
            }
        }

        @JvmRecord
        data class WorldHostMetadata(
            val connectionId: String?,
            val externalIp: String?,
            val serverInfo: ServerInfo?
        ) : MetadataAttributes {
            override fun typeId(): String {
                return ID
            }

            fun asE4mcMetadata(): E4mcMetadata {
                return E4mcMetadata(externalIp, serverInfo)
            }

            companion object {
                const val ID = "world_host"
            }
        }

        @JvmRecord
        data class E4mcMetadata(val domain: String?, val serverInfo: ServerInfo?) : MetadataAttributes {
            override fun typeId(): String {
                return ID
            }

            companion object {
                const val ID = "e4mc"
            }
        }

        @JvmRecord
        data class ExternalServerMetadata(val serverName: String?, val address: String?) : MetadataAttributes {
            override fun typeId(): String {
                return ID
            }

            companion object {
                const val ID = "external_server"
            }
        }

        @JvmRecord
        data class ServerInfo(
            val levelName: String?,
            val description: String?,
            val icon: Favicon?,
            val players: Players?,
            val version: Version?
        ) {
            @JsonAdapter(Favicon.FaviconTypeAdapter::class)
            @JvmRecord
            data class Favicon(val iconBytes: ByteArray?) {
                override fun toString(): String {
                    return PREFIX + String(Base64.getEncoder().encode(iconBytes!!), StandardCharsets.UTF_8)
                }

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other !is Favicon) return false
                    return iconBytes.contentEquals(other.iconBytes)
                }

                override fun hashCode(): Int {
                    return iconBytes.contentHashCode()
                }

                companion object {
                    private const val PREFIX = "data:image/png;base64,"

                    @JvmStatic
                    fun fromString(base64String: String): Favicon {
                        if (!base64String.startsWith(PREFIX)) {
                            return Favicon(null)
                        }
                        return Favicon(
                            Base64.getDecoder()
                                .decode(base64String.substring(PREFIX.length).replace("\n", "").toByteArray(StandardCharsets.UTF_8))
                        )
                    }
                }

                class FaviconTypeAdapter : TypeAdapter<Favicon?>() {
                    @Throws(IOException::class)
                    override fun write(out: JsonWriter, value: Favicon?) {
                        if (value == null || value.iconBytes == null) {
                            out.nullValue()
                            return
                        }
                        out.value(value.toString())
                    }

                    @Throws(IOException::class)
                    override fun read(`in`: JsonReader): Favicon {
                        return fromString(`in`.nextString())
                    }
                }
            }

            @JvmRecord
            data class Players(val max: Int, val online: Int, val sample: List<Player>?) {
                @JvmRecord
                data class Player(val name: String?, val uuid: String?)
            }

            @JvmRecord
            data class Version(val name: String?, val protocol: Int)
        }

        companion object {
            internal val UNKNOWN = Activity("", "", null, Instant.EPOCH)
        }
    }

    companion object {
        @JvmField
        val UNKNOWN = Status("offline", null, Activity.UNKNOWN)
    }
}
