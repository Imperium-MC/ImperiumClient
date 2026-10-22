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

import io.github.axolotlclient.AxolotlClient
import net.minecraft.client.Minecraft

/**
 * Shared chat-peek state (Alpine parity): while the peek keybind is held in-game, the chat renders
 * at focused height with no message fade, showing the full history without opening the chat
 * screen. Both halves of the feature live in mixins (`ChatHudMixin` for height,
 * `ChatAlphaCalculatorMixin` for fade) and consult this helper, which is a plain object so
 * the mixins can stay mixin-legal.
 */
object ChatPeek {

    /**
     * Whether peek is active right now. Called per chat line during render — kept allocation- and
     * lookup-free. Screens (chat screen open, menus, pause) never peek: the chat screen already
     * shows the full history, and peeking under other screens would be noise.
     */
    @JvmStatic
    fun isActive(): Boolean {
        if (AxolotlClient.config() == null || !AxolotlClient.config().chatPeek.get()) {
            return false
        }
        val minecraft = Minecraft.getInstance()
        return minecraft.player != null && minecraft.gui.screen() == null &&
            AxolotlClient.config().peekChatKey.`br$isPressed`()
    }
}
