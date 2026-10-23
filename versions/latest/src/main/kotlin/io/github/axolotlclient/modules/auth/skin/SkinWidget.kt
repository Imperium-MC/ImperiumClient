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

import java.util.concurrent.CompletableFuture

import io.github.axolotlclient.modules.auth.Account
import io.github.axolotlclient.modules.auth.Auth
import io.github.axolotlclient.modules.auth.MSApi
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.CommonComponents
import net.minecraft.util.Mth

class SkinWidget(width: Int, height: Int, var skin: Skin?, var cape: Cape?, private val owner: Account) :
    AbstractWidget(0, 0, width, height, CommonComponents.EMPTY) {

    private var rotationX = DEFAULT_ROTATION_X
    var rotationY = DEFAULT_ROTATION_Y
    private var noCape = false
    private var noCapeActive = false

    constructor(width: Int, height: Int, skin: Skin?, owner: Account) : this(width, height, skin, null, owner)

    fun noCape(noCapeActive: Boolean) {
        noCape = true
        this.noCapeActive = noCapeActive
    }

    override fun extractWidgetRenderState(
        guiGraphicsExtractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float
    ) {
        val minecraft = Minecraft.getInstance()

        val scale = FIT_SCALE * this.height / MODEL_HEIGHT
        val pivotY = -1.0625f

        val skinManager = Auth.getInstance().skinManager
        val skinRl = skinManager.loadSkin(skin!!)
        val classic = skin!!.classicVariant()
        val capeRl = if (cape == null) null else skinManager.loadCape(cape!!)

        // You might say that using `hashCode()` like this isn't ideal, but in reality it doesn't matter. These objects get freed
        // correctly by the screen so we mostly only need unique identifiers per widget which `hashCode()` provides.
        val renderer = SkinRenderer.getOrCreate(minecraft, hashCode().toString())
        guiGraphicsExtractor.guiRenderState.addPicturesInPictureState(
            SkinRenderState(
                classic, skinRl as net.minecraft.resources.Identifier,
                capeRl as net.minecraft.resources.Identifier?, this.rotationX, this.rotationY, pivotY,
                this.x, this.y, this.right, this.bottom, scale, guiGraphicsExtractor.scissorStack.peek(), renderer
            )
        )
    }

    override fun onDrag(mouseButtonEvent: MouseButtonEvent, dragX: Double, dragY: Double) {
        this.rotationX = Mth.clamp(this.rotationX - dragY.toFloat() * ROTATION_SENSITIVITY, -ROTATION_X_LIMIT, ROTATION_X_LIMIT)
        this.rotationY += dragX.toFloat() * ROTATION_SENSITIVITY
    }

    override fun playDownSound(handler: SoundManager) {
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
    }

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? = null

    companion object {
        private const val MODEL_HEIGHT = 2.125f
        private const val FIT_SCALE = 0.97f
        private const val ROTATION_SENSITIVITY = 2.5f
        private const val DEFAULT_ROTATION_X = -5.0f
        private const val DEFAULT_ROTATION_Y = 30.0f
        private const val ROTATION_X_LIMIT = 50.0f
    }

    fun isEquipped(): Boolean =
        if (noCape) noCapeActive else if (cape != null) cape!!.active() else skin != null && skin!!.active()

    fun equip(): CompletableFuture<out MSApi.MCProfile> {
        val msApi = Auth.getInstance().msApi
        if (noCape) {
            return msApi.hideCape(owner)
        }
        if (cape != null) {
            return cape!!.equip(msApi, owner)
        }
        if (skin != null) {
            return skin!!.equip(msApi, owner)
        }
        return msApi.resetSkin(owner)
    }

    fun getFocusedAsset(): Asset? = if (noCape) null else cape ?: skin
}
