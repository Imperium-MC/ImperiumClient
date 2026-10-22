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

package io.github.axolotlclient.modules.hud.gui.hud;

import java.util.List;
import java.util.Locale;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffectInstance;
import io.github.axolotlclient.bridge.entity.effect.AxoStatusEffects;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.bridge.util.AxoText;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.gui.layout.CardinalOrder;
import io.github.axolotlclient.modules.hud.util.DefaultOptions;
import io.github.axolotlclient.modules.hud.util.Rectangle;
import io.github.axolotlclient.util.ClientColors;
import io.github.axolotlclient.util.CommonUtil;
import lombok.AllArgsConstructor;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
public class PotionsHud extends TextHudEntry {
	public static final AxoIdentifier ID = AxoIdentifier.of("kronhud", "potionshud");

	private final EnumOption<CardinalOrder> order = DefaultOptions.getCardinalOrder(CardinalOrder.TOP_DOWN);

	private final EnumOption<Mode> mode = new EnumOption<>("mode", Mode.class, Mode.ICONS_AND_TEXT);
	private final BooleanOption showEffectName = new BooleanOption("showEffectNames", true);
	// Alpine parity: show_icon forces the icons off even in icon modes (a text-only override
	// of the display mode); display_vanilla keeps the vanilla effect HUD drawn alongside this
	// HUD instead of cancelling it (read by the InGameHudMixin cancel guard).
	private final BooleanOption showIcon = new BooleanOption("potionshud.show_icon", true);
	public final BooleanOption displayVanilla = new BooleanOption("potionshud.display_vanilla", false);
	private final ColorOption timerTextColor = new ColorOption("potionshud.timer_text_color", Color.parse("#7F7F7F"));
	private final BooleanOption collapsed = new BooleanOption("potionshud.collapsed", false);
	private final IntegerOption blinkLastSeconds = new IntegerOption("potionshud.blink_last_seconds", 0, 0, 60);
	private final BooleanOption dynamicColor = new BooleanOption("potionshud.dynamic_color", false);

	private static AxoText formatNameAndAmplifier(AxoStatusEffectInstance effect) {
		return effect.br$getType().br$getDisplayName().br$copy()
			.br$append(" ")
			.br$append(CommonUtil.toRoman(effect.br$getAmplifier() + 1));
	}

