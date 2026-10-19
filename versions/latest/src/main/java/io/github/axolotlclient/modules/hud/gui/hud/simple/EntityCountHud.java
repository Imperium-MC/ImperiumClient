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

package io.github.axolotlclient.modules.hud.gui.hud.simple;

import java.util.List;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.bridge.util.AxoI18n;
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Loaded entity count (Alpine "entity_count" module, parity spec M4).
 *
 * <p>The bridge has no entity-count accessor, so this module lives in versions/latest
 * (CooldownsHud precedent) and reads the client level directly. Alpine's
 * {@code display_total_count} setting chooses between the total number of loaded
 * entities ({@code ClientLevel.getEntityCount()}) and the render-culled subset
 * ({@code entitiesForRendering()}). Counting happens once per tick, never per frame.</p>
 */
public class EntityCountHud extends SimpleTextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "entitycounthud");

	/** Alpine display_total_count: show all loaded entities instead of the render subset. */
	public final BooleanOption displayTotalCount = new BooleanOption("display_total_count", true);

	private final Minecraft client = (Minecraft) super.client;
	private int count = 0;

	public EntityCountHud() {
		super(true);
	}

	@Override
	public boolean tickable() {
		return true;
	}

	@Override
	public void tick() {
		ClientLevel level = client.level;
		if (level == null) {
			count = 0;
			return;
		}
		if (displayTotalCount.get()) {
			count = level.getEntityCount();
		} else {
			int entities = 0;
			for (Entity entity : level.entitiesForRendering()) {
				entities++;
			}
			count = entities;
		}
	}

	@Override
	public String getValue() {
		if (client.level == null) {
			return getPlaceholderValue();
		}

		return String.valueOf(count);
	}

	@Override
	public String getPlaceholderValue() {
		return "127";
	}

	@Override
	public String getLabel() {
		return AxoI18n.translate("entitycounthud.entities");
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(displayTotalCount);
		return options;
	}
}
