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

package io.github.axolotlclient.modules.hud.gui.hud.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.bridge.BridgeVersion;
import io.github.axolotlclient.bridge.item.AxoEnchants;
import io.github.axolotlclient.bridge.item.AxoItemStack;
import io.github.axolotlclient.bridge.item.AxoItems;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.bridge.util.AxoIdentifier;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint;
import io.github.axolotlclient.modules.hud.gui.layout.CardinalOrder;
import io.github.axolotlclient.modules.hud.util.DefaultOptions;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import io.github.axolotlclient.util.ClientColors;
import io.github.axolotlclient.util.CommonUtil;
import io.github.axolotlclient.util.ItemUtil;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
public class ArmorHud extends TextHudEntry {

	public static final AxoIdentifier ID = AxoIdentifier.of("kronhud", "armorhud");
	private static final Supplier<AxoItemStack> PLACEHOLDER_MAIN_HAND = CommonUtil.memoize(() -> AxoItemStack.of(AxoItems.IRON_SWORD));
	private static final Supplier<List<AxoItemStack>> PLACEHOLDER_GEAR = CommonUtil.memoize(() -> List.of(
		AxoItemStack.of(AxoItems.IRON_BOOTS),
		AxoItemStack.of(AxoItems.IRON_LEGGINGS),
		AxoItemStack.of(AxoItems.IRON_CHESTPLATE),
		AxoItemStack.of(AxoItems.IRON_HELMET)
	));

	/**
	 * Armor pieces and their durability bars change through discrete events, not on their
	 * own, so this entry can be served from the {@code hud_frame_limit} recording.
	 */
	@Override
	public boolean dynamicContent() {
		return false;
	}

	protected final BooleanOption showProtLvl = new BooleanOption("showProtectionLevel", false);
	private final BooleanOption showDurabilityNumber = new BooleanOption("show_durability_num", false);
	private final BooleanOption showMaxDurabilityNumber = new BooleanOption("show_max_durability_num", false);
	private final EnumOption<DurabilityFormat> durabilityFormat = new EnumOption<>("armorhud.durability_format",
		DurabilityFormat.class, DurabilityFormat.NONE);
	private final BooleanOption durabilityColorTiers = new BooleanOption("armorhud.durability_color", false);
	private final BooleanOption customDurabilityNumColor = new BooleanOption("armorhud.custom_durability_num_color",
		false);
	private final ColorOption durabilityNumColor = new ColorOption("armorhud.durability_num_color", Colors.WHITE);
	// armor_status batch (Alpine parity): the durability readout can interpolate between two
	// configured colours (start = fresh, end = about to break) instead of the tiers/vanilla
	// bar colour; durability_color_default is the empty-track colour of the BARS format.
	private final BooleanOption durabilityGradient = new BooleanOption("armorhud.durability_gradient", false);
	private final ColorOption durabilityColorStart = new ColorOption("armorhud.durability_color_start", Colors.GREEN);
	private final ColorOption durabilityColorEnd = new ColorOption("armorhud.durability_color_end", Colors.RED);
	private final ColorOption durabilityColorDefault = new ColorOption("armorhud.durability_color_default",
		new Color(0xFF404040));
	// display_name: the item's name next to (or under) its icon, in name_color.
	private final BooleanOption displayName = new BooleanOption("armorhud.display_name", false);
	private final ColorOption nameColor = new ColorOption("armorhud.name_color", Colors.WHITE);
	// low_effect: a tint over items whose remaining durability percent has dropped to the
	// threshold or below.
	private final BooleanOption lowEffect = new BooleanOption("armorhud.low_effect", false);
	private final IntegerOption lowEffectThreshold = new IntegerOption("armorhud.low_effect_threshold", 20, 1, 100);
	private final ColorOption lowEffectColor = new ColorOption("armorhud.low_effect_color", new Color(0x80FF3030));
	private final EnumOption<MainHandItemPosition> mainHandItemPosition = new EnumOption<>("armorhud" +
		".main_hand_item_position", MainHandItemPosition.class, MainHandItemPosition.BOTTOM);
	private final EnumOption<CardinalOrder> order = DefaultOptions.getCardinalOrder(CardinalOrder.LEFT_RIGHT);
	private final BooleanOption reverseArmorOrder = new BooleanOption("armorhud.reverse_armor_order", false);
	private final BooleanOption hideIfEmpty = DefaultOptions.getHideIfEmpty();

