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

import java.util.Comparator;
import java.util.List;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint;
import io.github.axolotlclient.util.DrawUtil;
import io.github.axolotlclient.modules.hud.util.Rectangle;
import io.github.axolotlclient.util.ClientColors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

public class ScoreboardHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath("kronhud", "scoreboardhud");
	public static final Objective placeholder = Util.make(() -> {
		Scoreboard placeScore = new Scoreboard();
		Objective objective =
			placeScore.addObjective("placeholder", ObjectiveCriteria.DUMMY, Component.literal("Scoreboard"),
				ObjectiveCriteria.RenderType.INTEGER, false, StyledFormat.SIDEBAR_DEFAULT
			);
		ScoreAccess dark = placeScore.getOrCreatePlayerScore(ScoreHolder.forNameOnly("DarkKronicle"), objective);
		dark.set(8780);

		ScoreAccess moeh = placeScore.getOrCreatePlayerScore(ScoreHolder.forNameOnly("moehreag"), objective);
		moeh.set(743);

		ScoreAccess kode = placeScore.getOrCreatePlayerScore(ScoreHolder.forNameOnly("TheKodeToad"), objective);
		kode.set(2948);

		placeScore.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
		return objective;
	});

	private final ColorOption backgroundColor = new ColorOption("backgroundcolor", new Color(0x4C000000));
	private final ColorOption topColor = new ColorOption("topbackgroundcolor", new Color(0x66000000));
	private final IntegerOption topPadding = new IntegerOption("toppadding", 0, 0, 4);
	private final BooleanOption scores = new BooleanOption("scores", true);
	private final ColorOption scoreColor = new ColorOption("scorecolor", new Color(0xFFFF5555));
	private final IntegerOption textAlpha = new IntegerOption("text_alpha", 255, 0, 255);

	private final Minecraft client = (Minecraft) super.client;

	public ScoreboardHud() {
		super(200, 146, true);
	}

	@Override
	public void render(AxoRenderContext graphics, float delta) {
		graphics.br$pushMatrix();
		scale(graphics);
		renderComponent(graphics, delta);
		graphics.br$popMatrix();
	}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		//noinspection DataFlowIssue
		Scoreboard scoreboard = this.client.level.getScoreboard();
		Objective objective = null;
		//noinspection DataFlowIssue
		PlayerTeam playerTeam = scoreboard.getPlayersTeam(client.player.getScoreboardName());
		if (playerTeam != null) {
			var teamColor = playerTeam.getColor();
			if (teamColor.isPresent()) {
				objective = scoreboard.getDisplayObjective(teamColor.get().displaySlot());
			}
		}

		Objective objective2 =
			objective != null ? objective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective2 != null) {
			this.displayScoreboardSidebar((GuiGraphicsExtractor) graphics, objective2, false);
		}
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		displayScoreboardSidebar((GuiGraphicsExtractor) graphics, placeholder, true);
	}

	// Abusing this could break some stuff/could allow for unfair advantages. The goal is not to do this, so it won't
	// show any more information than it would have in vanilla.
	private void displayScoreboardSidebar(GuiGraphicsExtractor graphics, Objective objective, boolean placeholder) {
		var font = client.font;
		var scoreboard = objective.getScoreboard();
		var numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);

		@Environment(EnvType.CLIENT)
		record DisplayEntry(Component name, Component score, int scoreWidth) {
		}

		DisplayEntry[] entries = scoreboard.listPlayerScores(objective).stream()
			.filter(entry -> !entry.isHidden())
			.sorted(Comparator.comparing(PlayerScoreEntry::value).reversed()
				.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
			.limit(15L)
			.map(entry -> {
				var team = scoreboard.getPlayersTeam(entry.owner());
				var value = entry.formatValue(numberFormat);
				return new DisplayEntry(PlayerTeam.formatNameForTeam(team, entry.ownerName()), value, font.width(value));
			}).toArray(DisplayEntry[]::new);
		var title = objective.getDisplayName();
		int titleWidth = font.width(title) + 2;
		int maxWidth = titleWidth;
		int textOffset = font.width(": ");

		for (DisplayEntry lv : entries) {
			maxWidth = Math.max(maxWidth, font.width(lv.name) + (lv.scoreWidth > 0 && scores.get() ?
				textOffset + lv.scoreWidth : 0));
		}

		maxWidth += 3;
		int entryCount = entries.length;
		int mainHeight = entryCount * font.lineHeight;

		int newHeight = mainHeight + 10 + topPadding.get() * 2;

		boolean updated = false;
		if (newHeight + 1 != getContentHeight()) {
			setContentHeight(newHeight + 1);
			updated = true;
		}
		if (maxWidth + 1 != getContentWidth()) {
			setContentWidth(maxWidth + 1);
			updated = true;
		}
		if (updated) {
			onBoundsUpdate();
		}

		Rectangle bounds = getContentBounds();

		int yEnd = bounds.y() + bounds.height();
		int textX = bounds.x() + 2;
		int xEnd = bounds.x() + bounds.width() - 1;
		int titleEnd = yEnd - mainHeight;
		var bgBounds = getBounds();
		var maxRounding = Math.min(Math.min(font.lineHeight + topPadding.get() * 2 + backgroundPadding.get(), titleEnd - 1 - bgBounds.y()), xEnd - textX - 3) / 2f;
		float rounding = Math.min(maxRounding, backgroundRounding.get());
		if (!placeholder) {
			if (background.get()) {
				if (roundBackground.get()) {
					graphics.axolotlclient_rendering$roundedRectVarying(bgBounds.x(), bgBounds.y(), bgBounds.xEnd(), titleEnd - 1,
						topColor.get().toInt(), rounding, 0, 0, rounding);
					graphics.axolotlclient_rendering$roundedRectVarying(bgBounds.x(), titleEnd - 1, bgBounds.xEnd(), bgBounds.yEnd(),
						backgroundColor.get().toInt(), 0, rounding, rounding, 0);
				} else {
					graphics.fill(bgBounds.x(), bgBounds.y(), bgBounds.xEnd(), titleEnd - 1, topColor.get().toInt());
					graphics.fill(bgBounds.x(), titleEnd - 1, bgBounds.xEnd(), bgBounds.yEnd(), backgroundColor.get().toInt());
				}
			}
		} else {
			graphics.br$fillRect(bgBounds.x()+1, bgBounds.y()+1, bgBounds.width()-2, titleEnd - 1 - bgBounds.y()-1, ClientColors.DARK_GRAY.withAlpha(100));
		}
		graphics.text(font, title, textX + maxWidth / 2 - titleWidth / 2, titleEnd - font.lineHeight - topPadding.get(),
			ARGB.color(textAlpha.get(), -1), shadow.get());

		for (int v = 0; v < entryCount; v++) {
			DisplayEntry entry = entries[v];
			int y = yEnd - (entryCount - v) * font.lineHeight;
			graphics.text(font, entry.name, textX, y, ARGB.color(textAlpha.get(), -1), shadow.get());
			if (scores.get()) {
				graphics.text(font, entry.score, xEnd - entry.scoreWidth, y, scoreColor.get().toInt(),
					shadow.get());
			}
		}

		if (!placeholder) {
			if (outline.get() && outlineColor.get().getAlpha() > 0) {
				if (roundBackground.get()) {
					graphics.axolotlclient_rendering$outlineRoundedRect(bgBounds.x(), bgBounds.y(), bgBounds.xEnd(), bgBounds.yEnd(), outlineColor.get().toInt(), rounding, 0.5f);
				} else {
					DrawUtil.outlineRect(graphics, bgBounds, outlineColor.get());
				}
			}
		}
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.set(options.indexOf(super.backgroundColor), backgroundColor);
		options.add(hide);
		options.add(topColor);
		options.add(scores);
		options.add(scoreColor);
		options.add(topPadding);
		options.remove(textColor);
		options.add(textAlpha);
		return options;
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	protected AnchorPoint getDefaultAnchor() {
		return AnchorPoint.MIDDLE_RIGHT;
	}

	@Override
	public double getDefaultX() {
		return 1.0;
	}

	@Override
	public double getDefaultY() {
		return 0.5;
	}
}
