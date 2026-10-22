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
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Block coordinates (Alpine "block_coordinates" module, parity spec M4/M6): the block the
 * player is looking at — its X/Y/Z and translated name. Reads the same vanilla hit
 * result the crosshair uses ({@code Minecraft.hitResult}). The block name acts as the
 * row label: with {@code dynamic_block_type_color} it is drawn in the block's own map
 * colour (the same palette the vanilla map item uses).
 */
public class BlockCoordinatesHud extends SimpleTextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("imperiumclient", "blockcoordinateshud");

	/** Constructor minimums of {@code super(75, 13, ...)}; the parent's option fields are private. */
	private static final int MIN_WIDTH = 75;
	private static final int MIN_HEIGHT = 13;

	public final BooleanOption dynamicBlockTypeColor = new BooleanOption("blockcoords.dynamic_block_type_color", false);
	public final ColorOption labelColor = new ColorOption("blockcoords.label_color", new Color(255, 255, 255).immutable());
	public final ColorOption valueColor = new ColorOption("blockcoords.value_color", new Color(160, 160, 160).immutable());

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
		return name + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	@Override
	public void renderComponent(AxoRenderContext render, float delta) {
		drawParts(render, getValue());
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext render, float delta) {
		drawParts(render, getPlaceholderValue());
	}

	/** Two-colour draw: block name as label, coordinates as value. */
	private void drawParts(AxoRenderContext render, String value) {
		DrawPosition pos = getContentPos();
		int x = pos.x() + 2;
		int y = pos.y() + (Math.round((float) getContentHeight() / 2)) - 4;

		String[] parts = value.split(" ", 2);
		int width;
		if (parts.length == 2) {
			int cursor = render.br$drawString(parts[0] + " ", x, y, nameColor(parts[0]).toInt(), shadow.get());
			int end = render.br$drawString(parts[1], cursor, y, valueColor.get().toInt(), shadow.get());
			width = end - x;
		} else {
			int end = render.br$drawString(value, x, y, labelColor.get().toInt(), shadow.get());
			width = end - x;
		}
		updateOwnBounds(width);
	}

	/** Row height: mirror of {@code SimpleTextHudEntry#updateBounds} (both private there):
	 * widen to the drawn text, never below the constructor minimums, re-snap on change. */
	private void updateOwnBounds(int valueWidth) {
		int elementWidth = valueWidth + 2;
		int elementHeight = client.br$getFont().br$getFontHeight() + 4;
		boolean changed = false;
		int newWidth = Math.max(elementWidth, MIN_WIDTH);
		if (newWidth != getContentWidth()) {
			setContentWidth(newWidth);
			changed = true;
		}
		int newHeight = Math.max(elementHeight, MIN_HEIGHT);
		if (newHeight != getContentHeight()) {
			setContentHeight(newHeight);
			changed = true;
		}
		if (changed) {
			onBoundsUpdate();
		}
	}

	private Color nameColor(String name) {
		if (dynamicBlockTypeColor.get() && client.level != null && client.hitResult instanceof BlockHitResult blockHit
			&& client.level.getBlockState(blockHit.getBlockPos()).getBlock().getName().getString().equals(name)) {
			MapColor mapColor = client.level.getBlockState(blockHit.getBlockPos()).getMapColor(client.level, blockHit.getBlockPos());
			if (mapColor != null && mapColor != MapColor.NONE) {
				return new Color(0xFF000000 | mapColor.col).immutable();
			}
		}
		return labelColor.get();
	}

	@Override
	public String getPlaceholderValue() {
		return "Stone 128 64 -256";
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(dynamicBlockTypeColor);
		options.add(labelColor);
		options.add(valueColor);
		return options;
	}

	@Override
	public Identifier getId() {
		return ID;
	}
}