	public ArmorHud() {
		super(20, 100, true);
	}

	@Override
	public void render(AxoRenderContext ctx, float delta) {
		final var player = client.br$getPlayer();
		if (player == null) {
			return;
		}
		if (hideIfEmpty.get() && Stream.concat(mainHandItemPosition.get() == MainHandItemPosition.DISABLED ? Stream.empty() : Stream.of(player.br$getInventory().br$getMainHand()), player.br$getInventory().br$getArmor().stream()).allMatch(AxoItemStack::br$isEmpty)) {
			return;
		}
		super.render(ctx, delta);
	}

	@Override
	public void renderComponent(AxoRenderContext graphics, float delta) {
		final var player = client.br$getPlayer();
		if (player == null) {
			return;
		}

		final var mainHand = player.br$getInventory().br$getMainHand();

		renderInternal(
			graphics,
			mainHand,
			player.br$getInventory().br$getArmor(),
			ItemUtil.getTotal(client, mainHand.br$getItem())
		);
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		if (BridgeVersion.is26OrLater()) {
			if (client.br$getWorld() == null) {
				var w = order.get().isXAxis() ? 20 : 100;
				var h = order.get().isXAxis() ? 100 : 20;
				var updated = false;
				if (mainHandItemPosition.get() == MainHandItemPosition.DISABLED) {
					if (order.get().isXAxis()) h -= 20;
					else w -= 20;
				}
				if (w != getContentWidth()) {
					setContentWidth(w);
					updated = true;
				}
				if (h != getContentHeight()) {
					setContentHeight(h);
					updated = true;
				}
				if (updated) {
					onBoundsUpdate();
				}
				var pos = getContentPos();
				graphics.br$pushMatrix();
				if (order.get().isXAxis()) {
					graphics.br$rotateMatrixAround((float) Math.PI / 2f, pos.x() + getContentWidth() / 2f, pos.y() + getContentHeight() / 2f);
				}
				graphics.br$drawCenteredString(getName(), pos.x() + getContentWidth() / 2, pos.y() + getContentHeight() / 2 - graphics.br$getFont().br$getFontHeight() / 2, textColor.get());
				graphics.br$popMatrix();
				return;
			}
		}
		renderInternal(graphics, PLACEHOLDER_MAIN_HAND.get(), PLACEHOLDER_GEAR.get(), 1);
	}

