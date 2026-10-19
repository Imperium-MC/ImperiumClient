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

import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Block coordinates (Alpine "block_coordinates" module, parity spec M4): the block the
 * player is looking at — its X/Y/Z and translated name. Reads the same vanilla hit
 * result the crosshair uses ({@code Minecraft.hitResult}); Alpine's
 * {@code dynamic_block_type_color} colouring is not part of this simple-text batch
 * (the shared text colour option covers static colouring).
 */
public class BlockCoordinatesHud extends SimpleTextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "blockcoordinateshud");

	private final Minecraft client = (Minecraft) super.client;

	public BlockCoordinatesHud() {
		super(75, 13, true, false);
	}

	@Override
	public String getValue() {
		if (client.level == null) {
			return getPlaceholderValue();
		}

		HitResult hit = client.hitResult;
		if (hit == null || hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
			return "-";
		}

		BlockPos pos = blockHit.getBlockPos();
		String name = client.level.getBlockState(pos).getBlock().getName().getString();
		return pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + name;
	}

	@Override
	public String getPlaceholderValue() {
		return "128 64 -256 Stone";
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
