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

package io.github.axolotlclient.modules.auth.skin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.axolotlclient.AxolotlClientCommon;
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors;
import io.github.axolotlclient.api.SimpleTextInputScreen;
import io.github.axolotlclient.api.util.UUIDHelper;
import io.github.axolotlclient.modules.auth.Account;
import io.github.axolotlclient.modules.auth.Auth;
import io.github.axolotlclient.modules.auth.MSApi;
import io.github.axolotlclient.util.DrawUtil;
import io.github.axolotlclient.util.ClientColors;
import io.github.axolotlclient.util.Watcher;
import io.github.axolotlclient.util.notifications.Notifications;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.NullMarked;

public class SkinManagementScreen extends Screen {
	private static final Path SKINS_DIR = FabricLoader.getInstance().getGameDir().resolve("skins");
	private static final int LIST_SKIN_WIDTH = 75;
	private static final int LIST_SKIN_HEIGHT = 110;
	private static final Component TEXT_EQUIPPING = Component.translatable("skins.manage.equipping");
	private final Screen parent;
	private final Account account;
	private MSApi.MCProfile cachedProfile;
	private SkinListWidget skinList;
	private SkinListWidget capesList;
	private boolean capesTab;
	private SkinWidget current;
	private final Watcher skinDirWatcher;
	private final CompletableFuture<MSApi.MCProfile> loadingFuture;

	public SkinManagementScreen(Screen parent, Account account) {
		super(Component.translatable("skins.manage"));
		this.parent = parent;
		this.account = account;
		skinDirWatcher = Watcher.createSelfTicking(SKINS_DIR, () -> {
			AxolotlClientCommon.getInstance().getLogger().info("Reloading screen as local files changed!");
			loadSkinsList();
		});
		loadingFuture = (account.needsRefresh() ? account.refresh(Auth.getInstance().getMsApi())
			: CompletableFuture.completedFuture(null))
			.thenComposeAsync(unused -> Auth.getInstance().getMsApi().getProfile(account));
	}

