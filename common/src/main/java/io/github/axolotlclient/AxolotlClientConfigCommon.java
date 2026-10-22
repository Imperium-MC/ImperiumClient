/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.*;
import io.github.axolotlclient.bridge.events.Events;
import io.github.axolotlclient.bridge.key.AxoKeybinding;
import io.github.axolotlclient.bridge.key.AxoKeys;
import io.github.axolotlclient.util.options.ForceableBooleanOption;
import io.github.axolotlclient.util.options.GenericOption;
import net.fabricmc.loader.api.FabricLoader;

public abstract class AxolotlClientConfigCommon {
	public enum MenuButtonMode {
		DISABLED,
		MODMENU() {
			@Override
			public boolean showButton() {
				return !(FabricLoader.getInstance().isModLoaded("modmenu") && !FabricLoader.getInstance().isModLoaded("axolotlclient-modmenu"));
			}
		},
		ALWAYS() {
			@Override
			public boolean showButton() {
				return true;
			}
		};

		@Override
		public String toString() {
			return "menu_button_mode." + super.toString().toLowerCase(Locale.ROOT);
		}

		public boolean showButton() {
			return false;
		}

	}
	public enum TabBadgeMode {
		BEFORE_NAME,
		BEFORE_NAME_ALIGNED,
		BEFORE_PING;

		@Override
		public String toString() {
			return "tab_badge_mode." + super.toString().toLowerCase(Locale.ROOT);
		}

	}

	/**
	 * Block overlay highlight modes: outline lines only (vanilla default), a translucent fill
	 * of the targeted block's faces, or both at once.
	 */
	public enum OutlineMode {
		OUTLINE,
		FILL,
		BOTH;

		@Override
		public String toString() {
			return "block_outlines.mode." + super.toString().toLowerCase(Locale.ROOT);
		}

	}

	public enum NametagBackgroundMode {
		NONE,
		TRANSLUCENT,
		OPAQUE;

		@Override
		public String toString() {
			return "nametagBackground." + super.toString().toLowerCase(Locale.ROOT);
		}

	}

	public enum WeatherOverride {
		OFF,
		CLEAR,
		RAIN,
		THUNDER;

		@Override
		public String toString() {
			return "weather." + super.toString().toLowerCase(Locale.ROOT);
		}

	}

	/**
	 * Hurt camera tilt modes: vanilla tilt, a scaled-down tilt (30%), or none at all.
	 * Composes with the vanilla accessibility damage-tilt-strength slider.
	 */
	public enum HurtCamMode {
		FULL,
		SUBTLE,
		OFF;

		@Override
		public String toString() {
			return "hurt_cam." + super.toString().toLowerCase(Locale.ROOT);
		}

		/**
		 * Multiplier applied to the hurt-camera roll angle; OFF cancels the roll entirely.
		 */
		public float tiltMultiplier() {
			return this == SUBTLE ? 0.3F : 1.0F;
		}
	}

	/**
	 * How the low-health tint is drawn: the vanilla vignette texture tinted towards the
	 * configured colour, or a flat translucent fill of the whole screen.
	 */
	public enum LowHpTintStyle {
		VIGNETTE,
		FLAT;

		@Override
		public String toString() {
			return "low_hp_tint.style." + super.toString().toLowerCase(Locale.ROOT);
		}

	}

	/**
	 * When the built-in frame limiter (performance category, M3) is allowed to cap the
	 * framerate: never, only while the window is minimized, or whenever the window is
	 * minimized or unfocused. The AFK caps escalate on top of whichever window state
	 * applies; the limiter always defers to vanilla's inactivity throttling when vanilla
	 * is already the stricter of the two (see {@link io.github.axolotlclient.util.FrameLimiter}).
	 */
	public enum FrameLimiterMode {
		OFF,
		MINIMIZED,
		UNFOCUSED;

		@Override
		public String toString() {
			return "limiter." + super.toString().toLowerCase(Locale.ROOT);
		}

		/**
		 * Whether the limiter's window-state condition holds; AFK caps may only apply on
		 * top of this (OFF gates everything).
		 */
		public boolean applies(boolean minimized, boolean focused) {
			return switch (this) {
				case OFF -> false;
				case MINIMIZED -> minimized;
				case UNFOCUSED -> minimized || !focused;
			};
		}
	}

	/**
	 * Crop growth stage overlay style: text ("3/8") or a small progress bar near the crosshair.
	 */
	public enum FarmingOverlayStyle {
		TEXT,
		BAR;

