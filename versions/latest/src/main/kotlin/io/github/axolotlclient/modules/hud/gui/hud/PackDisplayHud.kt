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

package io.github.axolotlclient.modules.hud.gui.hud

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

import com.google.gson.JsonParser
import com.mojang.blaze3d.platform.NativeImage
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.modules.hud.gui.entry.TextHudEntry
import io.github.axolotlclient.modules.hud.util.DrawPosition
import io.github.axolotlclient.util.DrawUtil
import io.github.axolotlclient.util.DrawUtil.drawString
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier

class PackDisplayHud : TextHudEntry(120, 18, true) {

    @JvmField
    val widgets = ArrayList<PackWidget>()
    private val iconsOnly = BooleanOption("iconsonly", false)

    // Alpine profile settings (M6): display_description adds the pack.mcmeta description
    // line under the name; trim_extension strips ".zip"/".mcpack" suffixes from titles.
    @JvmField
    val displayDescription = BooleanOption("packdisplay.display_description", false)

    @JvmField
    val trimExtension = BooleanOption("packdisplay.trim_extension", true)

    private val mc: Minecraft = super.client as Minecraft
    private var placeholder: PackWidget? = null

    override fun renderComponent(context: AxoRenderContext, f: Float) {
        val graphics = context as GuiGraphicsExtractor

        val pos: DrawPosition = contentPos

        if (widgets.isEmpty()) init()

        if (background!!.get()) {
            DrawUtil.fillRect(graphics, getBounds(), backgroundColor!!.get())
        }

        if (outline!!.get()) DrawUtil.outlineRect(graphics, getBounds(), outlineColor!!.get())

        var y = pos.y() + 1
        for (i in widgets.indices.reversed()) { // Badly reverse the order (I'm sure there are better ways to do this)
            widgets[i].render(graphics, pos.x() + 1, y)
            y += widgets[i].height()
        }
        if (y - pos.y() != getContentHeight()) {
            setContentHeight(y - pos.y())
            onBoundsUpdate()
        }
    }

    override fun init() {
        val selected = mc.getResourcePackRepository().getSelectedPacks()
        val valid = selected.stream()
            .filter { p ->
                !(p.location().title().contents is TranslatableContents && (p.location().title().contents as TranslatableContents).key.matches(Regex("pack\\.name\\.fabricMods?")))
            }
            .toList()
        val listSize = valid.size
        valid.forEach { profile ->
            profile.open().forEach { p ->
                try {
                    p.use { pack ->
                        if (listSize == 1) {
                            widgets.add(createWidget(profile.getTitle(), pack))
                        } else if (pack.packId() != "vanilla") {
                            widgets.add(createWidget(profile.getTitle(), pack))
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
        }

        var w = 20
        widgets.forEach { packWidget ->
            val textW = mc.font.width(packWidget.getName()) + 20
            if (textW > w) w = textW
        }
        setContentWidth(w)

        setContentHeight(widgets.sumOf { it.height() } + 1)
        onBoundsUpdate()
    }

    private fun createWidget(displayName: Component, pack: net.minecraft.server.packs.PackResources): PackWidget {
        val supplier = pack.getRootResource("pack.png")!!
        val stream = supplier.get()
        val id = Identifier.fromNamespaceAndPath(ID.namespace, ID.path + "/" + pack.packId())
        mc.textureManager.register(id, DynamicTexture({ id.toString() }, NativeImage.read(stream)))
        stream.close()
        var name = displayName.string
        if (trimExtension.get()) {
            name = name.replaceFirst(Regex("(?i)\\.(zip|mcpack)$"), "")
        }
        return PackWidget(name, readDescription(pack), id)
    }

    /** Best-effort pack.mcmeta description line; null when absent or unreadable. */
    private fun readDescription(pack: net.minecraft.server.packs.PackResources): String? {
        if (!displayDescription.get()) {
            return null
        }
        val supplier = pack.getRootResource("pack.mcmeta") ?: return null
        try {
            supplier.get().use { meta ->
                val root = JsonParser.parseReader(InputStreamReader(meta, StandardCharsets.UTF_8))
                if (!root.isJsonObject) {
                    return null
                }
                val description = root.asJsonObject.get("description")
                if (description != null && description.isJsonPrimitive) {
                    val text = description.asString
                    return if (text.isBlank()) null else text
                }
                if (description != null && description.isJsonObject && description.asJsonObject.has("text")) {
                    val text = description.asJsonObject.get("text").asString
                    return if (text.isBlank()) null else text
                }
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    override fun renderPlaceholderComponent(graphics: AxoRenderContext, f: Float) {
        var updateBounds = false
        if (getContentHeight() < 18) {
            setContentHeight(18)
            updateBounds = true
        }
        if (getContentWidth() < 56) {
            setContentWidth(56)
            updateBounds = true
        }
        if (updateBounds) {
            onBoundsUpdate()
        }
        if (placeholder == null) {
            try {
                mc.getVanillaPackResources().fullResources().use { defaultPack ->
                    placeholder = createWidget(Component.literal(defaultPack.packId()), defaultPack)
                }
            } catch (ignored: Exception) {
            }
        } else {
            placeholder!!.render(graphics as GuiGraphicsExtractor, contentPos.x() + 1, contentPos.y() + 1)
        }
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(iconsOnly)
        options.add(displayDescription)
        options.add(trimExtension)
        return options
    }

    override fun getId(): Identifier = ID

    fun update() {
        widgets.clear()
        init()
    }

    inner class PackWidget(@JvmField val name: String, private val description: String?, texture: Identifier) {

        private val texture: Identifier = texture

        /** Row height: 17 for an icon row, +9 when the description line is shown. */
        fun height(): Int = if (description != null) 26 else 17

        fun getName(): String = name

        fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
            if (!iconsOnly.get()) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 16, 16, 16, 16)
            }
            drawString(graphics, name, x + 18, y + 16 / 2 - 9 / 2, textColor.get().toInt(), shadow.get())
            if (description != null) {
                drawString(
                    graphics, description, x + 18, y + 16 / 2 - 9 / 2 + 9,
                    textColor.get().withAlpha(170).toInt(), shadow.get()
                )
            }
        }
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "packdisplayhud")
    }
}
