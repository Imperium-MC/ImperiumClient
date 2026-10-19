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

package io.github.axolotlclient.util;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientConfigCommon.FarmingOverlayStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * farming (Alpine parity): when the crosshair targets a crop, its growth stage is drawn
 * near the crosshair — either as text ("3/8", age over total stages) or a small progress
 * bar. {@code farming.hoe_only} restricts the overlay to while holding a hoe.
 *
 * <p>Covers {@link CropBlock} crops (wheat, carrots, potatoes, beetroot); other growers
 * like cocoa/sweet berry/nether wart have their own age properties and are left to a
 * later pass.
 */
public class FarmingOverlay {

	private static final int BAR_WIDTH = 40;
	private static final int BAR_HEIGHT = 5;

	/**
	 * The stage text for a crop state: "{@code age}/{@code stages}", e.g. "3/8" for wheat at
	 * age 3 (stages 0-7). Pure function so the exact rendering input can be asserted.
	 */
	public static String stageText(BlockState state) {
		if (!(state.getBlock() instanceof CropBlock crop)) {
			return null;
		}
		int age = crop.getAge(state);
		int stages = crop.getMaxAge() + 1;
		return age + "/" + stages;
	}

	/**
	 * Growth progress 0..1 of a crop state, or a negative value if the state is not a crop.
	 */
	public static float progress(BlockState state) {
		if (!(state.getBlock() instanceof CropBlock crop)) {
			return -1.0F;
		}
		return (float) crop.getAge(state) / crop.getMaxAge();
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!AxolotlClient.config().farmingOverlay.get() || minecraft.level == null || minecraft.player == null) {
			return;
		}
		if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = minecraft.level.getBlockState(pos);
		if (!(state.getBlock() instanceof CropBlock crop)) {
			return;
		}
		if (AxolotlClient.config().farmingHoeOnly.get() && !isHoldingHoe(minecraft.player.getMainHandItem())) {
			return;
		}

		float progress = (float) crop.getAge(state) / crop.getMaxAge();
		int color = lerpColor(0xFFFF5555, 0xFF55FF55, progress);
		Font font = minecraft.font;

		if (AxolotlClient.config().farmingStyle.get() == FarmingOverlayStyle.BAR) {
			int x = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
			int y = graphics.guiHeight() / 2 + 12;
			graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0x90000000);
			graphics.fill(x + 1, y + 1, x + 1 + Math.round((BAR_WIDTH - 2) * progress), y + BAR_HEIGHT - 1, color);
		} else {
			String text = stageText(state);
			int x = graphics.guiWidth() / 2 + 10;
			int y = graphics.guiHeight() / 2 + 8;
			graphics.text(font, text, x, y, color, true);
		}
	}

	/**
	 * Hoes are fully data-driven in 26.3 (there is no HoeItem class), so the check matches
	 * the vanilla item ids ({@code *_hoe}).
	 */
	public static boolean isHoldingHoe(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_hoe");
	}

	private static int lerpColor(int from, int to, float t) {
		return ARGB.color(
			ARGB.alpha(from),
			Mth.lerpInt(t, ARGB.red(from), ARGB.red(to)),
			Mth.lerpInt(t, ARGB.green(from), ARGB.green(to)),
			Mth.lerpInt(t, ARGB.blue(from), ARGB.blue(to)));
	}
}
