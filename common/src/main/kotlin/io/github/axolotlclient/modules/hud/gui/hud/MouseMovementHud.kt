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

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.GraphicsOption
import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.events.types.PlayerDirectionChangeEvent
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.util.MathUtil

class MouseMovementHud : BoxHudEntry(53, 35, true), DynamicallyPositionable {
    private val anchor: EnumOption<AnchorPoint> = DefaultOptions.getAnchorPoint(this)
    private val mouseMovementIndicatorInner = GraphicsOption(
        "mouseMovementIndicator", arrayOf(
            intArrayOf(0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, -1, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0, 0, 0, 0)
        )
    )
    private val mouseMovementIndicatorOuter = GraphicsOption(
        "mouseMovementIndicatorOuter", arrayOf(
            intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),
            intArrayOf(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1),
            intArrayOf(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1),
            intArrayOf(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1),
            intArrayOf(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1),
            intArrayOf(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1),
            intArrayOf(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1),
            intArrayOf(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1),
            intArrayOf(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1),
            intArrayOf(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1),
            intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1)
        )
    )

    private var mouseX = 0f
    private var mouseY = 0f
    private var lastMouseX = 0f
    private var lastMouseY = 0f

    init {
        Events.PLAYER_DIRECTION_CHANGE.register { event -> onPlayerDirectionChange(event) }
    }

    fun onPlayerDirectionChange(event: PlayerDirectionChangeEvent) {
        // Implementation credit goes to TheKodeToad
        // This project has the author's approval to use this
        // https://github.com/Sol-Client/Client/blob/main/game/src/main/java/io/github/solclient/client/mod/impl/hud/keystrokes/KeystrokesMod.java
        mouseX += (event.yaw - event.prevYaw) / 7f
        mouseY += (event.pitch - event.prevPitch) / 7f
        // 0, 0 will be the center of the HUD element
        val halfWidth = getContentWidth() / 2f
        val halfHeight = getContentHeight() / 2f
        mouseX = MathUtil.clamp(mouseX, -halfWidth + 2, halfWidth - 2)
        mouseY = MathUtil.clamp(mouseY, -halfHeight + 2, halfHeight - 2)
    }

    override fun renderComponent(context: AxoRenderContext, delta: Float) {
        val spaceY = getContentY()
        val spaceX = getContentX()

        val calculatedMouseX = (lastMouseX + ((mouseX - lastMouseX) * delta)) - 5
        val calculatedMouseY = (lastMouseY + ((mouseY - lastMouseY) * delta)) - 5

        context.`br$drawTexture`(
            Platform.createTexture(mouseMovementIndicatorInner),
            spaceX + (getContentWidth() / 2) - 7 / 2 - 1, spaceY + getContentHeight() / 2 - (7 / 2), 7, 7
        )
        // Woah KodeToad, good use of translate
        context.`br$translateMatrix`(calculatedMouseX, calculatedMouseY)
        context.`br$drawTexture`(
            Platform.createTexture(mouseMovementIndicatorOuter),
            spaceX + (getContentWidth() / 2) - 1, spaceY + getContentHeight() / 2, 11, 11
        )
    }

    override fun renderPlaceholderComponent(context: AxoRenderContext, delta: Float) {
        renderComponent(context, delta)
    }

    override fun tick() {
        lastMouseX = mouseX
        lastMouseY = mouseY
        mouseX *= .75f
        mouseY *= .75f
    }

    override fun tickable(): Boolean = true

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(anchor)
        options.add(mouseMovementIndicatorInner)
        options.add(mouseMovementIndicatorOuter)
        return options
    }

    override fun getAnchor(): AnchorPoint = anchor.get()

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "mousemovementhud")
    }
}
