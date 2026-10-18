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

package io.github.axolotlclient.util.keybinds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.axolotlclient.AxolotlClientCommon;
import lombok.Getter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class KeyBinds {
	public static final KeyMapping.Category CATEGORY_AXOLOTLCLIENT = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "title"));
	@Getter
	private final static KeyBinds instance = new KeyBinds();

	private List<KeyMapping> binds = new ArrayList<>();

	public KeyMapping register(KeyMapping bind) {
		if (binds == null) {
			throw new IllegalStateException("Keybind registered too late!");
		}
		binds.add(bind);

		return bind;
	}

	public KeyMapping registerWithSimpleAction(KeyMapping bind, Runnable action) {
		ClientTickEvents.END_CLIENT_TICK.register(c -> {
			if (bind.consumeClick()) {
				action.run();
			}
		});
		return register(bind);
	}

	public KeyMapping[] process(KeyMapping[] keys) {
		List<KeyMapping> keyBinds = new ArrayList<>();
		Collections.addAll(keyBinds, keys);
		var registered = binds;
		binds = null;
		keyBinds.addAll(registered);
		return keyBinds.toArray(KeyMapping[]::new);
	}
}
