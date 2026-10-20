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

package io.github.axolotlclient.modules.blur;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.mixin.PostChainAccessor;
import io.github.axolotlclient.modules.AbstractModule;
import io.github.axolotlclient.modules.hud.HudManager;
import io.github.axolotlclient.modules.hud.gui.component.HudEntry;
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry;
import io.github.axolotlclient.util.MathUtil;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

/**
 * Background blur for HUD elements: renders a blurred copy of the world (before any GUI is drawn)
 * into the persistent {@code blurred} target of the {@code axolotlclient:hud_blur} post chain, which
 * {@link BoxHudEntry} then blits behind its background when the entry asks for it.
 *
 * <p>The chain is fully idle (no passes, no allocation beyond the already-loaded program) unless the
 * master option is on AND at least one enabled HUD entry wants blur this frame. The chain runs at
 * the strongest radius requested that frame — the master radius or any entry's radius, whichever
 * is larger — because there is only one shared blur per frame; each entry samples the same frame
 * with the region of the screen behind it.
 */
public class HudBlur extends AbstractModule {

	@Getter
	private static final HudBlur Instance = new HudBlur();
	public final BooleanOption enabled = new BooleanOption("enabled", false);
	public final IntegerOption radius = new IntegerOption("radius", "hudBlur.radius.tooltip", 8, 1, 24);
	public final OptionCategory category = OptionCategory.create("hudBlur");
	private final Identifier postChainId = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "hud_blur");

	private static float blurRadius = 8F;
	private static GpuTextureView blurredView;
	private static int framesProcessed;
	private static boolean failureLogged;

	/**
	 * The radius written into the {@code HudBlurConfig} uniform block each frame (see PostPassMixin).
	 */
	public static float radius() {
		return blurRadius;
	}

	/**
	 * The persistent blurred frame of this frame's pass; {@code null} while the pass is idle or
	 * failed, in which case HUD entries draw no blur (just their plain background).
	 */
	public static GpuTextureView getBlurredView() {
		return blurredView;
	}

	/**
	 * Frames the blur chain actually processed; gametest evidence that the pass runs and stops.
	 */
	public static int framesProcessed() {
		return framesProcessed;
	}

	@Override
	public void init() {
		category.add(enabled, radius);

		AxolotlClient.config().rendering.add(category);
	}

	/**
	 * Runs once per frame right before the GUI is drawn: the world (and only the world) is in the
	 * main target here, so the blurred copy never contains HUD or menu pixels.
	 */
	@SuppressWarnings("deprecation")
	public void render(CrossFrameResourcePool pool) {
		blurredView = null;

		if (!enabled.get() || client.level == null || !Minecraft.getInstance().isGameLoadFinished()) {
			return;
		}

		int max = MathUtil.clamp(radius.get(), 1, 24);
		for (HudEntry entry : HudManager.getInstance().getEntries()) {
			if (entry instanceof BoxHudEntry box && entry.isEnabled() && !entry.isHidden() && box.wantsBlur()) {
				max = Math.max(max, box.blurRadius());
			}
		}
		if (max <= 0) {
			return;
		}

		blurRadius = max;

		try {
			PostChain chain = client.getShaderManager().getPostChain(postChainId, LevelTargetBundle.MAIN_TARGETS);
			if (chain != null) {
				var target = client.gameRenderer.mainRenderTarget();
				chain.process(target, pool);
				// Uniform is set in PostPassMixin
				framesProcessed++;
				blurredView = findBlurredTarget(chain);
			}
		} catch (Exception e) {
			// A post chain failing to compile on an exotic setup must never take the frame down.
			if (!failureLogged) {
				failureLogged = true;
				AxolotlClientCommon.getInstance().getLogger().warn("Failed to run the HUD blur post chain, disabling HUD blur reporting (entries keep their flat background).", e);
			}
		}
	}

	private static GpuTextureView findBlurredTarget(PostChain chain) {
		for (var entry : ((PostChainAccessor) chain).axolotlclient$persistentTargets().entrySet()) {
			RenderTarget target = entry.getValue();
			if ("blurred".equals(entry.getKey().getPath()) && target != null) {
				return target.getColorTextureView();
			}
		}
		return null;
	}
}