	private void renderInternal(
		AxoRenderContext context,
		AxoItemStack mainHand, List<? extends AxoItemStack> armor,
		int mainHandCount
	) {
		int width = 20;
		int height = 100;
		boolean boundsChanged = false;
		DurabilityFormat format = effectiveDurabilityFormat();

		if (reverseArmorOrder.get()) {
			armor = new ArrayList<>(armor);
			Collections.reverse(armor);
		}

		MainHandItemPosition mhPos = mainHandItemPosition.get();
		if (hideIfEmpty.get() && mhPos != MainHandItemPosition.DISABLED && mainHand.br$isEmpty()) {
			mhPos = MainHandItemPosition.DISABLED;
		}
		var order = this.order.get();
		if (order.isXAxis()) {
			int labelWidth = format != DurabilityFormat.NONE || displayName.get() ?
				(mhPos == MainHandItemPosition.DISABLED ? armor.stream() : Stream.concat(Stream.of(mainHand), armor.stream()))
				.mapToInt(stack -> xAxisLabelWidth(context, stack, format)).max().orElse(0) : 0;
			width += labelWidth;
			if (width != getContentWidth()) {
				setContentWidth(width);
				boundsChanged = true;
			}

			if (mhPos == MainHandItemPosition.DISABLED) {
				height -= 20;
			}

			if (height != getContentHeight()) {
				setContentHeight(height);
				boundsChanged = true;
			}
			if (boundsChanged) {
				onBoundsUpdate();
			}
			DrawPosition pos = getContentPos();

			int lastY = 2 + (height - 20);

			if (mhPos == MainHandItemPosition.BOTTOM) {
				var x = pos.x() + 2;
				var y = pos.y() + lastY;
				if (order.getDirection() == 1) {
					renderXAxisLabel(context, mainHand, x, y);
					renderMainItem(context, mainHand, x + labelWidth, y, mainHandCount);
				} else {
					renderMainItem(context, mainHand, x, y, mainHandCount);
					renderXAxisLabel(context, mainHand, x + 18, y);
				}
				lastY -= 20;
			}

			for (AxoItemStack stack : armor) {
				String label = null;

				if (showProtLvl.get() && stack.br$hasEnchantment(AxoEnchants.PROTECTION)) {
					label = String.valueOf(stack.br$getEnchantment(AxoEnchants.PROTECTION));
				}

				var x = pos.x() + 2;
				var y = pos.y() + lastY;
				if (order.getDirection() == 1) {
					renderXAxisLabel(context, stack, x, y);
					renderItem(context, stack, x + labelWidth, y, label);
				} else {
					renderItem(context, stack, x, y, label);
					renderXAxisLabel(context, stack, x + 18, y);
				}
				lastY -= 20;
			}

			if (mhPos == MainHandItemPosition.TOP) {
				var x = pos.x() + 2;
				var y = pos.y() + lastY;
				if (order.getDirection() == 1) {
					renderXAxisLabel(context, mainHand, x, y);
					renderMainItem(context, mainHand, x + labelWidth, y, mainHandCount);
				} else {
					renderMainItem(context, mainHand, x, y, mainHandCount);
					renderXAxisLabel(context, mainHand, x + 18, y);
				}
			}
		} else {
			int labelWidth = format != DurabilityFormat.NONE ?
				(mhPos == MainHandItemPosition.DISABLED ? armor.stream() : Stream.concat(Stream.of(mainHand), armor.stream()))
				.mapToInt(stack -> {
					if (format == DurabilityFormat.NUMBER_AND_MAX) {
						var text1 = String.valueOf(stack.br$getMaxDamage() - stack.br$getDamage());
						var text2 = "/" + stack.br$getMaxDamage();
						int t1W = context.br$getFont().br$getWidth(text1);
						int t2W = context.br$getFont().br$getWidth(text2);
						return Math.max(t1W, t2W);
					}
					if (format == DurabilityFormat.BARS) {
						return BAR_WIDTH;
					}
					return context.br$getFont().br$getWidth(durabilityText(stack, format));
				}).map(i -> i + 2).max().orElse(0) : 0;
			if (displayName.get()) {
				int nameWidth = (mhPos == MainHandItemPosition.DISABLED ? armor.stream() : Stream.concat(Stream.of(mainHand), armor.stream()))
					.mapToInt(stack -> context.br$getFont().br$getWidth(stack.br$getHoverName())).max().orElse(0);
				labelWidth = Math.max(labelWidth, nameWidth + 2);
			}
			{
				int n = width;
				width = height - 6;
				height = n;
			}
			if (format != DurabilityFormat.NONE) {
				height += format == DurabilityFormat.NUMBER_AND_MAX ? 20 : 10;
			}
			if (displayName.get()) {
				height += 10;
			}
			if (labelWidth > 0) {
				width = (mhPos == MainHandItemPosition.DISABLED ? 4 : 5) * labelWidth + 2;
			}
			if (width != getContentWidth()) {
				setContentWidth(width);
				boundsChanged = true;
			}
			if (height != getContentHeight()) {
				setContentHeight(height);
				boundsChanged = true;
			}
			if (boundsChanged) {
				onBoundsUpdate();
			}
			DrawPosition pos = getContentPos();
			var x = pos.x() + 2;
			var y = pos.y() + 2;
			int stackWidth = 18;
			labelWidth = Math.max(stackWidth, labelWidth);
			if (mhPos == MainHandItemPosition.TOP) {
				if (order == CardinalOrder.TOP_DOWN) {
					var numHeight = renderDurabilityNumberYAxis(context, mainHand, x + labelWidth / 2, y);
					renderMainItem(context, mainHand, x - stackWidth / 2 + labelWidth / 2, y + numHeight, mainHandCount);
					renderNameYAxis(context, mainHand, x + labelWidth / 2, y + numHeight + 18);
				} else {
					renderMainItem(context, mainHand, x - stackWidth / 2 + labelWidth / 2, y, mainHandCount);
					var numHeight = renderDurabilityNumberYAxis(context, mainHand, x + labelWidth / 2, y + 18);
					renderNameYAxis(context, mainHand, x + labelWidth / 2, y + 18 + numHeight);
				}
				x += labelWidth;
			}
			for (var stack : armor) {
				String label = null;

				if (showProtLvl.get() && stack.br$hasEnchantment(AxoEnchants.PROTECTION)) {
					label = String.valueOf(stack.br$getEnchantment(AxoEnchants.PROTECTION));
				}

				if (order == CardinalOrder.TOP_DOWN) {
					var numHeight = renderDurabilityNumberYAxis(context, stack, x + labelWidth / 2, y);
					renderItem(context, stack, x - stackWidth / 2 + labelWidth / 2, y + numHeight, label);
					renderNameYAxis(context, stack, x + labelWidth / 2, y + numHeight + 18);
				} else {
					renderItem(context, stack, x - stackWidth / 2 + labelWidth / 2, y, label);
					var numHeight = renderDurabilityNumberYAxis(context, stack, x + labelWidth / 2, y + 18);
					renderNameYAxis(context, stack, x + labelWidth / 2, y + 18 + numHeight);
				}
				x += labelWidth;
			}
			if (mhPos == MainHandItemPosition.BOTTOM) {
				if (order == CardinalOrder.TOP_DOWN) {
					var numHeight = renderDurabilityNumberYAxis(context, mainHand, x + labelWidth / 2, y);
					renderMainItem(context, mainHand, x - stackWidth / 2 + labelWidth / 2, y + numHeight, mainHandCount);
					renderNameYAxis(context, mainHand, x + labelWidth / 2, y + numHeight + 18);
				} else {
					renderMainItem(context, mainHand, x - stackWidth / 2 + labelWidth / 2, y, mainHandCount);
					var numHeight = renderDurabilityNumberYAxis(context, mainHand, x + labelWidth / 2, y + 18);
					renderNameYAxis(context, mainHand, x + labelWidth / 2, y + 18 + numHeight);
				}
			}
		}
	}

