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

package io.github.axolotlclient.util.duck

import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessage

/**
 * Duck interface `ChatHudMixin` adds to [ChatComponent]; lets tests (and tooling) read
 * the chat history without reflection.
 */
interface ChatHudExtension {

    fun `axolotlclient$getMessages`(): List<GuiMessage>

    /**
     * Appends persisted messages to the (expected-empty) history and rebuilds the trimmed-line
     * cache, the same way vanilla's own `restoreState` does. Used by the
     * `persistent_chat` module on join.
     */
    fun `axolotlclient$restoreMessages`(messages: List<GuiMessage>)

    /**
     * The smooth-chat animation's current fractional scroll position (lines). Equals the
     * scrollbar target when smooth chat is off or settled; dips below zero while a message
     * slides in. Exposed for tests.
     */
    fun `axolotlclient$getSmoothScrollCurrent`(): Float
}
