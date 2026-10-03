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

package io.github.axolotlclient.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.events.types.ReceiveChatMessageEvent;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

	@WrapMethod(method = "addMessage")
	private void onChatMessage(Component contents, MessageSignature signature, GuiMessageSource source, net.minecraft.client.multiplayer.chat.GuiMessageTag tag, Operation<Void> original) {
		ReceiveChatMessageEvent event = new ReceiveChatMessageEvent(false, contents.getString(), contents);
		Events.RECEIVE_CHAT_MESSAGE.invoker().accept(event);
		if (event.isCancelled()) {
			return;
		} else if (event.getNewMessage() != null) {
			contents = (Component) event.getNewMessage();
		}
		original.call(contents, signature, source, tag);
	}
}