	public void renderMainItem(AxoRenderContext graphics, AxoItemStack stack, int x, int y, int mainHandCount) {
		renderItem(graphics, stack, x, y, mainHandCount == 1 ? null : String.valueOf(mainHandCount));
	}

	public void renderItem(AxoRenderContext graphics, AxoItemStack stack, int x, int y, String labelOverride) {
		graphics.br$renderGuiItemModel(stack, x, y);
		graphics.br$renderGuiItemOverlay(stack, x, y, labelOverride, textColor.get().toInt(), shadow.get());
		if (lowEffect.get() && isLowDurability(stack)) {
			graphics.br$fillRect(x, y, 18, 18, lowEffectColor.get().toInt());
		}
	}

	/** armor_status/low_effect: remaining durability percent at or below the threshold. */
	private boolean isLowDurability(AxoItemStack stack) {
		if (stack.br$isEmpty() || stack.br$getMaxDamage() <= 0) {
			return false;
		}
		float remainingPercent = (stack.br$getMaxDamage() - stack.br$getDamage()) * 100F / stack.br$getMaxDamage();
		return remainingPercent <= lowEffectThreshold.get();
	}

	/** Width of the shared name/durability label column of one item in the X-axis layout. */
	private int xAxisLabelWidth(AxoRenderContext context, AxoItemStack stack, DurabilityFormat format) {
		if (stack.br$isEmpty() || (format == DurabilityFormat.NONE && !displayName.get())) {
			return 0;
		}
		int width = 0;
		if (format != DurabilityFormat.NONE) {
			width = format == DurabilityFormat.BARS ? BAR_WIDTH : context.br$getFont().br$getWidth(durabilityText(stack, format));
		}
		if (displayName.get()) {
			width = Math.max(width, context.br$getFont().br$getWidth(stack.br$getHoverName()));
		}
		return width + 2;
	}

