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

package io.github.axolotlclient.modules.auth.skin

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.ArrayList
import java.util.Objects
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Function

import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.api.SimpleTextInputScreen
import io.github.axolotlclient.api.util.UUIDHelper
import io.github.axolotlclient.modules.auth.Account
import io.github.axolotlclient.modules.auth.Auth
import io.github.axolotlclient.modules.auth.MSApi
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.DrawUtil
import io.github.axolotlclient.util.Watcher
import io.github.axolotlclient.util.notifications.Notifications
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractContainerWidget
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.components.AbstractStringWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import org.joml.Matrix3x2f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SkinManagementScreen(private val parent: Screen?, private val account: Account) :
    Screen(Component.translatable("skins.manage")) {

    private var cachedProfile: MSApi.MCProfile? = null
    private var skinList: SkinListWidget? = null
    private var capesList: SkinListWidget? = null
    private var capesTab = false
    private var current: SkinWidget? = null
    private val skinDirWatcher: Watcher?
    private val loadingFuture: CompletableFuture<MSApi.MCProfile>

    init {
        skinDirWatcher = Watcher.createSelfTicking(SKINS_DIR, Runnable {
            AxolotlClientCommon.getInstance().logger.info("Reloading screen as local files changed!")
            loadSkinsList()
        })
        loadingFuture = (if (account.needsRefresh()) account.refresh(Auth.getInstance().msApi)
        else CompletableFuture.completedFuture(null))
            .thenComposeAsync { Auth.getInstance().msApi.getProfile(account) }
    }

    override fun init() {
        val headerHeight = 33
        val contentHeight = height - headerHeight * 2

        val titleWidget = StringWidget(
            width / 2 - font.width(title) / 2, headerHeight / 2 - font.lineHeight / 2,
            font.width(title), font.lineHeight, title, font
        )
        addRenderableWidget(titleWidget)

        val back = Button.builder(CommonComponents.GUI_BACK) { onClose() }
            .bounds(width / 2 - 75, height - headerHeight / 2 - 10, 150, 20).build()

        val loadingPlaceholder = LoadingDotsWidget(font, Component.translatable("skins.loading"))
        loadingPlaceholder.setPosition(width / 2 - loadingPlaceholder.width / 2, height / 2 - loadingPlaceholder.height / 2)
        addRenderableWidget(loadingPlaceholder)
        addRenderableWidget(back)

        val sList = SkinListWidget(minecraft!!, width / 2, contentHeight - 24, headerHeight + 24, LIST_SKIN_HEIGHT + 34)
        val cList = SkinListWidget(minecraft!!, width / 2, contentHeight - 24, headerHeight + 24, sList.getEntryContentsHeight() + 24)
        skinList = sList
        capesList = cList
        sList.setX(width / 2)
        cList.setX(width / 2)
        val currentHeight = min((width / 2f) * 120 / 85, contentHeight.toFloat()).toInt()
        val currentWidth = currentHeight * 85 / 120
        val cur = SkinWidget(currentWidth, currentHeight, null as Skin?, account)
        current = cur
        cur.setPosition((width / 4f - currentWidth / 2).toInt(), (height / 2f - currentHeight / 2).toInt())

        if (!capesTab) {
            cList.visible = false
            cList.active = false
        } else {
            sList.visible = false
            sList.active = false
        }
        val navBar = ArrayList<AbstractWidget>()
        val skinsTab = Button.builder(Component.translatable("skins.nav.skins")) { btn ->
            navBar.forEach { w -> if (w !== btn) w.active = true }
            btn.active = false
            sList.visible = true
            sList.active = true
            cList.visible = false
            cList.active = false
            capesTab = false
        }.pos(max(width * 3 / 4 - 102, width / 2 + 2), headerHeight).width(min(100, width / 4 - 2)).build()
        navBar.add(skinsTab)
        val capesTabBtn = Button.builder(Component.translatable("skins.nav.capes")) { btn ->
            navBar.forEach { w -> if (w !== btn) w.active = true }
            btn.active = false
            sList.visible = false
            sList.active = false
            cList.visible = true
            cList.active = true
            this.capesTab = true
        }.pos(width * 3 / 4 + 2, headerHeight).width(min(100, width / 4 - 2)).build()
        navBar.add(capesTabBtn)
        val importButton = SpriteIconButton.builder(Component.translatable("skins.manage.import.local"), { btn ->
            btn.active = false
            SkinImportUtil.openImportSkinDialog().thenAccept { p: List<Path> -> onFilesDrop(p) }
                .thenRun { btn.active = true }
        }, true).sprite(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "folder"), 7, 7).size(11, 11).build()
        importButton.setTooltip(Tooltip.create(importButton.message))
        val downloadButton = SpriteIconButton.builder(Component.translatable("skins.manage.import.online"), { btn ->
            btn.active = false
            promptForSkinDownload()
        }, true).sprite(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "download"), 7, 7).size(11, 11).build()
        downloadButton.setTooltip(Tooltip.create(downloadButton.message))
        if (width - (capesTabBtn.x + capesTabBtn.width) > 28) {
            importButton.setX(width - importButton.width - 2)
            downloadButton.setX(importButton.x - downloadButton.width - 2)
            importButton.setY(capesTabBtn.y + capesTabBtn.height - 11)
            downloadButton.setY(importButton.y)
        } else {
            importButton.setX(capesTabBtn.x + capesTabBtn.width - 11)
            importButton.setY(capesTabBtn.y - 13)
            downloadButton.setX(importButton.x - 2 - 11)
            downloadButton.setY(importButton.y)
        }
        skinsTab.active = this.capesTab
        capesTabBtn.active = !this.capesTab
        val addWidgets = Runnable {
            clearWidgets()
            addRenderableWidget(titleWidget)
            addRenderableWidget(cur)
            addRenderableWidget(skinsTab)
            addRenderableWidget(capesTabBtn)
            addRenderableWidget(downloadButton)
            addRenderableWidget(importButton)
            addRenderableWidget(sList)
            addRenderableWidget(cList)
            addRenderableWidget(back)
        }
        if (cachedProfile != null) {
            initDisplay()
            addWidgets.run()
            return
        }

        loadingFuture.thenAcceptAsync { profile ->
            cachedProfile = profile
            initDisplay()
            addWidgets.run()
        }.exceptionally { t: Throwable ->
            if (t.cause is CancellationException) {
                minecraft!!.gui.setScreen(parent)
                return@exceptionally null
            }
            AxolotlClientCommon.getInstance().logger.error("Failed to load skins!", t)
            val error = Component.translatable("skins.error.failed_to_load")
            val errorDesc = Component.translatable("skins.error.failed_to_load_desc")
            clearWidgets()
            addRenderableWidget(titleWidget)
            addRenderableWidget(
                StringWidget(
                    width / 2 - font.width(error) / 2, height / 2 - font.lineHeight - 2,
                    font.width(error), font.lineHeight, error, font
                )
            )
            addRenderableWidget(
                StringWidget(
                    width / 2 - font.width(errorDesc) / 2, height / 2 + 1,
                    font.width(errorDesc), font.lineHeight, errorDesc, font
                )
            )
            addRenderableWidget(back)
            null
        }
    }

    private fun promptForSkinDownload() {
        minecraft!!.gui.setScreen(
            SimpleTextInputScreen(
                this, Component.translatable("skins.manage.import.online"),
                Component.translatable("skins.manage.import.online.input"),
                Consumer { s: String ->
                    UUIDHelper.ensureUuidOpt(s).thenAcceptAsync { o ->
                        if (o.isPresent) {
                            AxolotlClientCommon.getInstance().logger.info("Downloading skin of {} ({})", s, o.get())
                            Auth.getInstance().msApi.getTextures(o.get())
                                ?.exceptionally { th: Throwable ->
                                    AxolotlClientCommon.getInstance().logger.info(
                                        "Failed to download skin of {} ({})", s, o.get(), th
                                    )
                                    null
                                }?.thenAcceptAsync { t: MSApi.SkinBundle? ->
                                    if (t == null) {
                                        Notifications.getInstance()
                                            .addStatus("skins.notification.title", "skins.notification.import.online.failed_to_download", s)
                                        return@thenAcceptAsync
                                    }
                                    try {
                                        val bytes = t.skin.join()
                                        val out = ensureNonexistent(SKINS_DIR.resolve(t.skinKey))
                                        Skin.LocalSkin.writeMetadata(
                                            out,
                                            mapOf(
                                                Skin.LocalSkin.CLASSIC_METADATA_KEY to t.classicModel, "name" to t.name,
                                                "uuid" to t.id, "download_time" to Instant.now()
                                            )
                                        )
                                        Files.write(out, bytes)
                                        minecraft!!.execute(Runnable { this.loadSkinsList() })
                                        Notifications.getInstance()
                                            .addStatus("skins.notification.title", "skins.notification.import.online.downloaded", t.name)
                                        AxolotlClientCommon.getInstance().logger.info("Downloaded skin of {} ({})", t.name, o.get())
                                    } catch (e: IOException) {
                                        AxolotlClientCommon.getInstance().logger.warn("Failed to write skin file", e)
                                        Notifications.getInstance()
                                            .addStatus("skins.notification.title", "skins.notification.import.online.failed_to_save", t.name)
                                    }
                                }
                        } else {
                            Notifications.getInstance()
                                .addStatus("skins.notification.title", "skins.notification.import.online.not_found", s)
                        }
                    }
                }
            )
        )
    }

    private fun initDisplay() {
        loadSkinsList()
        loadCapesList()
    }

    private fun refreshCurrentList() {
        if (capesTab) {
            val scroll = capesList!!.scrollAmount()
            loadCapesList()
            capesList!!.setScrollAmount(scroll)
        } else {
            val scroll = skinList!!.scrollAmount()
            loadSkinsList()
            skinList!!.setScrollAmount(scroll)
        }
    }

    private fun loadCapesList() {
        val rows = ArrayList<Row>()
        val profile = cachedProfile!!
        val columns = max(2, (width / 2 - 25) / LIST_SKIN_WIDTH)
        val capes = profile.capes
        val deselectCape = createWidgetForCape(current!!.skin, null)
        val activeCape = capes.stream().filter { obj: Cape -> obj.active() }.findFirst()
        current!!.cape = activeCape.orElse(null)
        deselectCape.noCape(activeCape.isEmpty)
        var i = 0
        while (i < capes.size + 1) {
            val widget: Entry
            if (i == 0) {
                widget = createEntry(
                    capesList!!.getEntryContentsHeight(), deselectCape, Component.translatable("skins.capes.no_cape")
                )
            } else {
                val cape = capes[i - 1]
                widget = createEntryForCape(current!!.skin, cape, capesList!!.getEntryContentsHeight())
            }
            val widgets = ArrayList<AbstractWidget>()
            widgets.add(widget)
            for (c in 1 until columns) {
                if (i >= capes.size + 1 - c) continue
                val cape2 = capes[i + c - 1]
                val widget2 = createEntryForCape(current!!.skin, cape2, capesList!!.getEntryContentsHeight())
                widgets.add(widget2)
            }
            rows.add(Row(widgets))
            i += columns
        }
        minecraft!!.execute(Runnable { capesList!!.replaceEntries(rows) })
        capesList!!.setScrollAmount(capesList!!.scrollAmount())
    }

    private fun loadSkinsList() {
        val profile = cachedProfile ?: return
        val columns = max(2, (width / 2 - 25) / LIST_SKIN_WIDTH)
        val skins = ArrayList<Skin>(profile.skins)
        val hashes = skins.stream().map { obj: Asset -> obj.sha256() }.collect(java.util.stream.Collectors.toSet())
        val defaultSkin = Skin.getDefaultSkin(account)
        val local = ArrayList(loadLocalSkins())
        val localHashes = local.stream().collect(
            java.util.stream.Collectors.toMap({ obj: Asset -> obj.sha256() }, Function.identity()) { skin, _ -> skin }
        )
        local.removeIf { s -> !localHashes.containsValue(s) }
        skins.replaceAll { s ->
            if (s is MSApi.MCProfile.OnlineSkin) {
                val file = localHashes[s.sha256()]
                if (localHashes.containsKey(s.sha256()) && file is Skin.LocalSkin) {
                    local.remove(localHashes.remove(s.sha256()))
                    return@replaceAll Skin.Shared(file, s)
                }
            }
            s
        }

        skins.addAll(local)
        if (!hashes.contains(defaultSkin.sha256())) {
            skins.add(defaultSkin)
        }
        populateSkinList(skins, columns)
        skinList!!.setScrollAmount(skinList!!.scrollAmount())
    }

    private fun loadLocalSkins(): List<Skin> {
        try {
            Files.createDirectories(SKINS_DIR)
            Files.list(SKINS_DIR).use { skins: java.util.stream.Stream<Path> ->
                return skins.filter { p: Path -> Files.isRegularFile(p) }.sorted(
                    java.util.Comparator.comparingLong { p: Path ->
                        try {
                            Files.getLastModifiedTime(p).toMillis()
                        } catch (e: IOException) {
                            0L
                        }
                    }.reversed()
                ).map { p: Path -> Auth.getInstance().skinManager.read(p) }.filter { obj: Skin? -> Objects.nonNull(obj) }
                    .map { obj -> obj!! }.toList()
            }
        } catch (e: IOException) {
            AxolotlClientCommon.getInstance().logger.warn("Failed to read skins dir!", e)
        }
        return emptyList()
    }

    private fun populateSkinList(skins: List<Skin>, columns: Int) {
        val entryHeight = skinList!!.getEntryContentsHeight()
        val rows = ArrayList<Row>()
        var i = 0
        while (i < skins.size) {
            val s = skins[i]
            if (s.active()) {
                current!!.skin = s
            }
            val widget = createEntryForSkin(s, entryHeight)
            val widgets = ArrayList<AbstractWidget>()
            widgets.add(widget)
            for (c in 1 until columns) {
                if (i >= skins.size - c) continue
                val s2 = skins[i + c]
                if (s2.active()) {
                    current!!.skin = s2
                }
                val widget2 = createEntryForSkin(s2, entryHeight)
                widgets.add(widget2)
            }
            rows.add(Row(widgets))
            i += columns
        }
        minecraft!!.execute(Runnable { skinList!!.replaceEntries(rows) })
    }

    private fun ensureNonexistent(p: Path): Path {
        var path = p
        if (Files.exists(path)) {
            var counter = 0
            do {
                counter++
                path = path.resolveSibling(path.fileName.toString() + "_" + counter)
            } while (Files.exists(path))
        }
        return path
    }

    override fun onFilesDrop(packs: List<Path>) {
        if (packs.isEmpty()) return

        val futs = arrayOfNulls<CompletableFuture<*>>(packs.size)
        for (i in packs.indices) {
            val p = packs[i]
            futs[i] = CompletableFuture.runAsync({
                try {
                    val target = ensureNonexistent(SKINS_DIR.resolve(p.fileName))
                    val skin = Auth.getInstance().skinManager.read(p, false)
                    if (skin != null) {
                        Files.write(target, skin.image())
                    } else {
                        AxolotlClientCommon.getInstance().logger.info(
                            "Skipping dragged file {} because it does not seem to be a valid skin!", p
                        )
                        Notifications.getInstance().addStatus("skins.notification.title", "skins.notification.not_copied", p.fileName)
                    }
                } catch (e: IOException) {
                    AxolotlClientCommon.getInstance().logger.warn("Failed to copy skin file: ", e)
                }
            }, minecraft)
        }
        CompletableFuture.allOf(*futs).thenRun { this.loadSkinsList() }
    }

    private fun createEntryForSkin(skin: Skin, entryHeight: Int): Entry =
        createEntry(entryHeight, SkinWidget(LIST_SKIN_WIDTH, LIST_SKIN_HEIGHT, skin, account))

    private fun createEntryForCape(currentSkin: Skin?, cape: Cape, entryHeight: Int): Entry =
        createEntry(entryHeight, createWidgetForCape(currentSkin, cape), Component.literal(cape.alias()))

    private fun createWidgetForCape(currentSkin: Skin?, cape: Cape?): SkinWidget {
        val widget2 = SkinWidget(LIST_SKIN_WIDTH, LIST_SKIN_HEIGHT, currentSkin, cape, account)
        widget2.rotationY = 210f
        return widget2
    }

    override fun clearWidgets() {
        super.clearWidgets()
        SkinRenderer.closeRenderers()
        Auth.getInstance().skinManager.releaseAll()
    }

    override fun removed() {
        Auth.getInstance().skinManager.releaseAll()
        Watcher.close(skinDirWatcher)
        SkinRenderer.closeRenderers()
    }

    override fun onClose() {
        minecraft!!.gui.setScreen(parent)
    }

    private fun getCurrentList(): SkinListWidget = if (capesTab) capesList!! else skinList!!

    private class SkinListWidget(minecraft: Minecraft, width: Int, height: Int, y: Int, entryHeight: Int) :
        ContainerObjectSelectionList<Row>(minecraft, width, height, y, entryHeight) {

        override fun scrollBarX(): Int = getRight() - 8

        override fun getRowLeft(): Int = getX() + 3

        override fun getRowWidth(): Int = if (!scrollable()) {
            getWidth() - 4
        } else {
            getWidth() - 14
        }

        fun getEntryContentsHeight(): Int = defaultEntryHeight - 4

        override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? {
            if (!active || !visible) return null
            return super.nextFocusPath(event)
        }

        public override fun replaceEntries(newEntries: Collection<Row>) {
            super.replaceEntries(newEntries)
            refreshScrollAmount()
        }

        public override fun centerScrollOn(entry: Row) {
            super.centerScrollOn(entry)
        }
    }

    private inner class Row(private val widgets: List<AbstractWidget>) :
        ContainerObjectSelectionList.Entry<Row>() {

        override fun narratables(): List<NarratableEntry> = widgets

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            var x = getX()
            if (widgets.isEmpty()) return
            val count = widgets.size
            val padding = (getWidth() - 5 * (count - 1)) / count
            for (w in widgets) {
                w.setPosition(x, getContentY())
                w.setWidth(padding)
                w.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
                x += w.width + 5
            }
        }

        override fun children(): List<GuiEventListener> = widgets

        override fun setFocused(focused: GuiEventListener?) {
            super.setFocused(focused)
            if (focused != null) {
                getCurrentList().centerScrollOn(this)
            }
        }
    }

    internal fun createEntry(height: Int, widget: SkinWidget): Entry = createEntry(height, widget, null)

    internal fun createEntry(height: Int, widget: SkinWidget, label: Component?): Entry = Entry(height, widget, label)

    internal inner class Entry(height: Int, widget: SkinWidget, label: Component?) :
        AbstractContainerWidget(0, 0, widget.getWidth(), height, Component.empty(), AbstractScrollArea.defaultSettings(9)) {

        private val skinWidget: SkinWidget
        private val label: AbstractWidget?
        private val actionButtons = ArrayList<AbstractWidget>()
        private val equipButton: Button
        private var equipping = false
        private var equippingStart: Long = 0

        private inner class SpriteButton(message: Component, onPress: Button.OnPress, var sprite: Identifier) :
            Button(0, 0, 11, 11, message, onPress, DEFAULT_NARRATION) {

            init {
                setTooltip(Tooltip.create(message, Component.empty()))
            }

            override fun setMessage(message: Component) {
                super.setMessage(message)
                setTooltip(Tooltip.create(message, Component.empty()))
            }

            override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
                extractDefaultSprite(graphics)
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.x + 2, this.y + 2, 7, 7)
            }
        }

        init {
            widget.setWidth(getWidth() - 4)
            val asset = widget.getFocusedAsset()
            if (asset != null) {
                if (asset is Skin) {
                    val skin = asset
                    val wideSprite = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "wide")
                    val slimSprite = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "slim")
                    val slimText = Component.translatable("skins.manage.variant.classic")
                    val wideText = Component.translatable("skins.manage.variant.slim")
                    actionButtons.add(
                        SpriteButton(if (skin.classicVariant()) wideText else slimText, {
                            btn ->
                            val self = btn as SpriteButton
                            skin.classicVariant(!skin.classicVariant())
                            self.sprite = if (skin.classicVariant()) slimSprite else wideSprite
                            self.setMessage(if (skin.classicVariant()) wideText else slimText)
                        }, if (skin.classicVariant()) slimSprite else wideSprite)
                    )
                }
                if (asset is Asset.Local) {
                    val local = asset
                    this.actionButtons.add(
                        SpriteButton(Component.translatable("skins.manage.delete"), { btn ->
                            btn.active = false
                            minecraft!!.gui.setScreen(
                                ConfirmScreen(
                                    { confirmed: Boolean ->
                                        minecraft!!.gui.setScreen(LoadingScreen(title, Component.translatable("menu.working")))
                                        if (confirmed) {
                                            try {
                                                Files.delete(local.file())
                                                Skin.LocalSkin.deleteMetadata(local.file())
                                                refreshCurrentList()
                                            } catch (e: IOException) {
                                                AxolotlClientCommon.getInstance().logger.warn("Failed to delete: ", e)
                                            }
                                        }
                                        minecraft!!.gui.setScreen(this@SkinManagementScreen)
                                        btn.active = true
                                    },
                                    Component.translatable("skins.manage.delete.confirm"),
                                    (if (asset.active()) {
                                        Component.translatable("skins.manage.delete.confirm.desc_active")
                                    } else {
                                        Component.translatable("skins.manage.delete.confirm.desc")
                                    }).withColor(Colors.RED.toInt())
                                )
                            )
                        }, Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "delete"))
                    )
                }
                if (asset is Asset.Online && asset.supportsDownload() && asset !is Asset.Local) {
                    val online = asset
                    this.actionButtons.add(
                        SpriteButton(Component.translatable("skins.manage.download"), { btn ->
                            btn.active = false
                            download(online).thenRun {
                                refreshCurrentList()
                                btn.active = true
                            }
                        }, Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "download"))
                    )
                }
            }
            if (label != null) {
                this.label = object : AbstractStringWidget(0, 0, widget.getWidth(), 16, label, font) {
                    override fun visitLines(activeTextCollector: ActiveTextCollector) {
                        extractScrollingStringOverContents(activeTextCollector, message, 2)
                    }
                }
                this.label!!.active = false
            } else {
                this.label = null
            }
            this.equipButton = Button.builder(
                Component.translatable(if (widget.isEquipped()) "skins.manage.equipped" else "skins.manage.equip")
            ) { btn ->
                equippingStart = Util.getMillis()
                equipping = true
                btn.setMessage(TEXT_EQUIPPING)
                btn.active = false
                val consumer = Consumer { f: CompletableFuture<MSApi.MCProfile> ->
                    f.thenAcceptAsync { p ->
                        cachedProfile = p
                        if (minecraft!!.gui.screen() === this@SkinManagementScreen) {
                            refreshCurrentList()
                        } else {
                            minecraft!!.execute(
                                Runnable { minecraft!!.gui.setScreen(this@SkinManagementScreen) }
                            )
                        }
                    }.exceptionally { t: Throwable ->
                        AxolotlClientCommon.getInstance().logger.warn("Failed to equip asset!", t)
                        equipping = false
                        null
                    }
                }
                if (asset is Skin && current!!.skin !is Skin.LocalSkin) {
                    minecraft!!.gui.setScreen(
                        ConfirmScreen(
                            { confirmed: Boolean ->
                                minecraft!!.gui.setScreen(LoadingScreen(title, TEXT_EQUIPPING))
                                if (confirmed) {
                                    consumer.accept(download(current!!.skin!!).thenCompose { widget.equip() as CompletableFuture<MSApi.MCProfile> })
                                } else {
                                    consumer.accept(widget.equip() as CompletableFuture<MSApi.MCProfile>)
                                }
                            },
                            Component.translatable("skins.manage.equip.confirm"),
                            Component.translatable("skins.manage.equip.download_current")
                        )
                    )
                } else {
                    consumer.accept(widget.equip() as CompletableFuture<MSApi.MCProfile>)
                }
            }.width(widget.getWidth()).build()
            this.equipButton.active = !widget.isEquipped()
            this.skinWidget = widget
        }

        private fun download(asset: Asset): CompletableFuture<*> = CompletableFuture.runAsync {
            try {
                val out = SKINS_DIR.resolve(asset.sha256())
                Files.createDirectories(out.getParent())
                Files.write(out, asset.image())
                if (asset is Skin) {
                    Skin.LocalSkin.writeMetadata(out, mapOf(Skin.LocalSkin.CLASSIC_METADATA_KEY to asset.classicVariant()))
                }
            } catch (e: IOException) {
                AxolotlClientCommon.getInstance().logger.warn("Failed to download: ", e)
            }
        }

        override fun children(): List<GuiEventListener> =
            (actionButtons.asSequence() + sequenceOf(skinWidget, label, equipButton)).filterNotNull().toList()

        override fun contentHeight(): Int = getHeight()

        override fun scrollRate(): Double = 0.0

        private fun applyEasing(x: Float): Float = x * x * x

        override fun extractWidgetRenderState(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float
        ) {
            val y = getY() + 4
            val x = getX() + 2
            skinWidget.setPosition(x, y)
            skinWidget.setWidth(getWidth() - 4)
            if (skinWidget.isEquipped() || equipping) {
                val prog: Long
                if (Auth.getInstance().skinManagerAnimations.get()) {
                    prog = if (equipping) (Util.getMillis() - equippingStart) / 20 % 100
                    else abs((Util.getMillis() / 30 % 200) - 100)
                } else {
                    prog = 100
                }
                val percent = prog / 100f
                val gradientWidth: Float
                if (equipping) {
                    gradientWidth = percent * min(getWidth() / 3f, getHeight() / 3f)
                } else {
                    gradientWidth = min(getWidth() / 15f, getHeight() / 6f) +
                        applyEasing(percent) * min(getWidth() * 2 / 15f, getHeight() / 6f)
                }
                GradientHoleRectangleRenderState.create(
                    guiGraphicsExtractor, getX() + 2, getY() + 2, getRight() - 2,
                    skinWidget.getBottom() + 2,
                    gradientWidth,
                    if (equipping) 0xFFFF0088.toInt() else ClientColors.SELECTOR_GREEN.toInt(), 0
                ).submit()
            }
            skinWidget.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            var actionButtonY = getY() + 2
            for (button in actionButtons) {
                button.setPosition(skinWidget.getRight() - button.getWidth(), actionButtonY)
                if (isHovered() || button.isHoveredOrFocused()) {
                    button.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
                }
                actionButtonY += button.getHeight() + 2
            }
            if (label != null) {
                label.setPosition(x, skinWidget.getBottom() + 6)
                label.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
                label.setWidth(getWidth() - 4)
                equipButton.setPosition(x, label.getBottom() + 2)
            } else {
                equipButton.setPosition(x, skinWidget.getBottom() + 4)
            }
            equipButton.setWidth(getWidth() - 4)
            equipButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)

            if (isHovered()) {
                DrawUtil.outlineRect(guiGraphicsExtractor, getX(), getY(), getWidth(), getHeight(), -1)
            }
        }

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
            skinWidget.updateNarration(narrationElementOutput)
            actionButtons.forEach { w -> w.updateNarration(narrationElementOutput) }
            label?.updateNarration(narrationElementOutput)
            equipButton.updateNarration(narrationElementOutput)
        }
    }

    /**
     * Kotlin note: this was a Java record (a private render state local to Entry); the
     * GuiElementRenderState record-style accessor *functions* are spelled out because a
     * Kotlin @JvmRecord's accessors are not treated as implementations (the
     * HorizontalGradientRectangleRenderState precedent). equals/hashCode were never used
     * by the original record and are not reproduced.
     */
    private class GradientHoleRectangleRenderState(
        private val pipeline: com.mojang.renderpearl.api.pipeline.RenderPipeline,
        private val textureSetup: TextureSetup,
        private val pose: Matrix3x2f,
        private val x0: Int, private val y0: Int, private val x1: Int, private val y1: Int,
        private val gradientWidth: Float, private val col1: Int, private val col2: Int,
        private val scissorArea: ScreenRectangle?,
        private val bounds: ScreenRectangle?
    ) : GuiElementRenderState {

        override fun pipeline(): com.mojang.renderpearl.api.pipeline.RenderPipeline = pipeline

        override fun textureSetup(): TextureSetup = textureSetup

        fun pose(): Matrix3x2f = pose

        override fun scissorArea(): ScreenRectangle? = scissorArea

        override fun bounds(): ScreenRectangle? = bounds

        fun submit() {
            Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.addGuiElement(this)
        }

        override fun buildVertices(vertexConsumer: VertexConsumer) {
            //top
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x0.toFloat(), this.y0.toFloat()).setColor(this.col1)
            vertexConsumer.addVertexWith2DPose(this.pose(), (this.x0 + gradientWidth).toFloat(), (this.y0 + gradientWidth).toFloat()).setColor(this.col2)
            vertexConsumer.addVertexWith2DPose(this.pose(), (this.x1 - gradientWidth).toFloat(), (this.y0 + gradientWidth).toFloat()).setColor(this.col2)
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x1.toFloat(), this.y0.toFloat()).setColor(this.col1)
            //left
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x0.toFloat(), this.y1.toFloat()).setColor(this.col1)
            vertexConsumer.addVertexWith2DPose(this.pose(), (this.x0 + gradientWidth).toFloat(), (this.y1 - gradientWidth).toFloat()).setColor(this.col2)
            vertexConsumer.addVertexWith2DPose(this.pose(), (this.x0 + gradientWidth).toFloat(), (this.y0 + gradientWidth).toFloat()).setColor(this.col2)
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x0.toFloat(), this.y0.toFloat()).setColor(this.col1)
            //bottom
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x1.toFloat(), this.y1.toFloat()).setColor(this.col1)
            vertexConsumer.addVertexWith2DPose(this.pose(), (this.x1 - gradientWidth).toFloat(), (this.y1 - gradientWidth).toFloat()).setColor(this.col2)
            vertexConsumer.addVertexWith2DPose(this.pose(), (this.x0 + gradientWidth).toFloat(), (this.y1 - gradientWidth).toFloat()).setColor(this.col2)
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x0.toFloat(), this.y1.toFloat()).setColor(this.col1)
            //right
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x1.toFloat(), this.y0.toFloat()).setColor(this.col1)
            vertexConsumer.addVertexWith2DPose(this.pose(), (this.x1 - gradientWidth).toFloat(), (this.y0 + gradientWidth).toFloat()).setColor(this.col2)
            vertexConsumer.addVertexWith2DPose(this.pose(), (this.x1 - gradientWidth).toFloat(), (this.y1 - gradientWidth).toFloat()).setColor(this.col2)
            vertexConsumer.addVertexWith2DPose(this.pose(), this.x1.toFloat(), this.y1.toFloat()).setColor(this.col1)
        }

        companion object {
            fun create(
                graphics: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int,
                gradientWidth: Float, col1: Int, col2: Int
            ): GradientHoleRectangleRenderState {
                val matrix = Matrix3x2f(graphics.pose())
                val area = graphics.scissorStack.peek()
                return GradientHoleRectangleRenderState(
                    RenderPipelines.GUI, TextureSetup.noTexture(), matrix, x0, y0, x1, y1, gradientWidth, col1, col2,
                    area, getBounds(x0, y0, x1, y1, matrix, area)
                )
            }

            private fun getBounds(
                i: Int, j: Int, k: Int, l: Int, matrix3x2f: Matrix3x2f, screenRectangle: ScreenRectangle?
            ): ScreenRectangle {
                val screenRectangle2 = ScreenRectangle(i, j, k - i, l - j).transformMaxBounds(matrix3x2f)
                return screenRectangle?.intersection(screenRectangle2) ?: screenRectangle2
            }
        }
    }

    companion object {
        private val SKINS_DIR: Path = FabricLoader.getInstance().gameDir.resolve("skins")
        private const val LIST_SKIN_WIDTH = 75
        private const val LIST_SKIN_HEIGHT = 110
        private val TEXT_EQUIPPING = Component.translatable("skins.manage.equipping")
    }
}