		@Override
		public String toString() {
			return "farming.style." + super.toString().toLowerCase(Locale.ROOT);
		}

	}

	/**
	 * Visual identity of the Imperium module menu: the UI v3 Imperium theme (bronze on warm
	 * dark, docs/design/README.md), with the carved chiseled-stone theme and the original flat
	 * dark-and-gold look kept as fallbacks.
	 */
	public enum MenuTheme {
		IMPERIUM,
		CARVED,
		CLASSIC;

		@Override
		public String toString() {
			return "menu.theme." + super.toString().toLowerCase(Locale.ROOT);
		}

	}

	// options

	public final BooleanOption showOwnNametag = new BooleanOption("showOwnNametag", false);
	public final BooleanOption useShadows = new BooleanOption("useShadows", false);
	public final EnumOption<NametagBackgroundMode> nametagBackground = new EnumOption<>("nametagBackground", NametagBackgroundMode.class, NametagBackgroundMode.TRANSLUCENT);
	public final DoubleOption nametagScale = new DoubleOption("nametagScale", 1.0D, 0.5D, 2.0D);

	public final BooleanOption showBadges = new BooleanOption("showBadges", true);
	public final BooleanOption customBadge = new BooleanOption("customBadge", false);
	public final StringOption badgeText = new StringOption("badgeText", "");
	public final EnumOption<TabBadgeMode> tabBadgeMode = new EnumOption<>("tab_badge_mode", TabBadgeMode.class, TabBadgeMode.BEFORE_NAME);

	public final ForceableBooleanOption timeChangerEnabled = new ForceableBooleanOption("enabled", false);
	public final IntegerOption customTime = new IntegerOption("time", 0, 0, 24000);
	public final FloatOption timeMultiplier = new FloatOption("time_multiplier", 1.0F, 0.1F, 10.0F);
	public final EnumOption<WeatherOverride> weather = new EnumOption<>("weather", WeatherOverride.class, WeatherOverride.OFF);
	public final FloatOption weatherIntensity = new FloatOption("weather_intensity", 1.0F, 0.0F, 1.0F);

	public final BooleanOption dynamicFOV = new BooleanOption("dynamicFov", true);
	public final ForceableBooleanOption fullBright = new ForceableBooleanOption("fullBright", false);
	public final BooleanOption removeVignette = new BooleanOption("removeVignette", false);
	public final ForceableBooleanOption lowFire = new ForceableBooleanOption("lowFire", false);
	public final ColorOption hitColor = new ColorOption("hitColor", new Color(0x4DFF0000), this::updateHitColor);
	public final BooleanOption hitColorOnArmor = new BooleanOption("hit_color_on_armor", false);
	// hit_color/color_trim (Alpine parity): also flash armour TRIMS with the hit colour.
	public final BooleanOption hitColorOnTrims = new BooleanOption("hit_color_on_trims", false);

	public final BooleanOption minimalViewBob = new BooleanOption("minimalViewBob", false);
	public final EnumOption<HurtCamMode> hurtCam = new EnumOption<>("hurt_cam", HurtCamMode.class, HurtCamMode.FULL);
	public final BooleanOption hideChat = new BooleanOption("hide_chat", false);

	// clear_water (Alpine parity): push the underwater fog out towards the render distance.
	public final BooleanOption clearWater = new BooleanOption("clear_water", false);
	// clear_water/disable_liquid_fov: skip vanilla's fluid FOV shrink while underwater.
	public final BooleanOption clearWaterDisableLiquidFov = new BooleanOption("clear_water.disable_liquid_fov", false);
	// clear_water/overlay_transparency: alpha of the underwater screen overlay, in percent
	// (10 matches vanilla's 0.1; 0 removes the overlay entirely). Gated by clear_water.
	public final IntegerOption waterOverlayTransparency = new IntegerOption("clear_water.overlay_transparency", 10, 0, 100);

