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

import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption;
import io.github.axolotlclient.AxolotlClientConfig.impl.util.GraphicsImpl;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.config.profiles.ProfileAware;
import io.github.axolotlclient.modules.hud.ClickInputTracker;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.gui.keystrokes.KeystrokePositioningScreen;
import io.github.axolotlclient.modules.hud.gui.keystrokes.KeystrokesScreen;
import io.github.axolotlclient.modules.hud.gui.layout.Justification;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import io.github.axolotlclient.modules.hud.util.Rectangle;
import io.github.axolotlclient.util.ClientColors;
import io.github.axolotlclient.util.GsonHelper;
import io.github.axolotlclient.util.events.Events;
import io.github.axolotlclient.util.options.GenericOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import static io.github.axolotlclient.util.DrawUtil.*;

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */

public class KeystrokeHud extends TextHudEntry implements ProfileAware {

	private static final String KEYSTROKE_SAVE_FILE_NAME = "keystrokes.json";
	public static final Identifier ID = Identifier.fromNamespaceAndPath("kronhud", "keystrokehud");

	private final Minecraft client = (Minecraft) super.client;

	private final ColorOption pressedTextColor = new ColorOption("heldtextcolor", new Color(0xFF000000));
	private final ColorOption pressedBackgroundColor = new ColorOption("heldbackgroundcolor", new Color(0x64FFFFFF));
	private final ColorOption pressedOutlineColor = new ColorOption("heldoutlinecolor", ClientColors.BLACK);

	private final GenericOption keystrokesOption = new GenericOption("keystrokes", "keystrokes.configure", () -> client.gui.setScreen(new KeystrokesScreen(KeystrokeHud.this, client.gui.screen())));
	private final GenericOption configurePositions = new GenericOption("keystrokes.positions", "keystrokes.positions.configure",
		() -> client.gui.setScreen(new KeystrokePositioningScreen(client.gui.screen(), this)));
	private final IntegerOption animationTime = new IntegerOption("keystrokes.animation_time", 100, 0, 500);
	public ArrayList<Keystroke> keystrokes;


	public KeystrokeHud() {
		super(53, 61, true);
		Events.KEYBIND_CHANGE.register(key -> {
			//noinspection ConstantValue
			if (Minecraft.getInstance().getWindow() != null) {
				KeyMapping.releaseAll();
				KeyMapping.setAll();
			}
		});
	}

