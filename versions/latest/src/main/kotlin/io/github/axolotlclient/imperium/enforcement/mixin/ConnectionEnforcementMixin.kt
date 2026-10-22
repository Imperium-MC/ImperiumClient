/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.imperium.enforcement.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import io.github.axolotlclient.imperium.enforcement.EnforcementService
import io.netty.channel.ChannelFutureListener
import net.minecraft.network.Connection
import net.minecraft.network.protocol.Packet
import org.spongepowered.asm.mixin.Mixin

/**
 * The single enforcement chokepoint: `Connection.send(Packet, ChannelFutureListener, boolean)`
 * is the funnel every outgoing packet passes through immediately before the channel write (both
 * `send` overloads delegate here), so validating/replacing/refusing here is strictly
 * "before the socket" for every packet on every connection — in-scope connections get the three
 * enforcement functions, everything else (singleplayer, LAN, other servers) passes untouched.
 *
 * A `null` return from [EnforcementService.processOutgoing] skips the original
 * call entirely: the refused packet is never written to the channel.
 */
@Mixin(Connection::class)
abstract class ConnectionEnforcementMixin {

    @WrapMethod(method = ["send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"])
    private fun `imperium$enforceOutgoing`(
        packet: Packet<*>,
        listener: ChannelFutureListener?,
        flush: Boolean,
        original: Operation<Void>
    ) {
        val enforced = EnforcementService.instance()
            .processOutgoing(this as Connection, packet)
        if (enforced == null) {
            // Refused: do not send. The refusal has already been logged once per session per type.
            return
        }
        original.call(enforced, listener, flush)
    }
}