	// low_hp_tint (Alpine parity): red vignette/tint when below a health threshold.
	// The tint colour's alpha is the peak intensity reached at 1 hp.
	public final BooleanOption lowHpTint = new BooleanOption("enabled", false);
	public final IntegerOption lowHpThreshold = new IntegerOption("low_hp_threshold", 6, 1, 19);
	public final EnumOption<LowHpTintStyle> lowHpTintStyle = new EnumOption<>("low_hp_tint_style", LowHpTintStyle.class, LowHpTintStyle.VIGNETTE);
	public final ColorOption lowHpTintColor = new ColorOption("low_hp_tint_color", new Color(0x66FF0000));
	// low_hp_tint/adaptive_opacity: ramp the intensity with health (threshold -> 1 hp); when
	// off, the tint draws at the colour's full alpha the whole time it is below the threshold.
	// min_opacity floors the ramped alpha, in percent of the tint colour's alpha.
	public final BooleanOption lowHpAdaptiveOpacity = new BooleanOption("low_hp_tint.adaptive_opacity", true);
	public final IntegerOption lowHpMinOpacity = new IntegerOption("low_hp_tint.min_opacity", 0, 0, 100);

	// farming (Alpine parity): crop growth stage overlay near the crosshair.
	public final BooleanOption farmingOverlay = new BooleanOption("enabled", false);
	public final BooleanOption farmingHoeOnly = new BooleanOption("farming.hoe_only", false);
	public final EnumOption<FarmingOverlayStyle> farmingStyle = new EnumOption<>("farming.style", FarmingOverlayStyle.class, FarmingOverlayStyle.TEXT);

	// chunk_borders (Alpine parity): chunk (and optional region) boundary gizmo lines.
	public final BooleanOption chunkBorders = new BooleanOption("enabled", false);
	public final BooleanOption regionBorders = new BooleanOption("chunk_borders.regions", false);
	public final ColorOption chunkBorderColor = new ColorOption("chunk_borders.color", new Color(0x80FFFF00));
	public final ColorOption regionBorderColor = new ColorOption("chunk_borders.region_color", new Color(0x80FF5555));
	// chunk_borders/line_thickness: gizmo line width of the chunk cell (region lines are one
	// pixel heavier so they stay distinguishable at any thickness).
	public final IntegerOption chunkBorderThickness = new IntegerOption("chunk_borders.line_thickness", 2, 1, 10);

	// light_level (Photon parity): keybind-toggled spawn-safety overlay tinting block tops by
	// effective light level (green = light > 7 after the sky-darkening scale, red = mobs can spawn).
	public final BooleanOption lightOverlay = new BooleanOption("enabled", false);
	public final IntegerOption lightOverlayRadius = new IntegerOption("light_level.radius", 16, 8, 32);
	public final ColorOption lightOverlaySafeColor = new ColorOption("light_level.safe_color", new Color(0xA000E000));
	public final ColorOption lightOverlayUnsafeColor = new ColorOption("light_level.unsafe_color", new Color(0xA0FF3000));

	// performance (Alpine parity, M3): ImperiumClient's own frame limiter, replacing Dynamic
	// FPS, plus cheap render-hide toggles. The limiter defers to vanilla's inactivity
	// throttling whenever vanilla is already the stricter of the two.
	public final EnumOption<FrameLimiterMode> frameLimiter = new EnumOption<>("limiter", FrameLimiterMode.class, FrameLimiterMode.OFF);
	public final IntegerOption backgroundFps = new IntegerOption("background_fps", 30, 1, 60);
	public final IntegerOption afkFps = new IntegerOption("afk_fps", 15, 1, 60);
	public final IntegerOption afkDelay = new IntegerOption("afk_delay", 60, 1, 3600);
	public final IntegerOption longAfkFps = new IntegerOption("long_afk_fps", 5, 1, 60);
	public final IntegerOption longAfkDelay = new IntegerOption("long_afk_delay", 300, 1, 7200);
	public final BooleanOption disableTntFlash = new BooleanOption("disable_tnt_flash", false);
	public final BooleanOption hideLightningBolts = new BooleanOption("hide_lightning_bolts", false);
	public final BooleanOption hideHoppers = new BooleanOption("hide_hoppers", false);
	public final BooleanOption hideDistantEntities = new BooleanOption("hide_distant_entities", false);
	public final IntegerOption entityRenderDistance = new IntegerOption("entity_render_distance", 64, 16, 128);

	// hud_frame_limit (Alpine parity, M3): HUD elements whose content does not change on its
	// own re-record into the GUI render state at most this many times per second; between
	// records the previously recorded elements are replayed unchanged. 0 disables the cache.
	public final IntegerOption hudFrameLimit = new IntegerOption("hud_frame_limit", 0, 0, 120);

	// text_render_distance (Alpine parity, M3): sign and name-tag text is not rendered beyond
	// this distance. 0 keeps vanilla behaviour (everything in range renders).
	public final IntegerOption textRenderDistance = new IntegerOption("text_render_distance", 0, 0, 128);

