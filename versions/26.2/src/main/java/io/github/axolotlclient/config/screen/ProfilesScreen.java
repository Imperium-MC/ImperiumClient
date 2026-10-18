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

package io.github.axolotlclient.config.screen;

import java.util.ArrayList;
import java.util.List;

import io.github.axolotlclient.AxolotlClientConfig.impl.ui.RecreatableScreen;
import io.github.axolotlclient.config.profiles.Profiles;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class ProfilesScreen extends Screen implements RecreatableScreen {

	private final HeaderAndFooterLayout haL = new HeaderAndFooterLayout(this);
	@Nullable
	private ProfilesList profilesList;
	@Nullable
	private final Screen parent;
	private boolean initialized;

	public ProfilesScreen(@Nullable Screen parent) {
		super(Component.translatable("profiles.configure.list"));
		this.parent = parent;
	}

	@Override
	protected void init() {

		if (!initialized) {
			initialized = true;
			haL.addTitleHeader(getTitle(), font);
			profilesList = new ProfilesList(minecraft, 0, 0, 0, 25);
			haL.addToContents(profilesList);
			var footer = haL.addToFooter(LinearLayout.horizontal()).spacing(4);
			footer.addChild(Button.builder(CommonComponents.GUI_BACK, btn -> onClose()).build());
			haL.visitWidgets(this::addRenderableWidget);
		}

		repositionElements();
	}

	@Override
	protected void repositionElements() {
		haL.arrangeElements();
		assert profilesList != null;
		profilesList.updateSize(this.width, this.haL);
	}

	@Override
	public void onClose() {
		Profiles.getInstance().saveProfiles();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public Screen recreate() {
		return new ProfilesScreen(RecreatableScreen.tryRecreate(parent));
	}

	public class ProfilesList extends ContainerObjectSelectionList<ProfilesList.Entry> {
		private static final Entry SPACER = new SpacerEntry();
		private final Entry ADD = new NewEntry();

		public ProfilesList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
			reload();
		}

		public void reload() {
			clearEntries();
			Profiles.getInstance().iterateAvailable(p -> addEntry(new ProfileEntry(p)));
			addEntry(SPACER);
			addEntry(ADD);
			var presets = Profiles.getInstance().findPresets();
			if (!presets.isEmpty()) {
				addEntry(SPACER);
				presets.forEach(p -> addEntry(new PresetEntry(p)));
			}
		}

		@Override
		public int getRowWidth() {
			return 340;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			children().stream().filter(e -> e instanceof ProfileEntry)
				.map(e -> (ProfileEntry) e).map(e -> e.profileName).forEach(e -> e.setFocused(false));
			return super.mouseClicked(event, doubleClick);
		}

		@Environment(EnvType.CLIENT)
		public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {

		}

		public static class SpacerEntry extends Entry {

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of();
			}

			@Override
			public void extractContent(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, boolean hovering, float partialTick) {

			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of();
			}

			@Nullable
			@Override
			public ComponentPath nextFocusPath(FocusNavigationEvent event) {
				return null;
			}
		}

		@Environment(EnvType.CLIENT)
		public class ProfileEntry extends Entry {
			private static final Component EXPORT_BUTTON_TITLE = Component.translatable("profiles.profile.export");
			private static final Component CURRENT_TEXT = Component.translatable("profiles.profile.current");
			private static final Component LOAD_BUTTON_TITLE = Component.translatable("profiles.profile.load");
			private static final Component DUPLICATE_BUTTON_TITLE = Component.translatable("profiles.profile.duplicate");
			private static final Component REMOVE_BUTTON_TITLE = Component.translatable("profiles.profile.remove");
			private final EditBox profileName;
			private final Button exportButton, loadButton, duplicateButton, removeButton;
			private final Profiles.Profile profile;

			ProfileEntry(Profiles.Profile profile) {
				this.profile = profile;
				profileName = new EditBox(getFont(), 0, 0, 150, 20, Component.empty());
				profileName.setValue(profile.name());
				profileName.setResponder(profile::setName);
				exportButton = Button.builder(EXPORT_BUTTON_TITLE, btn -> {
					btn.active = false;
					Profiles.getInstance().exportProfile(profile).thenRun(() -> btn.active = true);
				}).width(50).build();
				loadButton = Button.builder(LOAD_BUTTON_TITLE, btn ->
					Profiles.getInstance().switchTo(profile)).width(50).build();
				duplicateButton = Button.builder(DUPLICATE_BUTTON_TITLE, b -> {
					var dup = Profiles.getInstance().duplicate(profile);
					double d = (double) ProfilesList.this.maxScrollAmount() - ProfilesList.this.scrollAmount();
					@SuppressWarnings("unchecked") var entries = new ArrayList<>((List<Entry>) children());
					entries.add(entries.indexOf(ProfileEntry.this) + 1, new ProfileEntry(dup));
					replaceEntries(entries);
					ProfilesList.this.setScrollAmount(ProfilesList.this.maxScrollAmount() - d);
				}).width(50).build();

				this.removeButton = Button.builder(REMOVE_BUTTON_TITLE, b -> {
						removeEntry(this);
						Profiles.getInstance().remove(profile);
						refreshScrollAmount();
					}).width(50).build();
			}

			@Override
			public void extractContent(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, boolean hovering, float partialTick) {
				int i = scrollBarX() - removeButton.getWidth() - 4;
				int j = getContentY() - 2;
				this.removeButton.setPosition(i, j);
				this.removeButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);

				i -= duplicateButton.getWidth();
				duplicateButton.setPosition(i, j);
				duplicateButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);

				boolean current = Profiles.getInstance().getCurrent() == profile;
				loadButton.setMessage(current ? CURRENT_TEXT : LOAD_BUTTON_TITLE);
				loadButton.active = removeButton.active = !current;
				i -= loadButton.getWidth();
				this.loadButton.setPosition(i, j);
				this.loadButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
				i -= exportButton.getWidth();
				exportButton.setPosition(i, j);
				exportButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
				profileName.setWidth(i - getContentX() - 4);
				profileName.setPosition(getContentX(), j);
				profileName.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(profileName, exportButton, this.loadButton, duplicateButton, removeButton);
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(profileName, exportButton, this.loadButton, duplicateButton, removeButton);
			}
		}

		public class NewEntry extends Entry {

			private final Button addButton, importButton;

			public NewEntry() {
				this.addButton = Button.builder(Component.translatable("profiles.profile.add"), button -> {
						var entries = new ArrayList<>(ProfilesList.this.children());
						entries.add(Math.max(entries.indexOf(this) - 1, 0), new ProfileEntry(Profiles.getInstance().newProfile(I18n.get("profiles.profile.default_new_name"))));
						replaceEntries(entries);
						Profiles.getInstance().saveProfiles();
						setScrollAmount(maxScrollAmount());
					}).bounds(0, 0, 150, 20)
					.build();
				this.importButton = Button.builder(Component.translatable("profiles.profile.import"), btn -> {
					btn.active = false;
					Profiles.getInstance().importProfiles().thenRun(() -> {
						btn.active = true;
						ProfilesList.this.reload();
					});
				}).build();
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(addButton, importButton);
			}

			@Override
			public void extractContent(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, boolean hovering, float partialTick) {
				int i = scrollBarX() - getContentWidth() / 2 - 10 - addButton.getWidth() + 2;
				int j = getContentY() - 2;
				this.addButton.setPosition(i, j);
				this.addButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
				this.importButton.setPosition(addButton.getRight() + 2, j);
				this.importButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(addButton, importButton);
			}
		}

		public class PresetEntry extends Entry {
			private final Button importButton;
			private final StringWidget label;

			public PresetEntry(Profiles.ProfilePreset preset) {
				this.importButton = Button.builder(Component.translatable("profiles.profile.import_preset"), btn -> {
					preset.importProfile();
					ProfilesList.this.reload();
					ProfilesList.this.scrollToEntry(PresetEntry.this);
				}).width(150).build();
				this.label = new StringWidget(Component.literal(preset.info().name()), getFont());
				label.setHeight(20);
			}

			@Override
			public List<? extends NarratableEntry> narratables() {
				return List.of(label, importButton);
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				int i = scrollBarX() - importButton.getWidth() - 4;
				int j = getContentY() - 2;
				importButton.setPosition(i, j);
				importButton.extractRenderState(graphics, mouseX, mouseY, a);
				i -= label.getWidth();
				label.setMaxWidth(i - getContentX() - 4, StringWidget.TextOverflow.SCROLLING);
				label.setPosition(getContentX(), j);
				label.extractRenderState(graphics, mouseX, mouseY, a);
			}

			@Override
			public List<? extends GuiEventListener> children() {
				return List.of(label, importButton);
			}
		}
	}
}
