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

package io.github.axolotlclient.modules.blur;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption;
import io.github.axolotlclient.modules.AbstractModule;
import lombok.Getter;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

public class MotionBlur extends AbstractModule {

	@Getter
	private static final MotionBlur Instance = new MotionBlur();
	public final BooleanOption enabled = new BooleanOption("enabled", false);
	public final FloatOption strength = new FloatOption("strength", 50F, 1F, 99F);
	public final BooleanOption inGuis = new BooleanOption("inGuis", false);
	public final OptionCategory category = OptionCategory.create("motionBlur");
	private final Identifier postChainId = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "motion_blur");

	public static float getBlur() {
		return getInstance().strength.get() / 100F;
	}

	@Override
	public void init() {
		category.add(enabled, strength, inGuis);

		AxolotlClient.config().rendering.add(category);
	}

	@SuppressWarnings("deprecation")
	public void render(CrossFrameResourcePool pool) {
		PostChain shader = client.getShaderManager().getPostChain(postChainId, LevelTargetBundle.MAIN_TARGETS);
		if (shader != null) {
			var target = client.gameRenderer.mainRenderTarget();
			shader.process(target, pool);
			// Uniform is set in PostPassMixin
		}
	}
}