	public static Optional<String> getMouseKeyBindName(KeyMapping keyBinding) {
		if (keyBinding.saveString().equalsIgnoreCase(
			InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_1).getName())) {
			return Optional.of("LMB");
		} else if (keyBinding.saveString().equalsIgnoreCase(
			InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_2).getName())) {
			return Optional.of("RMB");
		} else if (keyBinding.saveString().equalsIgnoreCase(
			InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_3).getName())) {
			return Optional.of("MMB");
		}
		return Optional.empty();
	}

	public void setDefaultKeystrokes() {
		DrawPosition pos = getContentPos();
		// LMB
		keystrokes.add(createFromKey(new Rectangle(0, 36, 26, 17), pos, client.options.keyAttack));
		// RMB
		keystrokes.add(createFromKey(new Rectangle(27, 36, 26, 17), pos, client.options.keyUse));
		// W
		keystrokes.add(createFromKey(new Rectangle(18, 0, 17, 17), pos, client.options.keyUp));
		// A
		keystrokes.add(createFromKey(new Rectangle(0, 18, 17, 17), pos, client.options.keyLeft));
		// S
		keystrokes.add(createFromKey(new Rectangle(18, 18, 17, 17), pos, client.options.keyDown));
		// D
		keystrokes.add(createFromKey(new Rectangle(36, 18, 17, 17), pos, client.options.keyRight));

		// Space
		keystrokes.add(new SpecialRenderKeystroke(SpecialKeystroke.SPACE));
	}

	public void setKeystrokes() {
		//noinspection ConstantValue
		if (client.getWindow() == null) {
			keystrokes = null;
			return;
			// Wait until render is called
		}
		keystrokes = new ArrayList<>();
		setDefaultKeystrokes();
		loadKeystrokes();
		KeyMapping.releaseAll();
		KeyMapping.setAll();
	}

	public Keystroke createFromKey(Rectangle bounds, DrawPosition offset, KeyMapping key) {
		String name = getMouseKeyBindName(key).orElse(key.getTranslatedKeyMessage().getString().toUpperCase());
		if (name.length() > 4) {
			name = name.substring(0, 2);
		}
		return createFromString(bounds, offset, key, name);
	}

	public Keystroke createFromString(Rectangle bounds, DrawPosition offset, KeyMapping key, String word) {
		return new LabelKeystroke(bounds, offset, key, word);
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
		if (keystrokes == null) {
			setKeystrokes();
		}
		for (Keystroke stroke : keystrokes) {
			stroke.render((GuiGraphicsExtractor) graphics);
		}
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float delta) {
		renderComponent(graphics, delta);
	}

	@Override
	public boolean tickable() {
		return true;
	}

	@Override
	public void tick() {
		DrawPosition pos = getContentPos();
		if (keystrokes == null) {
			setKeystrokes();
		}
		for (Keystroke stroke : keystrokes) {
			stroke.offset = pos;
		}
	}

	@Override
	protected boolean getShadowDefault() {
		return false;
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		// We want a specific order since this is a more complicated entry
		List<Option<?>> options = new ArrayList<>();
		options.add(enabled);
		options.add(scale);
		options.add(anchor);
		options.add(textColor);
		options.add(pressedTextColor);
		options.add(shadow);
		options.add(background);
		options.add(backgroundColor);
		options.add(pressedBackgroundColor);
		options.add(outline);
		options.add(outlineColor);
		options.add(pressedOutlineColor);
		options.add(roundBackground);
		options.add(backgroundRounding);
		options.add(animationTime);
		options.add(keystrokesOption);
		options.add(configurePositions);
		return options;
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public void reloadConfig() {
		keystrokes = null;
	}

	@Override
	public void saveConfig() {
		saveKeystrokes();
	}

	public interface KeystrokeRenderer {

		void render(Keystroke stroke, GuiGraphicsExtractor graphics);
	}

	public abstract class Keystroke {
		protected static final Supplier<String> NO_LABEL = () -> ChatFormatting.ITALIC + I18n.get("keystrokes.stroke.custom_renderer");

		@Getter
		@Setter
		protected KeyMapping key;
		protected KeystrokeRenderer render;
		@Getter
		protected final Rectangle bounds;
		protected DrawPosition offset;
		private long start = -1;
		private boolean wasPressed = false;

		public Keystroke(Rectangle bounds, DrawPosition offset, KeyMapping key, KeystrokeRenderer render) {
			this.bounds = bounds;
			this.offset = offset;
			this.key = key;
			this.render = render;
		}

		public void setX(int x) {
			bounds.x(x - offset.x());
		}

		public void setY(int y) {
			bounds.y(y - offset.y());
		}

		public Rectangle getRenderPosition() {
			return bounds.offset(offset);
		}

		public Color getFGColor() {
			return isKeyDown() ? ClientColors.blend(textColor.get(), pressedTextColor.get(), getPercentPressed())
				: ClientColors.blend(pressedTextColor.get(), textColor.get(), getPercentPressed());
		}

		private float getPercentPressed() {
			return start == -1 ? 1 : Mth.clamp((float) (Util.getMillis() - start) / getAnimTime(), 0, 1);
		}

		public void render(GuiGraphicsExtractor matrices) {
			renderStroke(matrices);
			render.render(this, matrices);
		}

		public void renderStroke(GuiGraphicsExtractor matrices) {
			if (isKeyDown() != wasPressed) {
				start = Util.getMillis();
			}
			Rectangle rect = getRenderPosition();
			if (background.get()) {
				if (roundBackground.get()) {
					matrices.br$fillRectRound(rect, getColor(), Math.min(Math.min(rect.height(), rect.width()) / 2f, backgroundRounding.get()));
				} else {
					matrices.br$fillRect(rect, getColor());
				}
			}
			if (outline.get()) {
				if (roundBackground.get()) {
					matrices.br$outlineRectRound(rect, getOutlineColor(), Math.min(Math.min(rect.height(), rect.width()) / 2f, backgroundRounding.get()));
				} else {
					matrices.br$outlineRect(rect, getOutlineColor());
				}
			}
			if ((float) (Util.getMillis() - start) / getAnimTime() >= 1) {
				start = -1;
			}
			wasPressed = isKeyDown();
		}

		private int getAnimTime() {
			return animationTime.get();
		}

		private boolean isKeyDown() {
			return key != null && key.isDown();
		}

		public Color getColor() {
			return isKeyDown()
				? ClientColors.blend(backgroundColor.get(), pressedBackgroundColor.get(), getPercentPressed())
				: ClientColors.blend(pressedBackgroundColor.get(), backgroundColor.get(), getPercentPressed());
		}

		public Color getOutlineColor() {
			return isKeyDown() ? ClientColors.blend(outlineColor.get(), pressedOutlineColor.get(), getPercentPressed())
				: ClientColors.blend(pressedOutlineColor.get(), outlineColor.get(), getPercentPressed());
		}

		public Map<String, Object> serialize() {
			Map<String, Object> map = new HashMap<>();
			map.put("key", key.saveString());
			map.put("key_name", key.getName());
			map.put("bounds", Map.of("x", bounds.x(), "y", bounds.y(), "width", bounds.width(), "height", bounds.height()));
			return map;
		}

		public String getLabel() {
			return NO_LABEL.get();
		}

		public void setLabel(String label) {

		}

		public boolean isLabelEditable() {
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private Keystroke deserializeKey(Map<String, Object> json) {
		var type = (String) json.get("type");
		return switch (type) {
			case "custom_render" -> {
				var key = KeyMapping.get((String) json.get("key_name"));
				yield new CustomRenderKeystroke(getRectangle((Map<String, ?>) json.get("bounds")), getContentPos(), key, (String) json.get("graphics"));
			}
			case "option" -> {
				KeyMapping key = KeyMapping.get((String) json.getOrDefault("key_name", json.get("option")));
				yield new SpecialRenderKeystroke(SpecialKeystroke.byId.get(((String) json.get("special_name")).toLowerCase(Locale.ROOT)),
					getRectangle((Map<String, ?>) json.get("bounds")), getContentPos(), key);
			}
			default -> {
				var key = KeyMapping.get((String) json.get("key_name"));
				yield new LabelKeystroke(getRectangle((Map<String, ?>) json.get("bounds")), getContentPos(), key, (String) json.get("label"), (boolean) json.get("synchronize_label"),
					Justification.valueOf((String) json.getOrDefault("justification", "CENTER")));
			}
		};
	}

	private static Rectangle getRectangle(Map<String, ?> json) {
		return new Rectangle((int) (long) json.get("x"), (int) (long) json.get("y"), (int) (long) json.get("width"), (int) (long) json.get("height"));
	}

	public Keystroke newSpecialStroke(SpecialKeystroke stroke) {
		return new SpecialRenderKeystroke(stroke);
	}

	public LabelKeystroke newStroke() {
		return new LabelKeystroke(new Rectangle(0, 0, 17, 17), getContentPos(), null, "", false, Justification.CENTER);
	}

	public Keystroke newCustomStroke() {
		return new CustomRenderKeystroke(new Rectangle(0, 0, 17, 17), getContentPos(), null, null);
	}

	public class CustomRenderKeystroke extends Keystroke {
		public static final int DEFAULT_SIZE = 9;

		@Getter
		private final GraphicsOption graphics = new GraphicsOption("custom_render_stroke" + hashCode(), DEFAULT_SIZE, DEFAULT_SIZE);

		public CustomRenderKeystroke(Rectangle bounds, DrawPosition offset, KeyMapping key, @Nullable String graphic) {
			super(bounds, offset, key, (stroke, graphics) -> {
				var b = stroke.bounds;
				var xC = b.x() + stroke.offset.x() + b.width() / 2f;
				var yC = b.y() + stroke.offset.y() + b.height() / 2f;
				GraphicsOption g = ((CustomRenderKeystroke) stroke).getGraphics();
				var gW = g.get().getWidth();
				var gH = g.get().getHeight();
				int color = stroke.getFGColor().toInt();
				var texture = io.github.axolotlclient.util.Util.getTexture(g);
				graphics.br$pushMatrix();
				graphics.br$translateMatrix(xC - gW / 2f, yC - gH / 2f);
				if (shadow.get()) {
					graphics.br$translateMatrix(1, 1);
					graphics.axolotlclient_rendering$roundedBlit(texture, 0, 0, gW, gH, 0, 1f, 0, 1f, ClientColors.ARGB.scaleRGB(color, 0.25f), 0f);
					graphics.br$translateMatrix(-1, -1);
				}
				graphics.axolotlclient_rendering$roundedBlit(texture, 0, 0, gW, gH, 0, 1f, 0, 1f, color, 0f);
				graphics.br$popMatrix();
			});
			if (graphic != null) {
				graphics.fromSerializedValue(graphic);
			}
		}

		public int getSize() {
			return graphics.get().getWidth();
		}

		public void setSize(int size) {
			if (size != getSize()) {
				var newGraphics = new GraphicsImpl(size, size);
				graphics.get().copyTo(newGraphics);
				graphics.set(newGraphics);
			}
		}

		@Override
		public Map<String, Object> serialize() {
			var json = super.serialize();
			json.put("type", "custom_render");
			json.put("graphics", graphics.toSerializedValue());
			return json;
		}
	}

	public class SpecialRenderKeystroke extends Keystroke {
		private final SpecialKeystroke parent;

		public SpecialRenderKeystroke(SpecialKeystroke stroke, Rectangle bounds, DrawPosition offset, KeyMapping key) {
			super(bounds, offset, key, (s, g) -> stroke.getRenderer().render(KeystrokeHud.this, s, g));
			this.parent = stroke;
		}

		public SpecialRenderKeystroke(SpecialKeystroke stroke) {
			this(stroke, stroke.getRect().copy(), KeystrokeHud.this.getContentPos(), stroke.getKey());
		}
		@Override
		public Map<String, Object> serialize() {
			Map<String, Object> json = super.serialize();
			json.put("type", "option");
			json.put("special_name", parent.getId());
			return json;
		}

	}

	@Setter
	public class LabelKeystroke extends Keystroke {

		private String label;
		@Getter
		private boolean synchronizeLabel;
		@Getter
		private Justification justification;

		public LabelKeystroke(Rectangle bounds, DrawPosition offset, KeyMapping key, String label) {
			this(bounds, offset, key, label, true, Justification.CENTER);
		}

		public LabelKeystroke(Rectangle bounds, DrawPosition offset, KeyMapping key, String label, boolean synchronizeLabel, Justification justification) {
			super(bounds, offset, key, (stroke, matrices) -> {
				Rectangle strokeBounds = stroke.bounds;
				int x = strokeBounds.x() + stroke.offset.x() + 2 + ((LabelKeystroke) stroke).justification.getXOffset(stroke.getLabel(), strokeBounds.width() - 3);
				float y = strokeBounds.y() + stroke.offset.y() + ((float) strokeBounds.height() / 2) - 4;

				drawString(matrices, stroke.getLabel(), x, (int) y, stroke.getFGColor().toInt(), shadow.get());
			});
			this.label = label;
			setSynchronizeLabel(synchronizeLabel);
			this.justification = justification;
		}

		@Override
		public Map<String, Object> serialize() {
			Map<String, Object> json = super.serialize();
			json.put("type", "custom");
			json.put("label", label);
			json.put("synchronize_label", synchronizeLabel);
			json.put("justification", justification.name());
			return json;
		}

		public void setSynchronizeLabel(boolean synchronizeLabel) {
			if (synchronizeLabel) {
				String name = getMouseKeyBindName(key).orElse(key.getTranslatedKeyMessage().getString().toUpperCase());
				if (name.length() > 4) {
					name = name.substring(0, 2);
				}
				this.label = name;
			}
			this.synchronizeLabel = synchronizeLabel;
		}

		@Override
		public void setKey(KeyMapping key) {
			if (synchronizeLabel) {
				String name = getMouseKeyBindName(key).orElse(key.getTranslatedKeyMessage().getString().toUpperCase());
				if (name.length() > 4) {
					name = name.substring(0, 2);
				}
				this.label = name;
			}
			super.setKey(key);
		}

		@Override
		public String getLabel() {
			return label;
		}

		@Override
		public boolean isLabelEditable() {
			return true;
		}
	}

	public void saveKeystrokes() {
		if (keystrokes == null) return;
		try {
			var path = AxolotlClientCommon.resolveProfileConfigFile(KEYSTROKE_SAVE_FILE_NAME);
			Files.createDirectories(path.getParent());
			Files.writeString(path, GsonHelper.GSON.toJson(keystrokes.stream().map(Keystroke::serialize).toList()));
		} catch (Exception e) {
			AxolotlClientCommon.getInstance().getLogger().warn("Failed to save keystroke configuration!", e);
		}
	}

	@SuppressWarnings("unchecked")
	public void loadKeystrokes() {
		try {
			var path = AxolotlClientCommon.resolveProfileConfigFile(KEYSTROKE_SAVE_FILE_NAME);
			if (Files.exists(path)) {
				List<?> entries = (List<?>) GsonHelper.read(Files.readString(path));
				var loaded = entries.stream().map(e -> (Map<String, Object>) e)
					.map(KeystrokeHud.this::deserializeKey)
					.toList();
				if (keystrokes == null) {
					keystrokes = new ArrayList<>();
				} else {
					keystrokes.clear();
				}
				keystrokes.addAll(loaded);
			} else {
				saveKeystrokes();
			}
		} catch (Exception e) {
			AxolotlClientCommon.getInstance().getLogger().warn("Failed to load keystroke configuration, using defaults!", e);
		}
	}

	@AllArgsConstructor
	@Getter
	public enum SpecialKeystroke {
		SPACE("space", new Rectangle(0, 54, 53, 7), Minecraft.getInstance().options.keyJump, (hud, stroke, matrices) -> {
			Rectangle bounds = stroke.bounds;
			Rectangle spaceBounds = new Rectangle(bounds.x() + stroke.offset.x() + 4,
				bounds.y() + stroke.offset.y() + bounds.height() / 2 - 1, bounds.width() - 8, 1);
			fillRect(matrices, spaceBounds, stroke.getFGColor());
			if (hud.shadow.get()) {
				fillRect(matrices, spaceBounds.offset(1, 1), new Color(
					(stroke.getFGColor().toInt() & 16579836) >> 2 | stroke.getFGColor().toInt() & -16777216));
			}
		}),
		LMB_CPS("lmb_cps", new Rectangle(0, 36, 26, 17), Minecraft.getInstance().options.keyAttack, (hud, stroke, graphics) -> {
			Rectangle bounds = stroke.bounds;
			int centerX = bounds.x() + stroke.offset.x() + bounds.width() / 2;
			int y = bounds.y() + stroke.offset.y() + 3;
			int nameY = y + bounds.height() / 4 - hud.client.font.lineHeight / 2;
			drawCenteredString(graphics, hud.client.font, "LMB", centerX, nameY, stroke.getFGColor(), hud.shadow.get());
			int cpsY = y + bounds.height() * 3 / 4 - hud.client.font.lineHeight / 2;
			graphics.pose().pushMatrix();
			graphics.pose().translate(centerX, cpsY);
			graphics.pose().scale(0.5f, 0.5f);
			String cpsText = ClickInputTracker.getInstance().leftMouse.clicks() + " CPS";
			graphics.pose().translate(-hud.client.font.width(cpsText) / 2f, 0);
			drawString(graphics, cpsText, 0, 0, stroke.getFGColor(), hud.shadow.get());
			graphics.pose().popMatrix();
		}),
		RMB_CPS("rmb_cps", new Rectangle(27, 36, 26, 17), Minecraft.getInstance().options.keyUse, (hud, stroke, graphics) -> {
			Rectangle bounds = stroke.bounds;
			int centerX = bounds.x() + stroke.offset.x() + bounds.width() / 2;
			int y = bounds.y() + stroke.offset.y() + 3;
			int nameY = y + bounds.height() / 4 - hud.client.font.lineHeight / 2;
			drawCenteredString(graphics, hud.client.font, "RMB", centerX, nameY, stroke.getFGColor(), hud.shadow.get());
			int cpsY = y + bounds.height() * 3 / 4 - hud.client.font.lineHeight / 2;
			graphics.pose().pushMatrix();
			graphics.pose().translate(centerX, cpsY);
			graphics.pose().scale(0.5f, 0.5f);
			String cpsText = ClickInputTracker.getInstance().rightMouse.clicks() + " CPS";
			graphics.pose().translate(-hud.client.font.width(cpsText) / 2f, 0);
			drawString(graphics, cpsText, 0, 0, stroke.getFGColor(), hud.shadow.get());
			graphics.pose().popMatrix();
		});

		private static final Map<String, SpecialKeystroke> byId = Arrays.stream(values()).collect(Collectors.toMap(SpecialKeystroke::getId, Function.identity()));

		private final String id;
		private final Rectangle rect;
		private final KeyMapping key;
		private final SpecialKeystrokeRenderer renderer;

		public interface SpecialKeystrokeRenderer {
			void render(KeystrokeHud hud, KeystrokeHud.Keystroke stroke, GuiGraphicsExtractor graphics);
		}
	}
}
