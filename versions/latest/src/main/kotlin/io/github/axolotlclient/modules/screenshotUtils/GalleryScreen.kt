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

package io.github.axolotlclient.modules.screenshotUtils

import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.requests.FriendRequest
import io.github.axolotlclient.api.requests.UserRequest
import io.github.axolotlclient.util.HorizontalGradientRectangleRenderState
import io.github.axolotlclient.util.MathUtil
import io.github.axolotlclient.util.Watcher
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.ActiveTextCollector
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class GalleryScreen(private val parent: Screen?) : Screen(Component.translatable("gallery.title")) {

    private var current: Tab<*> = Tab.LOCAL

    private val watcher: Watcher? = Watcher.createSelfTicking(SCREENSHOTS_DIR, Runnable {
        if (current === Tab.LOCAL) {
            rebuildWidgets()
        }
    })

    override fun init() {
        val online = API.getInstance().isAuthenticated()
        val layout = HeaderAndFooterLayout(this)
        layout.setHeaderHeight(40)
        val header = layout.addToHeader(LinearLayout.vertical().spacing(4))
        header.defaultCellSetting().alignHorizontallyCenter()
        header.addChild(StringWidget(title, font))
        if (online) {
            header.addChild(StringWidget(current.title, font))
        }

        val columnCount = (width - (marginLeftRight * 2) + entrySpacing - 13) / (entryWidth + entrySpacing) // -13 to always have enough space for the scrollbar

        val area = ImageList(minecraft, layout.width, layout.contentHeight, layout.headerHeight, entryHeight + entrySpacing, columnCount)

        layout.addToContents(area, LayoutSettings::alignHorizontallyLeft)
        setInitialFocus(area)
        CompletableFuture.runAsync {
            try {
                loadTab(current, columnCount, area)
            } catch (e: Exception) {
                val error = LinearLayout.vertical().spacing(8)
                error.defaultCellSetting().alignVerticallyMiddle().alignVerticallyMiddle()
                error.addChild(StringWidget(Component.translatable("gallery.error.loading"), font))
                setInitialFocus(error.addChild(Button.builder(Component.translatable("gallery.reload")) { rebuildWidgets() }.build()))
                layout.addToContents(error)
            }
        }

        val footer = layout.addToFooter(LinearLayout.horizontal()).spacing(4)
        footer.defaultCellSetting().alignHorizontallyCenter()
        val buttonWidth = if (columnCount <= 5 && online) 100 else 150
        if (online) {
            val switchTab = if (current === Tab.SHARED) {
                Button.builder(Component.translatable("gallery.tab.local")) { setTab(Tab.LOCAL) }
            } else {
                Button.builder(Component.translatable("gallery.tab.shared")) { setTab(Tab.SHARED) }
            }
            footer.addChild(switchTab.width(buttonWidth).build())
        }
        footer.addChild(
            Button.builder(Component.translatable("gallery.download_external")) {
                minecraft.gui.setScreen(DownloadImageScreen(this))
            }
                .width(buttonWidth).build()
        )
        footer.addChild(
            Button.builder(CommonComponents.GUI_BACK) { onClose() }
                .width(buttonWidth).build()
        )

        layout.arrangeElements()
        layout.visitWidgets(this::addRenderableWidget)
    }

    override fun onClose() {
        Tab.LOCAL.loadingCache.forEach { (_, instance) ->
            if (instance != null) {
                minecraft.textureManager.release(instance.id)
            }
        }
        Tab.LOCAL.loadingCache.clear()
        Tab.SHARED.loadingCache.forEach { (_, instance) ->
            if (instance != null) {
                minecraft.textureManager.release(instance.id)
            }
        }
        Tab.SHARED.loadingCache.clear()
        Watcher.close(watcher)
        minecraft.gui.setScreen(parent)
    }

    private fun setTab(tab: Tab<*>) {
        current = tab
        rebuildWidgets()
    }

    private fun <T> loadTab(tab: Tab<T>, columnCount: Int, area: ImageList) {
        val images: List<T> = tab.list.call()
        val size = images.size
        var i = 0
        while (i < size) {
            val row = ImageListEntry(columnCount, area)
            area.addEntry(row)
            var x = 0
            while (x < columnCount) {
                if (i + x >= size) {
                    break
                }
                val p = images[i + x]
                val entry = ImageEntry(entryWidth, entryHeight, Callable { tab.loader.load(p) }, row)
                row.add(entry)
                x++
            }
            i += columnCount
        }
    }

    // This image list is loading its entries lazily! :)
    private inner class ImageEntry(
        width: Int, height: Int,
        private val instanceSupplier: Callable<ImageInstance>, private val row: ImageListEntry
    ) : Button(
        0, 0, width, height, Component.empty(),
        Button.OnPress { }, Button.DEFAULT_NARRATION
    ) {

        private val font: Font = Minecraft.getInstance().font
        private var loadStart: Long = 0
        private var future: CompletableFuture<ImageInstance?>? = null

        private fun load(): CompletableFuture<ImageInstance?> {
            if (future == null) {
                loadStart = net.minecraft.util.Util.getMillis()
                future = CompletableFuture.supplyAsync {
                    try {
                        val instance = instanceSupplier.call()
                        setMessage(Component.literal(instance.filename))
                        return@supplyAsync instance
                    } catch (e: Exception) {
                        minecraft.schedule(Runnable { row.remove(this) })
                        return@supplyAsync null
                    }
                }
            }
            return future!!
        }

        override fun onPress(inputWithModifiers: net.minecraft.client.input.InputWithModifiers) {
            minecraft.gui.setScreen(ImageScreen.create(this@GalleryScreen, load(), false))
        }

        override fun extractContents(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float
        ) {
            if (load().isDone && load().join() != null) {
                val instance = load().join()!!
                guiGraphicsExtractor.blit(
                    RenderPipelines.GUI_TEXTURED, instance.id, x, y, 0f, 0f, width, height - font.lineHeight - 2,
                    width, height - font.lineHeight - 2
                )
                extractDefaultLabel(
                    guiGraphicsExtractor.textRendererForWidget(
                        this, GuiGraphicsExtractor.HoveredTextEffects.NONE
                    )
                )
            } else {
                val delta = MathUtil.easeInOutCubic(
                    ((net.minecraft.util.Util.getMillis() - loadStart) % 1000f / 1000f).toDouble()
                ).toFloat()

                guiGraphicsExtractor.fill(x + 2, y + 2, right - 2, bottom - font.lineHeight - 2, bgColor)
                drawHorizontalGradient(
                    guiGraphicsExtractor, x + 2, y + 2, bottom - font.lineHeight - 2,
                    MathUtil.lerp(delta, x + 2, right - 2)
                )

                guiGraphicsExtractor.fill(x + 2, bottom - font.lineHeight - 1, right - 2, bottom - 2, bgColor)
                drawHorizontalGradient(
                    guiGraphicsExtractor, x + 2, bottom - font.lineHeight - 1, bottom - 2,
                    MathUtil.lerp(delta, x + 2, right - 2)
                )
            }
            guiGraphicsExtractor.`br$outlineRect`(x, y, width, height, if (isHoveredOrFocused) -1 else bgColor)
        }

        private fun drawHorizontalGradient(guiGraphicsExtractor: GuiGraphicsExtractor, x1: Int, y1: Int, y2: Int, x2: Int) {
            HorizontalGradientRectangleRenderState.create(guiGraphicsExtractor, x1, y1, x2, y2, bgColor, accent).submit()
        }

        override fun createNarrationMessage(): MutableComponent {
            return wrapDefaultNarrationMessage(Component.translatable("gallery.image.view"))
        }

        override fun extractScrollingStringOverContents(
            activeTextCollector: ActiveTextCollector, component: Component, i: Int
        ) {
            activeTextCollector.acceptScrollingWithDefaultCenter(
                component, this.x + i,
                this.x + this.width - i, this.y + height - font.lineHeight - 1,
                this.y + this.height
            )
        }
    }

    private class ImageListEntry(private val size: Int, private val list: ImageList) :
        ContainerObjectSelectionList.Entry<ImageListEntry>() {

        private val buttons: MutableList<ImageEntry> = ArrayList(size)

        override fun narratables(): List<NarratableEntry> = buttons

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            if (maxOf(contentX, list.x) <= minOf(contentX + contentWidth, list.x + list.width) - 1 &&
                maxOf(contentY - contentHeight, list.y) <= minOf(contentY + contentHeight * 2, list.y + list.height) - 1
            ) {
                buttons.forEach { e: ImageEntry ->
                    e.y = contentY
                    e.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
                }
            } else {
                buttons.forEach { e: ImageEntry -> e.y = contentY }
            }
        }

        fun add(e: ImageEntry) {
            buttons.add(e)
            repositionButtons()
        }

        fun remove(e: ImageEntry) {
            buttons.remove(e)
            if (buttons.isEmpty()) {
                list.removeEntry(this)
            } else if (buttons.size < size) {
                list.shiftEntries(this)
            }
            repositionButtons()
        }

        private fun repositionButtons() {
            var x = list.rowLeft
            for (e in buttons) {
                e.x = x
                x += e.width + entrySpacing
            }
        }

        fun pop(): ImageEntry {
            val entry = buttons.removeFirst()
            if (buttons.isEmpty()) {
                list.removeEntry(this)
                list.refreshScrollAmount()
            } else if (buttons.size < size) {
                list.shiftEntries(this)
            }
            repositionButtons()
            return entry
        }

        override fun children(): List<GuiEventListener> = buttons
    }

    private class ImageList(
        minecraft: Minecraft, i: Int, j: Int, k: Int, l: Int, columns: Int
    ) : ContainerObjectSelectionList<ImageListEntry>(minecraft, i, j, k, l) {

        private val rowWidth: Int = columns * (entryWidth + entrySpacing) - entrySpacing

        public override fun addEntry(entry: ImageListEntry): Int = super.addEntry(entry)

        override fun getRowWidth(): Int = rowWidth

        override fun getRowLeft(): Int = this.x + this.width / 2 - this.rowWidth / 2

        public override fun removeEntry(entry: ImageListEntry) {
            super.removeEntry(entry)
        }

        fun shiftEntries(origin: ImageListEntry) {
            val originIndex = children().indexOf(origin)
            val lastIndex = children().size - 1
            if (originIndex == lastIndex) {
                return
            }
            val next = children()[originIndex + 1]
            origin.add(next.pop())
        }
    }

    private data class Tab<T>(
        val title: Component, val list: Callable<List<T>>, val loadingCache: MutableMap<T, ImageInstance?>,
        val loader: Loader<T>
    ) {

        fun interface Loader<T> {
            @Throws(Exception::class)
            fun load(obj: T): ImageInstance?
        }

        companion object {
            private fun <T> of(title: Component, list: Callable<List<T>>, loader: Loader<T>): Tab<T> {
                val cache = HashMap<T, ImageInstance?>()
                return Tab(title, list, cache, Loader { o: T ->
                    if (cache.containsKey(o)) {
                        cache[o]
                    } else {
                        val value = loader.load(o)
                        cache[o] = value
                        value
                    }
                })
            }

            val LOCAL: Tab<Path> = of(
                Component.translatable("gallery.title.local"),
                Callable {
                    Files.list(SCREENSHOTS_DIR).use { screenshots: Stream<Path> ->
                        screenshots.filter { p: Path -> Files.isRegularFile(p) }.sorted(
                            java.util.Comparator.comparingLong { p: Path ->
                                try {
                                    Files.getLastModifiedTime(p).toMillis()
                                } catch (e: Exception) {
                                    0L
                                }
                            }.reversed()
                        ).toList()
                    }
                },
                Loader { p: Path -> ImageInstance.LocalImpl(p) }
            )

            val SHARED: Tab<String> = of(
                Component.translatable("gallery.title.shared"),
                Callable {
                    FriendRequest.getInstance().getFriendUuids()
                        .thenApply { res: List<String> ->
                            // sequential joins of each friend's uploaded-image future, flattened
                            // (the Java original joined the futures in order and reduced the lists)
                            res.flatMap { userUuid: String ->
                                UserRequest.getUploadedImages(userUuid).join().orElseGet { emptyList() }
                            }
                        }.join()
                },
                Loader { url: String -> ImageShare.getInstance().downloadImage(url).join() }
            )
        }
    }

    companion object {
        val SCREENSHOTS_DIR: Path = FabricLoader.getInstance().gameDir.resolve(Screenshot.SCREENSHOT_DIR)

        private const val entrySpacing = 4
        private const val entryWidth = 100
        private const val entryHeight = 75
        private const val marginLeftRight = 10
        private val bgColor = Colors.DARK_GRAY.toInt()
        private val accent = Colors.GRAY.withBrightness(0.5f).withAlpha(128).toInt()
    }
}