	// disable_tnt_expansion (Alpine parity, M3): primed TNT keeps its normal size during the
	// final swell instead of scaling up shortly before exploding.
	public final BooleanOption disableTntExpansion = new BooleanOption("disable_tnt_expansion", false);

	// optimize_spawners / disable_enchant_table_books (Alpine parity, M3): skip the spinning
	// mob inside monster spawners and the floating book above enchanting tables.
	public final BooleanOption optimizeSpawners = new BooleanOption("optimize_spawners", false);
	public final BooleanOption disableEnchantTableBooks = new BooleanOption("disable_enchant_table_books", false);

	public final BooleanOption compactChat = new BooleanOption("compact_chat", false);

	// compact_chat sub-settings (Alpine profile M6): time-based stacking re-finds the
	// earlier duplicate anywhere in the chat within the window instead of only at the
	// head; ignore_blank keeps whitespace-only lines from ever stacking; ignoring line
	// breaks normalizes embedded newlines before comparing.
	public final BooleanOption compactTimeBased = new BooleanOption("compact_time_based", false);
	public final IntegerOption compactTimeWindow = new IntegerOption("compact_time_window", 5, 1, 60);
	public final BooleanOption compactIgnoreBlank = new BooleanOption("compact_ignore_blank", true);
	public final BooleanOption compactIgnoreLineBreaks = new BooleanOption("compact_ignore_line_breaks", true);

	public final BooleanOption chatTimestamps = new BooleanOption("chat_timestamps", false);

	/** Chat timestamp layouts (Alpine "timestamp_format", M6). */
	public enum ChatTimestampFormat {
		HH_MM("HH:mm"),
		HH_MM_SS("HH:mm:ss"),
		HH_MM_12("h:mm a");

		private final String pattern;

		ChatTimestampFormat(String pattern) {
			this.pattern = pattern;
		}

		public java.time.format.DateTimeFormatter formatter() {
			return java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
		}

		@Override
		public String toString() {
			return "chat_timestamps.format." + name().toLowerCase(Locale.ROOT);
		}
	}

	public final EnumOption<ChatTimestampFormat> chatTimestampFormat =
		new EnumOption<>("chat_timestamps.format", ChatTimestampFormat.class, ChatTimestampFormat.HH_MM);
	public final BooleanOption chatEmptyFilter = new BooleanOption("empty_chat_filter", false);
	public final BooleanOption chatNameHighlight = new BooleanOption("name_highlight", false);
	public final BooleanOption chatNamePing = new BooleanOption("name_highlight_ping", true);
	public final BooleanOption chatHoverHighlight = new BooleanOption("hover_highlight", false);
	public final ColorOption chatHoverHighlightColor = new ColorOption("hover_highlight_color", new Color(0x33FFFFFF));
	public final BooleanOption chatPeek = new BooleanOption("peek_chat", false);
	public final AxoKeybinding peekChatKey;

	// smooth_chat: the chat scroll offset lerps toward its target over a short window instead
	// of snapping line-by-line (new messages slide in, wheel scrolls glide).
	public final BooleanOption smoothChat = new BooleanOption("smooth_chat", true);
	public final IntegerOption smoothChatDuration = new IntegerOption("smooth_chat_duration_ms", 120, 50, 400);

	// persistent_chat: the visible chat history is serialized on disconnect and restored on
	// the next join, so it survives relogs.
	public final BooleanOption persistentChat = new BooleanOption("persistent_chat", true);
	public final AxoKeybinding clearChatHistoryKey;

	// chat_screenshot_button: draws a small camera button on the chat screen that saves a
	// cropped screenshot of just the chat region.
	public final BooleanOption chatScreenshotButton = new BooleanOption("chat_screenshot_button", true);

	public final BooleanOption enableCustomOutlines = new BooleanOption("enabled", false);
	public final ColorOption outlineColor = new ColorOption("color", new Color(0x66000000));
	public final IntegerOption outlineWidth = new IntegerOption("outlineWidth", 1, 1, 15);
	public final EnumOption<OutlineMode> outlineMode = new EnumOption<>("block_outlines.mode", OutlineMode.class,
		OutlineMode.OUTLINE);
	public final BooleanOption outlineFill = new BooleanOption("block_outlines.fill", false);
	public final ColorOption outlineFillColor = new ColorOption("block_outlines.fill_color", Colors.WINE_RED.withAlpha(165));