	@Override
	protected void init() {
		int headerHeight = 33;
		int contentHeight = height - headerHeight * 2;

		StringWidget titleWidget = new StringWidget(width / 2 - font.width(getTitle()) / 2, headerHeight / 2 - font.lineHeight / 2, font.width(getTitle()), font.lineHeight, getTitle(), getFont());
		addRenderableWidget(titleWidget);

		var back = Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
			.bounds(width / 2 - 75, height - headerHeight / 2 - 10, 150, 20).build();

		var loadingPlaceholder = new LoadingDotsWidget(getFont(), Component.translatable("skins.loading"));
		loadingPlaceholder.setPosition(width/2 - loadingPlaceholder.getWidth()/2, height/2 - loadingPlaceholder.getHeight()/2);
		addRenderableWidget(loadingPlaceholder);
		addRenderableWidget(back);

		skinList = new SkinListWidget(minecraft, width / 2, contentHeight - 24, headerHeight + 24, LIST_SKIN_HEIGHT + 34);
		capesList = new SkinListWidget(minecraft, width / 2, contentHeight - 24, headerHeight + 24, skinList.getEntryContentsHeight() + 24);
		skinList.setX(width / 2);
		capesList.setX(width / 2);
		var currentHeight = Math.min((width / 2f) * 120 / 85, contentHeight);
		var currentWidth = currentHeight * 85 / 120;
		current = new SkinWidget((int) currentWidth, (int) currentHeight, null, account);
		current.setPosition((int) (width / 4f - currentWidth / 2), (int) (height / 2f - currentHeight / 2));

		if (!capesTab) {
			capesList.visible = capesList.active = false;
		} else {
			skinList.visible = skinList.active = false;
		}
		List<AbstractWidget> navBar = new ArrayList<>();
		var skinsTab = Button.builder(Component.translatable("skins.nav.skins"), btn -> {
			navBar.forEach(w -> {
				if (w != btn) w.active = true;
			});
			btn.active = false;
			skinList.visible = skinList.active = true;
			capesList.visible = capesList.active = false;
			capesTab = false;
		}).pos(Math.max(width * 3 / 4 - 102, width / 2 + 2), headerHeight).width(Math.min(100, width / 4 - 2)).build();
		navBar.add(skinsTab);
		var capesTab = Button.builder(Component.translatable("skins.nav.capes"), btn -> {
			navBar.forEach(w -> {
				if (w != btn) w.active = true;
			});
			btn.active = false;
			skinList.visible = skinList.active = false;
			capesList.visible = capesList.active = true;
			this.capesTab = true;
		}).pos(width * 3 / 4 + 2, headerHeight).width(Math.min(100, width / 4 - 2)).build();
		navBar.add(capesTab);
		var importButton = SpriteIconButton.builder(Component.translatable("skins.manage.import.local"), btn -> {
			btn.active = false;
			SkinImportUtil.openImportSkinDialog().thenAccept(this::onFilesDrop).thenRun(() -> btn.active = true);
		}, true).sprite(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "folder"), 7, 7).size(11, 11).build();
		importButton.setTooltip(Tooltip.create(importButton.getMessage()));
		var downloadButton = SpriteIconButton.builder(Component.translatable("skins.manage.import.online"), btn -> {
			btn.active = false;
			promptForSkinDownload();
		}, true).sprite(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "download"), 7, 7).size(11, 11).build();
		downloadButton.setTooltip(Tooltip.create(downloadButton.getMessage()));
		if (width - (capesTab.getX() + capesTab.getWidth()) > 28) {
			importButton.setX(width - importButton.getWidth() - 2);
			downloadButton.setX(importButton.getX() - downloadButton.getWidth() - 2);
			importButton.setY(capesTab.getY() + capesTab.getHeight() - 11);
			downloadButton.setY(importButton.getY());
		} else {
			importButton.setX(capesTab.getX() + capesTab.getWidth() - 11);
			importButton.setY(capesTab.getY() - 13);
			downloadButton.setX(importButton.getX() - 2 - 11);
			downloadButton.setY(importButton.getY());
		}
		skinsTab.active = this.capesTab;
		capesTab.active = !this.capesTab;
		Runnable addWidgets = () -> {
			clearWidgets();
			addRenderableWidget(titleWidget);
			addRenderableWidget(current);
			addRenderableWidget(skinsTab);
			addRenderableWidget(capesTab);
			addRenderableWidget(downloadButton);
			addRenderableWidget(importButton);
			addRenderableWidget(skinList);
			addRenderableWidget(capesList);
			addRenderableWidget(back);
		};
		if (cachedProfile != null) {
			initDisplay();
			addWidgets.run();
			return;
		}

		loadingFuture.thenAcceptAsync(profile -> {
			cachedProfile = profile;
			initDisplay();
			addWidgets.run();
		}).exceptionally(t -> {
			if (t.getCause() instanceof CancellationException) {
				minecraft.gui.setScreen(parent);
				return null;
			}
			AxolotlClientCommon.getInstance().getLogger().error("Failed to load skins!", t);
			var error = Component.translatable("skins.error.failed_to_load");
			var errorDesc = Component.translatable("skins.error.failed_to_load_desc");
			clearWidgets();
			addRenderableWidget(titleWidget);
			addRenderableWidget(new StringWidget(width / 2 - getFont().width(error) / 2, height / 2 - getFont().lineHeight - 2, getFont().width(error), getFont().lineHeight, error, getFont()));
			addRenderableWidget(new StringWidget(width / 2 - getFont().width(errorDesc) / 2, height / 2 + 1, getFont().width(errorDesc), getFont().lineHeight, errorDesc, getFont()));
			addRenderableWidget(back);
			return null;
		});
	}

	private void promptForSkinDownload() {
		minecraft.gui.setScreen(new SimpleTextInputScreen(this, Component.translatable("skins.manage.import.online"), Component.translatable("skins.manage.import.online.input"), s ->
			UUIDHelper.ensureUuidOpt(s).thenAcceptAsync(o -> {
				if (o.isPresent()) {
					AxolotlClientCommon.getInstance().getLogger().info("Downloading skin of {} ({})", s, o.get());
					Auth.getInstance().getMsApi().getTextures(o.get())
						.exceptionally(th -> {
							AxolotlClientCommon.getInstance().getLogger().info("Failed to download skin of {} ({})", s, o.get(), th);
							return null;
						}).thenAcceptAsync(t -> {
							if (t == null) {
								Notifications.getInstance().addStatus("skins.notification.title", "skins.notification.import.online.failed_to_download", s);
								return;
							}
							try {
								var bytes = t.skin().join();
								var out = ensureNonexistent(SKINS_DIR.resolve(t.skinKey()));
								Skin.LocalSkin.writeMetadata(out, Map.of(Skin.LocalSkin.CLASSIC_METADATA_KEY, t.classicModel(), "name", t.name(), "uuid", t.id(), "download_time", Instant.now()));
								Files.write(out, bytes);
								minecraft.execute(this::loadSkinsList);
								Notifications.getInstance().addStatus("skins.notification.title", "skins.notification.import.online.downloaded", t.name());
								AxolotlClientCommon.getInstance().getLogger().info("Downloaded skin of {} ({})", t.name(), o.get());
							} catch (IOException e) {
								AxolotlClientCommon.getInstance().getLogger().warn("Failed to write skin file", e);
								Notifications.getInstance().addStatus("skins.notification.title", "skins.notification.import.online.failed_to_save", t.name());
							}
						});
				} else {
					Notifications.getInstance().addStatus("skins.notification.title", "skins.notification.import.online.not_found", s);
				}
			})));
	}

	private void initDisplay() {
		loadSkinsList();
		loadCapesList();
	}

	private void refreshCurrentList() {
		if (capesTab) {
			var scroll = capesList.scrollAmount();
			loadCapesList();
			capesList.setScrollAmount(scroll);
		} else {
			var scroll = skinList.scrollAmount();
			loadSkinsList();
			skinList.setScrollAmount(scroll);
		}
	}

	private void loadCapesList() {
		List<Row> rows = new ArrayList<>();
		var profile = cachedProfile;
		int columns = Math.max(2, (width / 2 - 25) / LIST_SKIN_WIDTH);
		var capes = profile.capes();
		var deselectCape = createWidgetForCape(current.getSkin(), null);
		var activeCape = capes.stream().filter(Cape::active).findFirst();
		current.setCape(activeCape.orElse(null));
		deselectCape.noCape(activeCape.isEmpty());
		for (int i = 0; i < capes.size() + 1; i += columns) {
			Entry widget;
			if (i == 0) {
				widget = createEntry(capesList.getEntryContentsHeight(), deselectCape, Component.translatable("skins.capes.no_cape"));
			} else {
				var cape = capes.get(i - 1);
				widget = createEntryForCape(current.getSkin(), cape, capesList.getEntryContentsHeight());
			}
			List<AbstractWidget> widgets = new ArrayList<>();
			widgets.add(widget);
			for (int c = 1; c < columns; c++) {
				if (!(i < capes.size() + 1 - c)) continue;
				var cape2 = capes.get(i + c - 1);
				Entry widget2 = createEntryForCape(current.getSkin(), cape2, capesList.getEntryContentsHeight());

				widgets.add(widget2);
			}
			rows.add(new Row(widgets));
		}
		minecraft.execute(() -> capesList.replaceEntries(rows));
		capesList.setScrollAmount(capesList.scrollAmount());
	}

	private void loadSkinsList() {
		var profile = cachedProfile;
		int columns = Math.max(2, (width / 2 - 25) / LIST_SKIN_WIDTH);
		List<Skin> skins = new ArrayList<>(profile.skins());
		var hashes = skins.stream().map(Asset::sha256).collect(Collectors.toSet());
		var defaultSkin = Skin.getDefaultSkin(account);
		var local = new ArrayList<>(loadLocalSkins());
		var localHashes = local.stream().collect(Collectors.toMap(Asset::sha256, Function.identity(), (skin, skin2) -> skin));
		local.removeIf(s -> !localHashes.containsValue(s));
		skins.replaceAll(s -> {
			if (s instanceof MSApi.MCProfile.OnlineSkin online) {
				if (localHashes.containsKey(s.sha256()) && localHashes.get(s.sha256()) instanceof Skin.LocalSkin file) {
					local.remove(localHashes.remove(s.sha256()));
					return new Skin.Shared(file, online);
				}
			}
			return s;
		});

		skins.addAll(local);
		if (!hashes.contains(defaultSkin.sha256())) {
			skins.add(defaultSkin);
		}
		populateSkinList(skins, columns);
		skinList.setScrollAmount(skinList.scrollAmount());
	}

	private List<Skin> loadLocalSkins() {
		try {
			Files.createDirectories(SKINS_DIR);
			try (Stream<Path> skins = Files.list(SKINS_DIR)) {
				return skins.filter(Files::isRegularFile).sorted(Comparator.<Path>comparingLong(p -> {
					try {
						return Files.getLastModifiedTime(p).toMillis();
					} catch (IOException e) {
						return 0L;
					}
				}).reversed()).map(Auth.getInstance().getSkinManager()::read).filter(Objects::nonNull).toList();
			}
		} catch (IOException e) {
			AxolotlClientCommon.getInstance().getLogger().warn("Failed to read skins dir!", e);
		}
		return Collections.emptyList();
	}

	private void populateSkinList(List<? extends Skin> skins, int columns) {
		int entryHeight = skinList.getEntryContentsHeight();
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < skins.size(); i += columns) {
			var s = skins.get(i);
			if (s != null && s.active()) {
				current.setSkin(s);
			}
			var widget = createEntryForSkin(s, entryHeight);
			List<AbstractWidget> widgets = new ArrayList<>();
			widgets.add(widget);
			for (int c = 1; c < columns; c++) {
				if (!(i < skins.size() - c)) continue;
				var s2 = skins.get(i + c);
				if (s2 != null && s2.active()) {
					current.setSkin(s2);
				}
				var widget2 = createEntryForSkin(s2, entryHeight);
				widgets.add(widget2);
			}
			rows.add(new Row(widgets));
		}
		minecraft.execute(() -> skinList.replaceEntries(rows));
	}

	private Path ensureNonexistent(Path p) {
		if (Files.exists(p)) {
			int counter = 0;
			do {
				counter++;
				p = p.resolveSibling(p.getFileName().toString() + "_" + counter);
			} while (Files.exists(p));
		}
		return p;
	}

	@Override
	public void onFilesDrop(List<Path> packs) {
		if (packs.isEmpty()) return;

		CompletableFuture<?>[] futs = new CompletableFuture[packs.size()];
		for (int i = 0; i < packs.size(); i++) {
			Path p = packs.get(i);
			futs[i] = CompletableFuture.runAsync(() -> {
				try {
					var target = ensureNonexistent(SKINS_DIR.resolve(p.getFileName()));
					var skin = Auth.getInstance().getSkinManager().read(p, false);
					if (skin != null) {
						Files.write(target, skin.image());
					} else {
						AxolotlClientCommon.getInstance().getLogger().info("Skipping dragged file {} because it does not seem to be a valid skin!", p);
						Notifications.getInstance().addStatus("skins.notification.title", "skins.notification.not_copied", p.getFileName());
					}
				} catch (IOException e) {
					AxolotlClientCommon.getInstance().getLogger().warn("Failed to copy skin file: ", e);
				}
			}, minecraft);
		}
		CompletableFuture.allOf(futs).thenRun(this::loadSkinsList);
	}

	private @NotNull Entry createEntryForSkin(Skin skin, int entryHeight) {
		return createEntry(entryHeight, new SkinWidget(LIST_SKIN_WIDTH, LIST_SKIN_HEIGHT, skin, account));
	}

	private @NotNull Entry createEntryForCape(Skin currentSkin, Cape cape, int entryHeight) {
		return createEntry(entryHeight, createWidgetForCape(currentSkin, cape), Component.literal(cape.alias()));
	}

	private SkinWidget createWidgetForCape(Skin currentSkin, Cape cape) {
		SkinWidget widget2 = new SkinWidget(LIST_SKIN_WIDTH, LIST_SKIN_HEIGHT, currentSkin, cape, account);
		widget2.setRotationY(210);
		return widget2;
	}

	@Override
	protected void clearWidgets() {
		super.clearWidgets();
		SkinRenderer.closeRenderers();
		Auth.getInstance().getSkinManager().releaseAll();
	}

	@Override
	public void removed() {
		Auth.getInstance().getSkinManager().releaseAll();
		Watcher.close(skinDirWatcher);
		SkinRenderer.closeRenderers();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	private SkinListWidget getCurrentList() {
		return capesTab ? capesList : skinList;
	}

	@NullMarked
	private static class SkinListWidget extends ContainerObjectSelectionList<Row> {
		public SkinListWidget(Minecraft minecraft, int width, int height, int y, int entryHeight) {
			super(minecraft, width, height, y, entryHeight);
		}

		@Override
		protected int scrollBarX() {
			return getRight() - 8;
		}

		@Override
		public int getRowLeft() {
			return getX() + 3;
		}

		@Override
		public int getRowWidth() {
			if (!scrollable()) {
				return getWidth() - 4;
			}
			return getWidth() - 14;
		}

		public int getEntryContentsHeight() {
			return defaultEntryHeight - 4;
		}

		@Override
		public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
			if (!active || !visible) return null;
			return super.nextFocusPath(event);
		}

		@Override
		public void replaceEntries(Collection<Row> newEntries) {
			super.replaceEntries(newEntries);
			refreshScrollAmount();
		}

		@Override
		public void centerScrollOn(Row entry) {
			super.centerScrollOn(entry);
		}
	}

	@NullMarked
	private class Row extends ContainerObjectSelectionList.Entry<Row> {
		private final List<AbstractWidget> widgets;

		public Row(List<AbstractWidget> entries) {
			this.widgets = entries;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return widgets;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, boolean hovering, float partialTick) {
			int x = getX();
			if (widgets.isEmpty()) return;
			int count = widgets.size();
			int padding = ((getWidth() - 5 * (count - 1)) / count);
			for (var w : widgets) {
				w.setPosition(x, getContentY());
				w.setWidth(padding);
				w.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
				x += w.getWidth() + 5;
			}
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return widgets;
		}

		@Override
		public void setFocused(@Nullable GuiEventListener focused) {
			super.setFocused(focused);
			if (focused != null) {
				getCurrentList().centerScrollOn(this);
			}
		}
	}

	Entry createEntry(int height, SkinWidget widget) {
		return createEntry(height, widget, null);
	}

	Entry createEntry(int height, SkinWidget widget, Component label) {
		return new Entry(height, widget, label);
	}

	private class Entry extends AbstractContainerWidget {
		private final SkinWidget skinWidget;
		private final @Nullable AbstractWidget label;
		private final List<AbstractWidget> actionButtons = new ArrayList<>();
		private final AbstractWidget equipButton;
		private boolean equipping;
		private long equippingStart;

		public Entry(int height, SkinWidget widget, @Nullable Component label) {
			super(0, 0, widget.getWidth(), height, Component.empty(), AbstractScrollArea.defaultSettings(9));
			widget.setWidth(getWidth() - 4);
			var asset = widget.getFocusedAsset();
			if (asset != null) {
				class SpriteButton extends Button {
					private Identifier sprite;

					public SpriteButton(Component message, OnPress onPress, Identifier sprite) {
						super(0, 0, 11, 11, message, onPress, DEFAULT_NARRATION);
						this.sprite = sprite;
						setTooltip(Tooltip.create(message, Component.empty()));
					}

					@Override
					public void setMessage(@NotNull Component message) {
						super.setMessage(message);
						setTooltip(Tooltip.create(message, Component.empty()));
					}

					@Override
					protected void extractContents(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
						extractDefaultSprite(graphics);
						graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX() + 2, this.getY() + 2, 7, 7);
					}
				}
				if (asset instanceof Skin skin) {
					var wideSprite = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "wide");
					var slimSprite = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "slim");
					var slimText = Component.translatable("skins.manage.variant.classic");
					var wideText = Component.translatable("skins.manage.variant.slim");
					actionButtons.add(new SpriteButton(skin.classicVariant() ? wideText : slimText, btn -> {
						var self = (SpriteButton) btn;
						skin.classicVariant(!skin.classicVariant());
						self.sprite = skin.classicVariant() ? slimSprite : wideSprite;
						self.setMessage(skin.classicVariant() ? wideText : slimText);
					}, skin.classicVariant() ? slimSprite : wideSprite));
				}
				if (asset instanceof Asset.Local local) {
					this.actionButtons.add(new SpriteButton(Component.translatable("skins.manage.delete"), btn -> {
						btn.active = false;
						minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
							minecraft.gui.setScreen(new LoadingScreen(getTitle(), Component.translatable("menu.working")));
							if (confirmed) {
								try {
									Files.delete(local.file());
									Skin.LocalSkin.deleteMetadata(local.file());
									refreshCurrentList();
								} catch (IOException e) {
									AxolotlClientCommon.getInstance().getLogger().warn("Failed to delete: ", e);
								}
							}
							minecraft.gui.setScreen(SkinManagementScreen.this);
							btn.active = true;
						}, Component.translatable("skins.manage.delete.confirm"), (asset.active() ?
							Component.translatable("skins.manage.delete.confirm.desc_active") :
							Component.translatable("skins.manage.delete.confirm.desc")
						).withColor(Colors.RED.toInt())));
					}, Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "delete")));
				}
				if (asset instanceof Asset.Online online && online.supportsDownload() && !(asset instanceof Asset.Local)) {
					this.actionButtons.add(new SpriteButton(Component.translatable("skins.manage.download"), btn -> {
						btn.active = false;
						download(asset).thenRun(() -> {
							refreshCurrentList();
							btn.active = true;
						});
					}, Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "download")));
				}
			}
			if (label != null) {
				this.label = new AbstractStringWidget(0, 0, widget.getWidth(), 16, label, font) {
					@Override
					public void visitLines(@NotNull ActiveTextCollector activeTextCollector) {
						extractScrollingStringOverContents(activeTextCollector, getMessage(), 2);
					}
				};
				this.label.active = false;
			} else {
				this.label = null;
			}
			this.equipButton = Button.builder(Component.translatable(
					widget.isEquipped() ? "skins.manage.equipped" : "skins.manage.equip"),
				btn -> {
					equippingStart = Util.getMillis();
					equipping = true;
					btn.setMessage(TEXT_EQUIPPING);
					btn.active = false;
					Consumer<CompletableFuture<MSApi.MCProfile>> consumer = f ->
						f.thenAcceptAsync(p -> {
							cachedProfile = p;
							if (minecraft.gui.screen() == SkinManagementScreen.this) {
								refreshCurrentList();
							} else {
								minecraft.execute(() -> minecraft.gui.setScreen(SkinManagementScreen.this));
							}
						}).exceptionally(t -> {
							AxolotlClientCommon.getInstance().getLogger().warn("Failed to equip asset!", t);
							equipping = false;
							return null;
						});
					if (asset instanceof Skin && !(current.getSkin() instanceof Skin.Local)) {
						minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
							minecraft.gui.setScreen(new LoadingScreen(getTitle(), TEXT_EQUIPPING));
							if (confirmed) {
								consumer.accept(download(current.getSkin()).thenCompose(a -> widget.equip()));
							} else {
								consumer.accept(widget.equip());
							}
						}, Component.translatable("skins.manage.equip.confirm"), Component.translatable("skins.manage.equip.download_current")));
					} else {
						consumer.accept(widget.equip());
					}
				}).width(widget.getWidth()).build();
			this.equipButton.active = !widget.isEquipped();
			this.skinWidget = widget;
		}

		private @NotNull CompletableFuture<?> download(Asset asset) {
			return CompletableFuture.runAsync(() -> {
				try {
					var out = SKINS_DIR.resolve(asset.sha256());
					Files.createDirectories(out.getParent());
					Files.write(out, asset.image());
					if (asset instanceof Skin skin) {
						Skin.LocalSkin.writeMetadata(out, Map.of(Skin.LocalSkin.CLASSIC_METADATA_KEY, skin.classicVariant()));
					}
				} catch (IOException e) {
					AxolotlClientCommon.getInstance().getLogger().warn("Failed to download: ", e);
				}
			});
		}

		@Override
		public @NotNull List<? extends GuiEventListener> children() {
			return Stream.concat(actionButtons.stream(), Stream.of(skinWidget, label, equipButton)).filter(Objects::nonNull).toList();
		}

		@Override
		protected int contentHeight() {
			return getHeight();
		}

		@Override
		protected double scrollRate() {
			return 0;
		}

		private float applyEasing(float x) {
			return x * x * x;
		}

		@Override
		protected void extractWidgetRenderState(@NotNull GuiGraphicsExtractor guiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
			int y = getY() + 4;
			int x = getX() + 2;
			skinWidget.setPosition(x, y);
			skinWidget.setWidth(getWidth() - 4);
			if (skinWidget.isEquipped() || equipping) {
				long prog;
				if (Auth.getInstance().skinManagerAnimations.get()) {
					if (equipping) prog = (Util.getMillis() - equippingStart) / 20 % 100;
					else prog = Math.abs((Util.getMillis() / 30 % 200) - 100);
				} else prog = 100;
				var percent = prog / 100f;
				float gradientWidth;
				if (equipping) {
					gradientWidth = percent * Math.min(getWidth() / 3f, getHeight() / 3f);
				} else {
					gradientWidth = Math.min(getWidth() / 15f, getHeight() / 6f) + applyEasing(percent) * Math.min(getWidth() * 2 / 15f, getHeight() / 6f);
				}
				GradientHoleRectangleRenderState.create(guiGraphicsExtractor, getX() + 2, getY() + 2, getRight() - 2,
					skinWidget.getBottom() + 2,
					gradientWidth,
					equipping ? 0xFFFF0088 : ClientColors.SELECTOR_GREEN.toInt(), 0).submit();
			}
			skinWidget.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
			int actionButtonY = getY() + 2;
			for (var button : actionButtons) {
				button.setPosition(skinWidget.getRight() - button.getWidth(), actionButtonY);
				if (isHovered() || button.isHoveredOrFocused()) {
					button.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
				}
				actionButtonY += button.getHeight() + 2;
			}
			if (label != null) {
				label.setPosition(x, skinWidget.getBottom() + 6);
				label.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);
				label.setWidth(getWidth() - 4);
				equipButton.setPosition(x, label.getBottom() + 2);
			} else {
				equipButton.setPosition(x, skinWidget.getBottom() + 4);
			}
			equipButton.setWidth(getWidth() - 4);
			equipButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick);

			if (isHovered()) {
				DrawUtil.outlineRect(guiGraphicsExtractor, getX(), getY(), getWidth(), getHeight(), -1);
			}
		}

		@Override
		protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
			skinWidget.updateNarration(narrationElementOutput);
			actionButtons.forEach(w -> w.updateNarration(narrationElementOutput));
			if (label != null) {
				label.updateNarration(narrationElementOutput);
			}
			equipButton.updateNarration(narrationElementOutput);
		}

		private record GradientHoleRectangleRenderState(RenderPipeline pipeline, TextureSetup textureSetup,
														Matrix3x2f pose,
														int x0, int y0, int x1, int y1, float gradientWidth, int col1,
														int col2, @Nullable ScreenRectangle scissorArea,
														@Nullable ScreenRectangle bounds) implements GuiElementRenderState {

			public static GradientHoleRectangleRenderState create(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float gradientWidth, int col1, int col2) {
				var matrix = new Matrix3x2f(graphics.pose());
				var area = graphics.scissorStack.peek();
				return new GradientHoleRectangleRenderState(RenderPipelines.GUI, TextureSetup.noTexture(), matrix, x0, y0, x1, y1, gradientWidth, col1, col2, area, getBounds(x0, y0, x1, y1, matrix, area));
			}

			public void submit() {
				Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.addGuiElement(this);
			}

			@Override
			public void buildVertices(VertexConsumer vertexConsumer) {
				//top
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y0()).setColor(this.col1());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x0() + gradientWidth(), this.y0() + gradientWidth()).setColor(this.col2());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x1() - gradientWidth(), this.y0() + gradientWidth()).setColor(this.col2());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y0()).setColor(this.col1());
				//left
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y1()).setColor(this.col1());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x0() + gradientWidth(), this.y1() - gradientWidth()).setColor(this.col2());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x0() + gradientWidth(), this.y0() + gradientWidth()).setColor(this.col2());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y0()).setColor(this.col1());
				//bottom
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y1()).setColor(this.col1());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x1() - gradientWidth(), this.y1() - gradientWidth()).setColor(this.col2());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x0() + gradientWidth(), this.y1() - gradientWidth()).setColor(this.col2());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x0(), this.y1()).setColor(this.col1());
				//right
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y0()).setColor(this.col1());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x1() - gradientWidth(), this.y0() + gradientWidth()).setColor(this.col2());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x1() - gradientWidth(), this.y1() - gradientWidth()).setColor(this.col2());
				vertexConsumer.addVertexWith2DPose(this.pose(), this.x1(), this.y1()).setColor(this.col1());
			}

			@Nullable
			private static ScreenRectangle getBounds(int i, int j, int k, int l, Matrix3x2f matrix3x2f, @Nullable ScreenRectangle screenRectangle) {
				ScreenRectangle screenRectangle2 = new ScreenRectangle(i, j, k - i, l - j).transformMaxBounds(matrix3x2f);
				return screenRectangle != null ? screenRectangle.intersection(screenRectangle2) : screenRectangle2;
			}
		}
	}
}
