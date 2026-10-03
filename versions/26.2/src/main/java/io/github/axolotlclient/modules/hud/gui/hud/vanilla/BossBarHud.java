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

package io.github.axolotlclient.modules.hud.gui.hud.vanilla;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.render.AxoWindow;
import io.github.axolotlclient.mixin.BossBarHudAccessor;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.BossEvent;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

public class BossBarHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("kronhud", "bossbarhud");
	private static final Identifier[] BAR_BACKGROUND_SPRITES =
		new Identifier[]{Identifier.withDefaultNamespace("boss_bar/pink_background"),
			Identifier.withDefaultNamespace("boss_bar/blue_background"),
			Identifier.withDefaultNamespace("boss_bar/red_background"),
			Identifier.withDefaultNamespace("boss_bar/green_background"),
			Identifier.withDefaultNamespace("boss_bar/yellow_background"),
			Identifier.withDefaultNamespace("boss_bar/purple_background"),
			Identifier.withDefaultNamespace("boss_bar/white_background")};
	private static final Identifier[] BAR_PROGRESS_SPRITES =
		new Identifier[]{Identifier.withDefaultNamespace("boss_bar/pink_progress"),
			Identifier.withDefaultNamespace("boss_bar/blue_progress"),
			Identifier.withDefaultNamespace("boss_bar/red_progress"),
			Identifier.withDefaultNamespace("boss_bar/green_progress"),
			Identifier.withDefaultNamespace("boss_bar/yellow_progress"),
			Identifier.withDefaultNamespace("boss_bar/purple_progress"),
			Identifier.withDefaultNamespace("boss_bar/white_progress")};
	private static final Identifier[] OVERLAY_BACKGROUND_SPRITES =
		new Identifier[]{Identifier.withDefaultNamespace("boss_bar/notched_6_background"),
			Identifier.withDefaultNamespace("boss_bar/notched_10_background"),
			Identifier.withDefaultNamespace("boss_bar/notched_12_background"),
			Identifier.withDefaultNamespace("boss_bar/notched_20_background")};
	private static final Identifier[] OVERLAY_PROGRESS_SPRITES =
		new Identifier[]{Identifier.withDefaultNamespace("boss_bar/notched_6_progress"),
			Identifier.withDefaultNamespace("boss_bar/notched_10_progress"),
			Identifier.withDefaultNamespace("boss_bar/notched_12_progress"),
			Identifier.withDefaultNamespace("boss_bar/notched_20_progress")};
	private final BossEvent placeholder = new CustomBossBar(Component.literal("Boss bar"), BossEvent.BossBarColor.WHITE,
		BossEvent.BossBarOverlay.PROGRESS
	);
	private final BossEvent placeholder2 = Util.make(() -> {
		BossEvent boss = new CustomBossBar(Component.literal("More boss bars..."), BossEvent.BossBarColor.PURPLE,
			BossEvent.BossBarOverlay.PROGRESS
		);
		boss.setProgress(0.45F);
		return boss;
	});
	private final BooleanOption text = new BooleanOption("text", true);
	private final BooleanOption bar = new BooleanOption("bar", true);
	// TODO custom color
	private Map<UUID, LerpingBossEvent> bossBars = new HashMap<>();
	private final Minecraft client = (Minecraft) super.client;

	public BossBarHud() {
		super(184, 80, false);
	}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		setBossBars();
		if (bossBars == null || this.bossBars.isEmpty()) {
			return;
		}
		DrawPosition scaledPos = getContentPos();
		int by = 12;
		for (LerpingBossEvent bossBar : bossBars.values()) {
			renderBossBar((GuiGraphicsExtractor) graphics, scaledPos.x() + 1, by + scaledPos.y(), bossBar);
			by = by + 19;
			if (by > getContentHeight()) {
				break;
			}
		}
	}

	public void setBossBars() {
		bossBars = ((BossBarHudAccessor) client.gui.hud.getBossOverlay()).axolotlclient$getBossBars();
		if (bossBars != null) {
			if (bossBars.isEmpty()) {
				// Just leave it alone, it's not rendering anyway
				return;
			}
			var h = Math.min(bossBars.size() * 19, (int) (AxoWindow.getWindow().br$getScaledHeight() / 3d));
			if (h != getContentHeight()) {
				// Update height
				setContentHeight(Math.min(bossBars.size() * 19, (int) (AxoWindow.getWindow().br$getScaledHeight() / 3d)));
				onBoundsUpdate();
			}
		}
	}

	private void renderBossBar(GuiGraphicsExtractor graphics, int x, int y, BossEvent bossBar) {
		if (bar.get()) {
			this.drawBar(graphics, x, y, bossBar, 182, BAR_BACKGROUND_SPRITES, OVERLAY_BACKGROUND_SPRITES);
			int i = Mth.lerpDiscrete(bossBar.getProgress(), 0, 182);
			if (i > 0) {
				this.drawBar(graphics, x, y, bossBar, i, BAR_PROGRESS_SPRITES, OVERLAY_PROGRESS_SPRITES);
			}
		}
		if (text.get()) {
			Component text = bossBar.getName();
			float textX = x + ((float) getContentWidth() / 2) - ((float) client.font.width(text) / 2);
			float textY = y - 9;
			graphics.text(client.font, text, (int) textX, (int) textY, textColor.get().toInt(), shadow.get());
		}
	}

	private void drawBar(GuiGraphicsExtractor graphics, int x, int y, BossEvent bar, int width, Identifier[] textures, Identifier[] alternativeTextures) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, textures[bar.getColor().ordinal()], 182, 5, 0, 0, x, y, width, 5);
		if (bar.getOverlay() != BossEvent.BossBarOverlay.PROGRESS) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, alternativeTextures[bar.getOverlay().ordinal() - 1], 182, 5, 0,
				0, x, y, width, 5
			);
		}
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		var height = 2 * 19;
		if (height != getContentHeight()) {
			setContentHeight(height);
			onBoundsUpdate();
		}
		DrawPosition pos = getContentPos();
		renderBossBar((GuiGraphicsExtractor) graphics, pos.x() + 1, pos.y() + 12, placeholder);
		renderBossBar((GuiGraphicsExtractor) graphics, pos.x() + 1, pos.y() + 31, placeholder2);
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(hide);
		options.add(text);
		options.add(bar);
		return options;
	}

	@Override
	protected AnchorPoint getDefaultAnchor() {
		return AnchorPoint.TOP_MIDDLE;
	}

	@Override
	public double getDefaultX() {
		return 0.5;
	}

	public static class CustomBossBar extends BossEvent {
		private static final RandomSource source = RandomSource.create();

		public CustomBossBar(Component name, BossBarColor color, BossBarOverlay style) {
			super(Mth.createInsecureUUID(source), name, color, style);
		}
	}
}