	public final BooleanOption customWindowTitle = new BooleanOption("customWindowTitle", true, this::updateWindowTitle);
	public final BooleanOption confirmDisconnect = new BooleanOption("confirm_disconnect", false);

	public final OptionCategory general = OptionCategory.create("general");
	public final OptionCategory nametagOptions = OptionCategory.create("nametagOptions");
	public final OptionCategory rendering = OptionCategory.create("rendering");
	public final OptionCategory outlines = OptionCategory.create("blockOutlines");
	public final OptionCategory timeChanger = OptionCategory.create("timeChanger");
	public final OptionCategory chatSettings = OptionCategory.create("chatSettings");
	public final OptionCategory lowHpTintCategory = OptionCategory.create("lowHpTint");
	public final OptionCategory farming = OptionCategory.create("farming");
	public final OptionCategory chunkBordersCategory = OptionCategory.create("chunkBorders");
	public final OptionCategory lightOverlayCategory = OptionCategory.create("lightLevelOverlay");
	public final OptionCategory performance = OptionCategory.create("performance");

	public final BooleanOption creditsBGM = new BooleanOption("creditsBGM", true);
	public final BooleanOption modifyClientBrand = new BooleanOption("modify_client_brand", true);
	public final BooleanOption debugLogOutput = new BooleanOption("debugLogOutput", false);

	public final BooleanOption noRain = new BooleanOption("noRain", false);

	public final BooleanOption noAltIcons = new BooleanOption("no_alt_icons", false);

	public final OptionCategory config = OptionCategory.create("config");
	public final OptionCategory hidden = OptionCategory.create("storedOptions");
	public final BooleanOption someNiceBackground = new BooleanOption("defNoSecret", false);
	public final StringOption datetimeFormat = new StringOption("datetime_format", "yyyy/MM/dd HH:mm:ss", s -> dateTimeFormatter = DateTimeFormatter.ofPattern(s));
	public final EnumOption<MenuButtonMode> titleScreenOptionButtonMode = new EnumOption<>("title_screen_button_mode", MenuButtonMode.class, MenuButtonMode.MODMENU);
	public final EnumOption<MenuButtonMode> gameMenuScreenOptionButtonMode = new EnumOption<>("game_menu_screen_button_mode", MenuButtonMode.class, MenuButtonMode.MODMENU);

	// menu.theme: Imperium (UI v3) is the default, CARVED/CLASSIC stay as fallbacks.
	public final EnumOption<MenuTheme> menuTheme = new EnumOption<>("menu.theme", MenuTheme.class, MenuTheme.IMPERIUM);

	// Spec "Reduce motion": disables every menu animation (nav pill slide, hover colour/scale).
	public final BooleanOption menuReduceMotion = new BooleanOption("menu.reduce_motion", false);

	public DateTimeFormatter dateTimeFormatter;

