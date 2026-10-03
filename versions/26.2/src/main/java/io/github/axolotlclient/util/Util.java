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

package io.github.axolotlclient.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Graphics;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringUtil;
import org.apache.commons.lang3.StringUtils;

public class Util {
	private static final Map<Identifier, DynamicTexture> textures = new HashMap<>();

	public static void sendChatMessage(String msg) {
		msg = StringUtil.trimChatMessage(StringUtils.normalizeSpace(msg.trim()));
		assert Minecraft.getInstance().player != null;
		if (msg.startsWith("/")) {
			Minecraft.getInstance().player.connection.sendCommand(msg.substring(1));
		} else {
			Minecraft.getInstance().player.connection.sendChat(msg);
		}
	}

	public static void addMessageToChatHud(Component msg) {
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(msg);
	}

	public static Identifier getTexture(GraphicsOption option) {
		return getTexture(option.get(), option.getName());
	}

	public static Identifier getTexture(Graphics graphics, String name) {
		Identifier id = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "graphics_" + name.toLowerCase(Locale.ROOT));
		try {
			DynamicTexture texture;
			boolean reuse = textures.containsKey(id);
			if (reuse) {
				var img = textures.get(id);
				reuse = img.getPixels().getWidth() == graphics.getWidth() && img.getPixels().getHeight() == graphics.getHeight();
			}
			if (!reuse) {
				texture = new DynamicTexture(id::toString, NativeImage.read(graphics.getPixelData()));
				var prev = textures.put(id, texture);
				if (prev != null) prev.close();
				Minecraft.getInstance().getTextureManager().register(id, texture);
			} else {
				texture = textures.get(id);
				for (int x = 0; x < graphics.getWidth(); x++) {
					for (int y = 0; y < graphics.getHeight(); y++) {
						texture.getPixels().setPixel(x, y, graphics.getPixelColor(x, y));
					}
				}
			}

			texture.upload();
		} catch (IOException e) {
			AxolotlClientCommon.getInstance().getLogger().error("Failed to bind texture for " + name + ": ", e);
		}
		return id;
	}
}