	/**
	 * X-axis label column: the item name (display_name, top line) plus the durability
	 * readout as text or a bar (bottom line, or centred when there is no name).
	 */
	private void renderXAxisLabel(AxoRenderContext graphics, AxoItemStack stack, int x, int y) {
		DurabilityFormat format = effectiveDurabilityFormat();
		if (stack.br$isEmpty()) {
			return;
		}
		boolean showName = displayName.get();
		boolean showDurability = format != DurabilityFormat.NONE && stack.br$getMaxDamage() > 0;
		if (!showName && !showDurability) {
			return;
		}
		int durabilityY = y + 10 - graphics.br$getFont().br$getFontHeight() / 2;
		if (showName) {
			graphics.br$drawString(stack.br$getHoverName(), x, y + 1, nameColor.get().toInt(), shadow.get());
			durabilityY = y + 11;
		}
		if (!showDurability) {
			return;
		}
		if (format == DurabilityFormat.BARS) {
			renderDurabilityBar(graphics, x, showName ? y + 15 : y + 9, BAR_WIDTH, stack);
		} else {
			graphics.br$drawString(durabilityText(stack, format), x, durabilityY, durabilityNumberColor(stack), shadow.get());
		}
	}

	/**
	 * The BARS durability readout: a {@code durability_color_default} track with a fill in
	 * the active durability colour (gradient / tiers / custom / vanilla bar colour).
	 */
	private void renderDurabilityBar(AxoRenderContext graphics, int x, int y, int width, AxoItemStack stack) {
		graphics.br$fillRect(x, y, width, BAR_HEIGHT, durabilityColorDefault.get().toInt());
		float fraction = (stack.br$getMaxDamage() - stack.br$getDamage()) / (float) stack.br$getMaxDamage();
		int fill = Math.round(width * fraction);
		if (fill > 0) {
			graphics.br$fillRect(x, y, Math.min(fill, width), BAR_HEIGHT, durabilityNumberColor(stack));
		}
	}

	/** display_name in the Y-axis layout: the item name centred under its column. */
	private void renderNameYAxis(AxoRenderContext graphics, AxoItemStack stack, int centerX, int y) {
		if (!displayName.get() || stack.br$isEmpty()) {
			return;
		}
		int textWidth = graphics.br$getFont().br$getWidth(stack.br$getHoverName());
		graphics.br$drawString(stack.br$getHoverName(), centerX - textWidth / 2, y, nameColor.get().toInt(), shadow.get());
	}

	private int renderDurabilityNumberYAxis(AxoRenderContext graphics, AxoItemStack stack, int x, int y) {
		DurabilityFormat format = effectiveDurabilityFormat();
		if (format == DurabilityFormat.NONE) {
			return 0;
		}
		if (stack.br$isEmpty() || stack.br$getMaxDamage() == 0) {
			return format == DurabilityFormat.NUMBER_AND_MAX ? 20 : 10;
		}
		if (format == DurabilityFormat.BARS) {
			renderDurabilityBar(graphics, x - BAR_WIDTH / 2, y + 4, BAR_WIDTH, stack);
			return 10;
		}
		int textY = y + 10 / 2 - graphics.br$getFont().br$getFontHeight() / 2;
		if (format == DurabilityFormat.NUMBER_AND_MAX) {
			var text1 = String.valueOf(stack.br$getMaxDamage() - stack.br$getDamage());
			var text2 = "/" + stack.br$getMaxDamage();
			int t1W = graphics.br$getFont().br$getWidth(text1);
			int t2W = graphics.br$getFont().br$getWidth(text2);
			graphics.br$drawString(text1, x - t1W / 2, textY, durabilityNumberColor(stack), true);
			graphics.br$drawString(text2, x - t2W / 2, textY + 10, durabilityNumberColor(stack), true);
			return 20;
		} else {
			var text = durabilityText(stack, format);
			graphics.br$drawString(text, x - graphics.br$getFont().br$getWidth(text) / 2, textY, durabilityNumberColor(stack), true);
			return 10;
		}
	}