	public AxolotlClientConfigCommon() {
		config.add(general);
		config.add(rendering);
		config.add(chatSettings);
		config.add(hidden);

		chatSettings.add(compactChat);
		chatSettings.add(compactTimeBased, compactTimeWindow);
		chatSettings.add(compactIgnoreBlank, compactIgnoreLineBreaks);
		chatSettings.add(chatTimestamps);
		chatSettings.add(chatTimestampFormat);
		chatSettings.add(chatEmptyFilter);
		chatSettings.add(chatNameHighlight, chatNamePing);
		chatSettings.add(chatHoverHighlight, chatHoverHighlightColor);
		chatSettings.add(chatPeek);
		chatSettings.add(smoothChat, smoothChatDuration);
		chatSettings.add(persistentChat);
		chatSettings.add(chatScreenshotButton);

		rendering.add(outlines);
		rendering.add(nametagOptions);

		nametagOptions.add(showOwnNametag);
		nametagOptions.add(useShadows);
		nametagOptions.add(nametagBackground);
		nametagOptions.add(nametagScale);

		general.add(customWindowTitle);
		general.add(debugLogOutput);

		general.add(datetimeFormat);
		general.add(titleScreenOptionButtonMode);
		general.add(gameMenuScreenOptionButtonMode);
		general.add(menuTheme);
		general.add(menuReduceMotion);
		general.add(confirmDisconnect);

		timeChanger.add(timeChangerEnabled);
		timeChanger.add(customTime);
		timeChanger.add(timeMultiplier);
		timeChanger.add(new GenericOption("time_changer.preset.day", "time_changer.set_preset", () -> customTime.set(1000)));
		timeChanger.add(new GenericOption("time_changer.preset.noon", "time_changer.set_preset", () -> customTime.set(6000)));
		timeChanger.add(new GenericOption("time_changer.preset.night", "time_changer.set_preset", () -> customTime.set(13000)));
		timeChanger.add(new GenericOption("time_changer.preset.midnight", "time_changer.set_preset", () -> customTime.set(18000)));
		timeChanger.add(weather);
		timeChanger.add(weatherIntensity);

		outlines.add(enableCustomOutlines);
		outlines.add(outlineColor);
		outlines.add(outlineWidth, outlineMode, outlineFill, outlineFillColor);

		rendering.add(timeChanger);

		rendering.add(
			dynamicFOV,
			fullBright,
			removeVignette,
			lowFire,
			minimalViewBob,
			hurtCam,
			clearWater,
			clearWaterDisableLiquidFov,
			waterOverlayTransparency,
			noRain,
			hideChat,
			hitColor,
			hitColorOnArmor,
			hitColorOnTrims
		);

		lowHpTintCategory.add(lowHpTint);
		lowHpTintCategory.add(lowHpThreshold);
		lowHpTintCategory.add(lowHpTintStyle);
		lowHpTintCategory.add(lowHpTintColor);
		lowHpTintCategory.add(lowHpAdaptiveOpacity);
		lowHpTintCategory.add(lowHpMinOpacity);
		rendering.add(lowHpTintCategory);

		farming.add(farmingOverlay);
		farming.add(farmingHoeOnly);
		farming.add(farmingStyle);
		rendering.add(farming);

		chunkBordersCategory.add(chunkBorders);
		chunkBordersCategory.add(regionBorders);
		chunkBordersCategory.add(chunkBorderThickness);
		chunkBordersCategory.add(chunkBorderColor);
		chunkBordersCategory.add(regionBorderColor);
		rendering.add(chunkBordersCategory);

		lightOverlayCategory.add(lightOverlay);
		lightOverlayCategory.add(lightOverlayRadius);
		lightOverlayCategory.add(lightOverlaySafeColor);
		lightOverlayCategory.add(lightOverlayUnsafeColor);
		rendering.add(lightOverlayCategory);

		performance.add(frameLimiter, backgroundFps, afkFps, afkDelay, longAfkFps, longAfkDelay);
		performance.add(disableTntFlash, hideLightningBolts, hideHoppers, hideDistantEntities, entityRenderDistance);
		performance.add(hudFrameLimit, textRenderDistance);
		performance.add(disableTntExpansion, optimizeSpawners, disableEnchantTableBooks);
		config.add(performance);

		hidden.add(creditsBGM, someNiceBackground, modifyClientBrand, noAltIcons);

		AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "toggle_hide_chat").br$registerOnConsumeClick(() -> {
			hideChat.toggle();
			AxolotlClientCommon.getInstance().saveConfig();
		});
		peekChatKey = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "peek_chat");
		clearChatHistoryKey = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "clear_chat_history");
		AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "toggle_fullbright").br$registerOnConsumeClick(() -> {
			fullBright.toggle();
			AxolotlClientCommon.getInstance().saveConfig();
		});
		AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "toggle_chunk_borders").br$registerOnConsumeClick(() -> {
			chunkBorders.toggle();
			AxolotlClientCommon.getInstance().saveConfig();
		});
		AxoKeybinding.create(AxoKeys.KEY_F7, "toggle_light_overlay").br$registerOnConsumeClick(() -> {
			lightOverlay.toggle();
			AxolotlClientCommon.getInstance().saveConfig();
		});

		Events.TICK.register(() -> {
			if (hitColor.getOriginal().isChroma()) {
				updateHitColor(hitColor.get());
			}
		});
	}

	public DateTimeFormatter getDateTimeFormatter() {
		if (dateTimeFormatter == null) {
			dateTimeFormatter = DateTimeFormatter.ofPattern(datetimeFormat.get());
		}

		return dateTimeFormatter;
	}

	public static AxolotlClientConfigCommon instance() {
		return AxolotlClientCommon.getInstance().getConfig();
	}

	public final void add(Option<?> option) {
		config.add(option);
	}

	public final void addCategory(OptionCategory cat) {
		config.add(cat);
	}

	public final OptionCategory getConfig() {
		return config;
	}

	protected abstract void updateWindowTitle(boolean useCustom);

	protected abstract void updateHitColor(Color color);
}
