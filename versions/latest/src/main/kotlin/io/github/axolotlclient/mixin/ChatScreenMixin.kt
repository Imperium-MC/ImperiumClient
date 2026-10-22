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

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.modules.chat.ChatScreenshotButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Chat screenshot button (`chat_screenshot_button`): after the chat screen builds its
 * input row, the input is narrowed slightly and a small camera button sits at the right end
 * of that row. The button itself is a vector-drawn widget ([ChatScreenshotButton]);
 * the click goes through the vanilla render-target readback in `ChatScreenshot`.
 *
 * Extends [Screen] (the FriendsOverlay pattern) so the inherited
 * `addRenderableWidget` is callable from the injected init code.
 */
@Mixin(ChatScreen::class)
abstract class ChatScreenMixin private constructor(title: Component) : Screen(title) {

    @Shadow
    protected var input: EditBox? = null

    @Inject(method = ["init"], at = [At("TAIL")])
    private fun `axolotlclient$addChatScreenshotButton`(ci: CallbackInfo) {
        if (!AxolotlClient.config().chatScreenshotButton.get()) {
            return
        }
        val size = 12
        val gap = 2
        // Vanilla's input spans x 4 .. width-4 on the bottom 12px row; leave room for the
        // camera at its right end so the two never overlap.
        input!!.setWidth(width - 8 - size - gap)
        addRenderableWidget(ChatScreenshotButton(width - 4 - size, height - 12, size))
    }
}