	private DurabilityFormat effectiveDurabilityFormat() {
		DurabilityFormat format = durabilityFormat.get();
		if (format != DurabilityFormat.NONE) {
			return format;
		}
		// Legacy toggles keep working when no explicit format is selected.
		if (showDurabilityNumber.get() && showMaxDurabilityNumber.get()) {
			return DurabilityFormat.NUMBER_AND_MAX;
		}
		if (showDurabilityNumber.get()) {
			return DurabilityFormat.NUMBER;
		}
		if (showMaxDurabilityNumber.get()) {
			return DurabilityFormat.MAX;
		}
		return DurabilityFormat.NONE;
	}

	private static String durabilityText(AxoItemStack stack, DurabilityFormat format) {
		int remaining = stack.br$getMaxDamage() - stack.br$getDamage();
		return switch (format) {
			case NONE, BARS -> "";
			case NUMBER -> String.valueOf(remaining);
			case MAX -> String.valueOf(stack.br$getMaxDamage());
			case NUMBER_AND_MAX -> remaining + "/" + stack.br$getMaxDamage();
			case PERCENT -> stack.br$getMaxDamage() == 0 ? "0%" : Math.round(remaining * 100F / stack.br$getMaxDamage()) + "%";
		};
	}

	private int durabilityNumberColor(AxoItemStack stack) {
		if (stack.br$getMaxDamage() > 0) {
			// armor_status/durability gradient: interpolate start (fresh) -> end (about to
			// break); takes precedence over the tiers and the custom flat colour.
			if (durabilityGradient.get()) {
				float fraction = (stack.br$getMaxDamage() - stack.br$getDamage()) / (float) stack.br$getMaxDamage();
				return ClientColors.blend(durabilityColorEnd.get(), durabilityColorStart.get(), fraction).toInt();
			}
			if (durabilityColorTiers.get()) {
				float percent = (stack.br$getMaxDamage() - stack.br$getDamage()) / (float) stack.br$getMaxDamage();
				if (percent > 0.5F) {
					return Colors.GREEN.toInt();
				}
				if (percent > 0.2F) {
					return Colors.YELLOW.toInt();
				}
				return Colors.RED.toInt();
			}
		}
		return customDurabilityNumColor.get() ? durabilityNumColor.get().toInt() :
			ClientColors.ARGB.opaque(stack.br$getBarColor());
	}

	@Override
	public AxoIdentifier getId() {
		return ID;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(showProtLvl);
		options.add(showDurabilityNumber);
		options.add(showMaxDurabilityNumber);
		options.add(durabilityFormat);
		options.add(durabilityGradient);
		options.add(durabilityColorStart);
		options.add(durabilityColorEnd);
		options.add(durabilityColorDefault);
		options.add(durabilityColorTiers);
		options.add(customDurabilityNumColor);
		options.add(durabilityNumColor);
		options.add(displayName);
		options.add(nameColor);
		options.add(lowEffect);
		options.add(lowEffectThreshold);
		options.add(lowEffectColor);
		options.add(mainHandItemPosition);
		options.add(order);
		options.add(reverseArmorOrder);
		options.add(hideIfEmpty);
		return options;
	}

	@Override
	protected AnchorPoint getDefaultAnchor() {
		return AnchorPoint.TOP_RIGHT;
	}

	private enum MainHandItemPosition {
		BOTTOM,
		TOP,
		DISABLED,
		;

		@Override
		public String toString() {
			return "armorhud.main_hand_item_position." + super.toString().toLowerCase(Locale.ROOT);
		}
	}

	private enum DurabilityFormat {
		NONE,
		NUMBER,
		MAX,
		NUMBER_AND_MAX,
		PERCENT,
		BARS,
		;

		@Override
		public String toString() {
			return "armorhud.durability_format." + super.toString().toLowerCase(Locale.ROOT);
		}
	}

	private static final int BAR_WIDTH = 18;
	private static final int BAR_HEIGHT = 2;
}
