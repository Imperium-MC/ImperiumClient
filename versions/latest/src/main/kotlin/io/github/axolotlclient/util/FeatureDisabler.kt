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

import com.mojang.serialization.Codec
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.util.options.ForceableBooleanOption
import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

class FeatureDisabler : FeatureDisablerCommon() {

    override fun registerChannel() {
        PayloadTypeRegistry.clientboundPlay().register(CHANNEL_ID, FeaturePayload.CODEC)
        ClientPlayConnectionEvents.INIT.register(ClientPlayConnectionEvents.Init { _, _ ->
            ClientPlayNetworking.registerGlobalReceiver(
                CHANNEL_ID,
                ClientPlayNetworking.PlayPayloadHandler { payload, _ ->
                    for (feature in payload.features) {
                        try {
                            val e: ForceableBooleanOption? = FEATURES[feature]
                            e!!.setForceOff(true, "ban_reason")
                        } catch (e: Exception) {
                            AxolotlClientCommon.getInstance().logger.error("Failed to disable $feature!")
                        }
                    }
                }
            )
        })
    }

    @JvmRecord
    data class FeaturePayload(val features: List<String>) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<*> = CHANNEL_ID

        companion object {
            @JvmField
            val CODEC: StreamCodec<ByteBuf, FeaturePayload> =
                ByteBufCodecs.fromCodec(Codec.STRING.listOf().xmap(::FeaturePayload, FeaturePayload::features))
        }
    }

    companion object {
        // Eagerly constructs the singleton (the base class stores it). NOTE: the static
        // accessor below is what makes that happen — invoking an *inherited* static
        // (FeatureDisablerCommon.getInstance()) only initializes the declaring class, so
        // before this accessor existed the companion never ran and the singleton stayed
        // null, crashing client startup with an NPE in AxolotlClientCommon.init.
        private val INSTANCE = FeatureDisabler()

        // Declared IN this class on purpose (JvmStatic): invoking it initializes
        // FeatureDisabler, which runs the companion initializer above.
        @JvmStatic
        fun getInstance(): FeatureDisabler = INSTANCE

        private val CHANNEL_ID = CustomPacketPayload.Type<FeaturePayload>(CHANNEL_NAME as Identifier)
    }
}