	public PotionsHud() {
		super(50, 200, true);
		background = new BooleanOption("background", false);
	}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		assert client.br$getPlayer() != null;
		renderEffects(graphics, client.br$getPlayer().br$getStatusEffects());
	}

	@Override
	public void render(AxoRenderContext ctx, float delta) {
		final var player = client.br$getPlayer();
		if (player == null) {
			return;
		}
		var effects = player.br$getStatusEffects();
		boolean noEffects = effects.isEmpty();
		int calcWidth = noEffects ? 0 : calculateWidth(effects);
		int calcHeight = noEffects ? 0 : calculateHeight(effects);
		boolean changed = false;
		if (calcWidth != getContentWidth()) {
			setContentWidth(calcWidth);
			changed = true;
		}
		if (calcHeight != getContentHeight()) {
			setContentHeight(calcHeight);
			changed = true;
		}
		if (changed) {
			onBoundsUpdate();
		}
		if (noEffects) {
			return;
		}
		super.render(ctx, delta);
	}

	private void renderEffects(AxoRenderContext graphics, List<AxoStatusEffectInstance> effects) {
		boolean noEffects = effects.isEmpty();
		int calcWidth = noEffects ? 0 : calculateWidth(effects);
		int calcHeight = noEffects ? 0 : calculateHeight(effects);
		boolean changed = false;
		if (calcWidth != getContentWidth()) {
			setContentWidth(calcWidth);
			changed = true;
		}
		if (calcHeight != getContentHeight()) {
			setContentHeight(calcHeight);
			changed = true;
		}
		if (changed) {
			onBoundsUpdate();
		}
		if (noEffects) {
			return;
		}
		int lastPos = 0;
		CardinalOrder direction = order.get();

		Rectangle bounds = getContentBounds();
		int x = bounds.x();
		int y = bounds.y();
		for (int i = 0; i < effects.size(); i++) {
			final var effect = effects.get(direction.getDirection() == -1 ? i : effects.size() - i - 1);
			if (direction.isXAxis()) {
				renderPotion(graphics, effect, x + lastPos + 2, y + 2);
				int nameWidth = 0;
				if (showText()) {
					nameWidth += graphics.br$getFont().br$getWidth(effect.br$formatDuration()) + 2;
					if (showEffectName.get()) {
						nameWidth = Math.max(nameWidth, client.br$getFont().br$getWidth(formatNameAndAmplifier(effect)) + 2);
					}
				}
				if (showIcons()) {
					nameWidth += 20;
				}
				lastPos += nameWidth;
			} else {
				renderPotion(graphics, effect, x + 2, y + 2 + lastPos);
				lastPos += 20;
			}
		}
	}

	private int calculateWidth(List<AxoStatusEffectInstance> effects) {
		final var widthStreamFullName = effects.stream()
			.map(PotionsHud::formatNameAndAmplifier)
			.mapToInt(client.br$getFont()::br$getWidth);
		final var widthStreamDuration = effects.stream()
			.map(AxoStatusEffectInstance::br$formatDuration)
			.mapToInt(client.br$getFont()::br$getWidth);

		int width = 2;
		if (order.get().isXAxis()) {
			if (showIcons()) {
				width += 20 * effects.size();
			}

			if (showText()) {
				if (!showEffectName.get()) {
					width += widthStreamDuration.map(i -> i + 2).sum();
				} else {
					width += widthStreamFullName.map(i -> i + 2).sum();
				}
			}
		} else {
			if (showIcons()) {
				width += 20;
			}

			if (showText()) {
				if (!showEffectName.get()) {
					width += widthStreamDuration.max().orElse(0) + 2;
				} else {
					width += widthStreamFullName.max().orElse(0) + 2;
				}
			}
		}
		return width;
	}

	private int calculateHeight(List<AxoStatusEffectInstance> effects) {
		if ((order.get()).isXAxis()) {
			return 24;
		} else {
			return 20 * effects.size() + 2;
		}
	}

	private void renderPotion(AxoRenderContext graphics, AxoStatusEffectInstance effect, int x, int y) {
		final var type = effect.br$getType();

		if (showIcons()) {
			if (!isIconBlinkingOff(effect)) {
				Color tint = dynamicTint(effect);
				graphics.br$drawTexture(type.br$getSprite(), x, y, 18, 18, tint != null ? tint.toInt() : -1);
			}
			x += 19;
		}

		if (showText()) {
			Color tint = dynamicTint(effect);
			Color durationColor = tint != null ? tint : timerTextColor.get();
			if (showEffectName.get()) {
				graphics.br$drawString(formatNameAndAmplifier(effect), x, y + 1, textColor.get().toInt(), shadow.get());
				graphics.br$drawString(effect.br$formatDuration(), x, y + 1 + 10,
					durationColor.toInt(), shadow.get());
			} else {
				graphics.br$drawString(effect.br$formatDuration(), x, y + 5,
					durationColor.toInt(), shadow.get());
			}
		}
	}

	/**
	 * Icons show in icon modes and while collapsed, unless {@code show_icon} is switched off
	 * (a text-only override that wins over every icon mode).
	 */
	private boolean showIcons() {
		return showIcon.get() && (collapsed.get() || mode.get().hasIcons);
	}

	private boolean showText() {
		return !collapsed.get() && mode.get().hasText;
	}

	/**
	 * Whether the icon is currently in its "off" blink phase: effects with less than
	 * {@code blink_last_seconds} remaining toggle their icon on a ~250 ms cycle so expiring
	 * effects are hard to miss.
	 */
	private boolean isIconBlinkingOff(AxoStatusEffectInstance effect) {
		int threshold = blinkLastSeconds.get();
		if (threshold <= 0) {
			return false;
		}
		int duration = effect.br$getDuration();
		if (duration < 0 || duration >= threshold * 20) {
			return false;
		}
		return (System.currentTimeMillis() / 250L) % 2L == 0L;
	}

	/**
	 * Duration-based tint for icon and timer text: yellow in the last 30 seconds, orange in
	 * the last 10, red in the last 3. Infinite effects are not tinted.
	 */
	private Color dynamicTint(AxoStatusEffectInstance effect) {
		if (!dynamicColor.get()) {
			return null;
		}
		int duration = effect.br$getDuration();
		if (duration < 0) {
			return null;
		}
		float seconds = duration / 20F;
		if (seconds <= 3) {
			return Colors.RED;
		}
		if (seconds <= 10) {
			return ClientColors.GOLD;
		}
		if (seconds <= 30) {
			return Colors.YELLOW;
		}
		return null;
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		AxoStatusEffectInstance effect = AxoStatusEffectInstance.create(AxoStatusEffects.SPEED, 9999);
		AxoStatusEffectInstance jump = AxoStatusEffectInstance.create(AxoStatusEffects.JUMP_BOOST, 99999);
		AxoStatusEffectInstance haste = AxoStatusEffectInstance.create(AxoStatusEffects.HASTE, -1);
		renderEffects(graphics, List.of(effect, jump, haste));
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(order);
		options.add(mode);
		options.add(collapsed);
		options.add(showIcon);
		options.add(displayVanilla);
		options.add(blinkLastSeconds);
		options.add(dynamicColor);
		options.add(showEffectName);
		options.add(timerTextColor);
		return options;
	}

	@Override
	public AxoIdentifier getId() {
		return ID;
	}

	@AllArgsConstructor
	private enum Mode {
		ICONS_ONLY(true, false),
		TEXT_ONLY(false, true),
		ICONS_AND_TEXT(true, true);
		private final boolean hasIcons, hasText;

		@Override
		public String toString() {
			return "potionshud.mode." + super.toString().toLowerCase(Locale.ROOT);
		}
	}
}
