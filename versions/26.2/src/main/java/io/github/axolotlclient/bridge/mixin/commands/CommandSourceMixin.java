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

package io.github.axolotlclient.bridge.mixin.commands;

import io.github.axolotlclient.bridge.commands.AxoClientCmdSrcStack;
import io.github.axolotlclient.bridge.util.AxoText;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FabricClientCommandSource.class)
public interface CommandSourceMixin extends AxoClientCmdSrcStack {

	@Shadow
	void sendError(Component par1);

	@Shadow
	void sendFeedback(Component par1);

	@Override
	default void br$sendError(AxoText text) {
		sendError((Component) text);
	}

	@Override
	default void br$sendFeedback(AxoText text) {
		sendFeedback((Component) text);
	}
}
