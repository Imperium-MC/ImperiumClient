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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option;
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption;
import io.github.axolotlclient.bridge.render.AxoRenderContext;
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry;
import io.github.axolotlclient.modules.hud.util.DrawPosition;
import io.github.axolotlclient.util.DrawUtil;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.NotNull;

import static io.github.axolotlclient.util.DrawUtil.drawString;

public class PackDisplayHud extends TextHudEntry {

	public static final Identifier ID = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "packdisplayhud");
	public final List<PackWidget> widgets = new ArrayList<>();
	private final BooleanOption iconsOnly = new BooleanOption("iconsonly", false);

	// Alpine profile settings (M6): display_description adds the pack.mcmeta description
	// line under the name; trim_extension strips ".zip"/".mcpack" suffixes from titles.
	public final BooleanOption displayDescription = new BooleanOption("packdisplay.display_description", false);
	public final BooleanOption trimExtension = new BooleanOption("packdisplay.trim_extension", true);

	private final Minecraft client = (Minecraft) super.client;
	private PackWidget placeholder;

	public PackDisplayHud() {
		super(120, 18, true);
	}

	@Override
	public void renderComponent(AxoRenderContext context, float f) {
		final var graphics = (GuiGraphicsExtractor) context;

		DrawPosition pos = getContentPos();

		if (widgets.isEmpty()) init();

		if (background.get()) {
			DrawUtil.fillRect(graphics, getBounds(), backgroundColor.get());
		}

		if (outline.get()) DrawUtil.outlineRect(graphics, getBounds(), outlineColor.get());

		int y = pos.y() + 1;
		for (int i = widgets.size() - 1; i >= 0; i--) { // Badly reverse the order (I'm sure there are better ways to do this)
			widgets.get(i).render(graphics, pos.x() + 1, y);
			y += widgets.get(i).height();
		}
		if (y - pos.y() != getContentHeight()) {
			setContentHeight(y - pos.y());
			onBoundsUpdate();
		}
	}

	@Override
	public void init() {
		var selected = client.getResourcePackRepository().getSelectedPacks();
		var valid = selected.stream()
			.filter(p -> !(p.location().title().getContents() instanceof TranslatableContents tr && tr.getKey().matches("pack\\.name\\.fabricMods?")))
			.toList();
		var listSize = valid.size();
		valid.forEach(profile -> {
			profile.open().forEach(p -> {
				try (PackResources pack = p) {
					if (listSize == 1) {
						widgets.add(createWidget(profile.getTitle(), pack));
					} else if (!pack.packId().equalsIgnoreCase("vanilla")) {
						widgets.add(createWidget(profile.getTitle(), pack));
					}
				} catch (Exception ignored) {
				}
			});
		});

		AtomicInteger w = new AtomicInteger(20);
		widgets.forEach(packWidget -> {
			int textW = client.font.width(packWidget.getName()) + 20;
			if (textW > w.get()) w.set(textW);
		});
		setContentWidth(w.get());

		setContentHeight(widgets.stream().mapToInt(PackWidget::height).sum() + 1);
		onBoundsUpdate();
	}

	private PackWidget createWidget(Component displayName, PackResources pack) throws IOException, AssertionError {
		IoSupplier<@NotNull InputStream> supplier = pack.getRootResource("pack.png");
		assert supplier != null;
		InputStream stream = supplier.get();
		Identifier id = Identifier.fromNamespaceAndPath(ID.getNamespace(), ID.getPath() + "/" + pack.packId());
		client.getTextureManager().register(id, new DynamicTexture(id::toString, NativeImage.read(stream)));
		stream.close();
		String name = displayName.getString();
		if (trimExtension.get()) {
			name = name.replaceFirst("(?i)\\.(zip|mcpack)$", "");
		}
		return new PackWidget(name, readDescription(pack), id);
	}

	/** Best-effort pack.mcmeta description line; null when absent or unreadable. */
	private String readDescription(PackResources pack) {
		if (!displayDescription.get()) {
			return null;
		}
		IoSupplier<@NotNull InputStream> supplier = pack.getRootResource("pack.mcmeta");
		if (supplier == null) {
			return null;
		}
		try (InputStream meta = supplier.get()) {
			var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(meta, java.nio.charset.StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return null;
			}
			var description = root.getAsJsonObject().get("description");
			if (description != null && description.isJsonPrimitive()) {
				String text = description.getAsString();
				return text.isBlank() ? null : text;
			}
			if (description != null && description.isJsonObject() && description.getAsJsonObject().has("text")) {
				String text = description.getAsJsonObject().get("text").getAsString();
				return text.isBlank() ? null : text;
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	@Override
	public void renderPlaceholderComponent(AxoRenderContext graphics, float f) {
		boolean updateBounds = false;
		if (getContentHeight() < 18) {
			setContentHeight(18);
			updateBounds = true;
		}
		if (getContentWidth() < 56) {
			setContentWidth(56);
			updateBounds = true;
		}
		if (updateBounds) {
			onBoundsUpdate();
		}
		if (placeholder == null) {
			try (PackResources defaultPack = client.getVanillaPackResources().fullResources()) {
				placeholder = createWidget(Component.literal(defaultPack.packId()), defaultPack);
			} catch (Exception ignored) {
			}
		} else {
			placeholder.render((GuiGraphicsExtractor) graphics, getContentPos().x() + 1, getContentPos().y() + 1);
		}
	}

	@Override
	public List<Option<?>> getConfigurationOptions() {
		List<Option<?>> options = super.getConfigurationOptions();
		options.add(iconsOnly);
		options.add(displayDescription);
		options.add(trimExtension);
		return options;
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	public void update() {
		widgets.clear();
		init();
	}

	public class PackWidget {

		@Getter
		public final String name;
		private final String description;
		private final Identifier texture;

		public PackWidget(String name, String description, Identifier id) {
			this.name = name;
			this.description = description;
			texture = id;
		}

		/** Row height: 17 for an icon row, +9 when the description line is shown. */
		public int height() {
			return description != null ? 26 : 17;
		}

		public void render(GuiGraphicsExtractor graphics, int x, int y) {
			if (!iconsOnly.get()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, 16, 16, 16, 16);
			}
			drawString(graphics, name, x + 18, y + 16 / 2 - 9 / 2, textColor.get().toInt(), shadow.get());
			if (description != null) {
				drawString(graphics, description, x + 18, y + 16 / 2 - 9 / 2 + 9,
					textColor.get().withAlpha(170).toInt(), shadow.get());
			}
		}
	}
}
