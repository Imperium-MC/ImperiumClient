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

package io.github.axolotlclient.config.screen;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;

import io.github.axolotlclient.AxolotlClient;
import io.github.axolotlclient.credits.Credits;
import io.github.axolotlclient.util.DrawUtil;
import io.github.axolotlclient.util.ClientColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class CreditsScreen extends Screen {

	public static final HashMap<String, String[]> externalModuleCredits = new HashMap<>();
	private final Screen parent;
	private final SoundInstance bgm = SimpleSoundInstance.forUI(SoundEvents.MUSIC_DISC_CHIRP.value(), 1, 1);
	private final HeaderAndFooterLayout haF = new HeaderAndFooterLayout(this);
	private boolean initialized;

	public CreditsScreen(Screen parent) {
		super(Component.translatable("credits"));
		this.parent = parent;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (AxolotlClient.config().someNiceBackground.get()) { // Credit to pridelib for the colors
			graphics.fill(0, 0, width, height / 6, 0xFFff0018);
			graphics.fill(0, height / 6, width, height * 2 / 6, 0xFFffa52c);
			graphics.fill(0, height * 2 / 6, width, height / 2, 0xFFffff41);
			graphics.fill(0, height * 2 / 3, width, height * 5 / 6, 0xFF0000f9);
			graphics.fill(0, height / 2, width, height * 2 / 3, 0xFF008018);
			graphics.fill(0, height * 5 / 6, width, height, 0xFF86007d);
		} else {
			super.extractBackground(graphics, mouseX, mouseY, delta);
		}
	}

	@Override
	public void init() {
		if (initialized) {
			haF.arrangeElements();
			haF.visitWidgets(this::addRenderableWidget);
			return;
		}
		initialized = true;
		haF.addTitleHeader(getTitle(), getFont());
		haF.addToContents(new CreditsList(minecraft, haF.getWidth(), haF.getContentHeight(), haF.getHeaderHeight(), 25));

		var back = haF.addToFooter(Button.builder(CommonComponents.GUI_BACK, buttonWidget -> onClose()).build());

		haF.arrangeElements();
		haF.visitWidgets(this::addRenderableWidget);

		this.addRenderableWidget(Button.builder(Component.translatable("creditsBGM").append(": ")
				.append(Component.translatable(AxolotlClient.config().creditsBGM.get() ? "options.on" : "options.off")),
			buttonWidget -> {
				AxolotlClient.config().creditsBGM.toggle();
				AxolotlClient.getInstance().saveConfig();
				stopBGM();
				buttonWidget.setMessage(Component.translatable("creditsBGM").append(": ").append(
					Component.translatable(AxolotlClient.config().creditsBGM.get() ? "options.on" : "options.off")));
			}).bounds(6, back.getY(), 100, 20).build());
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
		stopBGM();
	}

	@Override
	protected void repositionElements() {
		haF.arrangeElements();
	}

	@Override
	public void tick() {
		tickBGM();
	}

	public void tickBGM() {
		if (AxolotlClient.config().creditsBGM.get() && !minecraft.getSoundManager().isActive(bgm)) {
			minecraft.getSoundManager().play(bgm);
		}
	}

	private void stopBGM() {
		minecraft.getSoundManager().stop(bgm);
	}

	private class CreditsList extends ContainerObjectSelectionList<Entry> {

		public CreditsList(Minecraft minecraftClient, int width, int height, int top,
						   int entryHeight) {
			super(minecraftClient, width, height, top, entryHeight);

			addEntry(new SpacerTitle("- - - - - - " + I18n.get("contributors") + " - - - - - -"));
			Credits.getContributors().forEach(credit -> addEntry(new Credit(credit.getName(), credit.getThings())));

			addEntry(new SpacerTitle("- - - - - - " + I18n.get("other_people") + " - - - - - -"));
			Credits.getOtherPeople().forEach(credit -> addEntry(new Credit(credit.getName(), credit.getThings())));

			if (!externalModuleCredits.isEmpty()) {
				addEntry(new SpacerTitle("- - - - - - " + I18n.get("external_modules") + " - - - - - -"));
				externalModuleCredits.forEach((s, s2) -> addEntry(new Credit(s, s2)));
			}
		}
	}

	private static abstract class Entry extends ContainerObjectSelectionList.Entry<Entry> {

	}

	private class Credit extends Entry {

		private final String name;
		private final String[] things;
		private final Button c;

		public Credit(String name, String... things) {
			this.name = name;
			this.things = things;
			c = new Button(0, 0, 200, 20, Component.literal(name), buttonWidget -> minecraft.gui.setScreen(new CreditOverlay(this)), Supplier::get) {
				private final Component hoveredMessage = getMessage().copy().withColor(ClientColors.SELECTOR_RED.toInt());

				@Override
				protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
					if (isHoveredOrFocused()) {
						DrawUtil.outlineRect(graphics, getX(), getY(), getWidth(), getHeight(), ClientColors.ERROR.toInt());
					}
					//int i = this.active ? (isHoveredOrFocused() ? ClientColors.SELECTOR_RED.toInt() : -1) : 10526880;
					//this.renderString(graphics, font, i | Mth.ceil(this.alpha * 255.0F) << 24);
					extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
				}

				@Override
				public Component getMessage() {
					return isHoveredOrFocused() ? hoveredMessage : super.getMessage();
				}
			};
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX,
								  int mouseY, boolean hovered, float tickDelta) {
			c.setPosition(getContentX(), getContentY());
			c.extractRenderState(graphics, mouseX, mouseY, tickDelta);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of(c);
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of(c);
		}
	}

	private class CreditOverlay extends Screen {
		private final Credit credit;

		public CreditOverlay(Credit credit) {
			super(Component.literal(credit.name));
			this.credit = credit;
		}

		@Override
		public void onClose() {
			minecraft.gui.setScreen(CreditsScreen.this);
		}

		@Override
		public void init() {
			int startY = 100;
			for (String t : credit.things) {
				int textWidth = font.br$getWidth(t);
				if (t.startsWith("http")) {
					addRenderableWidget(new PlainTextButton(width / 2 - textWidth / 2, startY, textWidth, 12,
						Component.literal(t).withColor(ClientColors.SELECTOR_GREEN.toInt()), btn ->
						defaultHandleClickEvent(new ClickEvent.OpenUrl(URI.create(t)), minecraft, CreditsScreen.this), font));
				} else {
					addRenderableOnly(new StringWidget(width / 2 - textWidth / 2, startY, textWidth, 12,
						Component.literal(t).withColor(ClientColors.SELECTOR_GREEN.toInt()), font));
				}
				startY += 12;
			}
			addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, buttonWidget -> onClose())
				.pos(width / 2 - 75, height - 33 / 2 - 10).build());
		}

		@Override
		public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			super.extractBackground(graphics, mouseX, mouseY, delta);
			DrawUtil.fillRect(graphics, 100, 50, width - 200, height - 100,
				ClientColors.DARK_GRAY.withAlpha(127).toInt());
			DrawUtil.outlineRect(graphics, 100, 50, width - 200, height - 100,
				ClientColors.BLACK.toInt());
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
			super.extractRenderState(graphics, mouseX, mouseY, delta);
			DrawUtil.drawCenteredString(graphics, font, credit.name,
				width / 2, 57, -16784327, true);
		}

		@Override
		public void tick() {
			CreditsScreen.this.tickBGM();
		}
	}

	private class SpacerTitle extends Entry {

		private final String name;

		public SpacerTitle(String name) {
			this.name = name;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX,
								  int mouseY, boolean hovered, float tickDelta) {
			DrawUtil.drawCenteredString(graphics, font, name, getContentXMiddle(), getContentY(), -128374,
				true);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}
	}
}
