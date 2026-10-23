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

package io.github.axolotlclient.modules.hud.gui.keystrokes

import com.google.common.collect.ImmutableList
import io.github.axolotlclient.AxolotlClientConfig.api.util.Colors
import io.github.axolotlclient.modules.hud.gui.hud.KeystrokeHud
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import org.apache.commons.lang3.ArrayUtils
import kotlin.math.min

class SpecialKeystrokeSelectionList(val keyBindsScreen: AddSpecialKeystrokeScreen, minecraft: Minecraft) :
    ContainerObjectSelectionList<SpecialKeystrokeSelectionList.Entry>(
        minecraft, keyBindsScreen.width, keyBindsScreen.layout.getContentHeight(),
        keyBindsScreen.layout.getHeaderHeight(), ITEM_HEIGHT
    ) {

    init {
        val strokes = ArrayUtils.clone(KeystrokeHud.SpecialKeystroke.entries.toTypedArray())
        strokes.sort()

        for (keyMapping in strokes) {
            this.addEntry(KeyEntry(keyMapping))
        }
    }

    override fun getRowWidth(): Int = 340

    abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    inner class KeyEntry(key: KeystrokeHud.SpecialKeystroke) : Entry() {
        private val boundKey: Component = key.key.translatedKeyMessage
        private val addButton: Button
        private val keystroke: KeystrokeHud.Keystroke = keyBindsScreen.hud.newSpecialStroke(key)

        init {
            this.addButton = Button.builder(Component.translatable("keystrokes.stroke.add")) {
                keyBindsScreen.hud.keystrokes!!.add(keyBindsScreen.hud.newSpecialStroke(key))
            }
                .bounds(0, 0, 75, 20)
                .build()
        }

        override fun extractContent(
            guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovering: Boolean, partialTick: Float
        ) {
            val i = scrollBarX() - 10
            val j = getContentY() - 2
            val k = i - 5 - this.addButton.width
            this.addButton.setPosition(k, j)
            this.addButton.extractRenderState(guiGraphicsExtractor, mouseX, mouseY, partialTick)
            guiGraphicsExtractor.pose().pushMatrix()
            val rect = keystroke.getRenderPosition()
            val scale = min(getContentHeight() / rect.height().toFloat(), 100 / rect.width().toFloat())
            guiGraphicsExtractor.pose().translate(getContentX().toFloat(), getContentY().toFloat())
            guiGraphicsExtractor.pose().scale(scale, scale)
            guiGraphicsExtractor.pose().translate(-rect.x().toFloat(), -rect.y().toFloat())
            keystroke.render(guiGraphicsExtractor)
            guiGraphicsExtractor.pose().popMatrix()
            guiGraphicsExtractor.text(
                minecraft.font, boundKey, getContentX() + 110 + (k - getContentX() - 110) / 3,
                getContentY() + getContentHeight() / 2 - 9 / 2, Colors.GRAY.toInt()
            )
        }

        override fun children(): List<GuiEventListener> = ImmutableList.of(this.addButton)

        override fun narratables(): List<NarratableEntry> = ImmutableList.of(this.addButton)
    }

    companion object {
        private const val ITEM_HEIGHT = 24
    }
}
