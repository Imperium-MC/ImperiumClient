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

package io.github.axolotlclient.mixin

import io.github.axolotlclient.modules.autoutils.AutoReconnect
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Auto-Reconnect's countdown carrier: a centered line near the bottom of the vanilla
 * disconnect screen. The module owns the text and the connect decision; this only hands it
 * a widget on the screen (init re-fires on resize, and attach restarts the countdown).
 */
@Mixin(DisconnectedScreen::class)
abstract class DisconnectedScreenMixin : Screen(Component.empty()) {

    @Inject(method = ["init"], at = [At("TAIL")])
    private fun `axolotlclient$addReconnectCountdown`(ci: CallbackInfo) {
        val reconnect = AutoReconnect.getInstance()
        if (!reconnect.wantsCountdown()) {
            return
        }
        val self = this as DisconnectedScreen
        val label = StringWidget(0, this.height - 30, 10, 12, Component.empty(), this.font)
        addRenderableWidget(label)
        reconnect.attachCountdown(self, label)
    }
}
