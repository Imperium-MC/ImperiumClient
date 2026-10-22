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
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.DoubleOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.entry.BoxHudEntry
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DefaultOptions

abstract class PlayerHudCommon : BoxHudEntry(62, 94, true), DynamicallyPositionable {
    @JvmField
    protected val rotation = DoubleOption("rotation", 0.0, 0.0, 360.0)
    @JvmField
    protected val dynamicRotation = BooleanOption("dynamicrotation", true)
    @JvmField
    protected val autoHide = BooleanOption("autoHide", false)
    @JvmField
    protected val anchor: EnumOption<AnchorPoint> = DefaultOptions.getAnchorPoint(this)
    @JvmField
    protected var lastYawOffset = 0f
    @JvmField
    protected var yawOffset = 0f
    @JvmField
    protected var lastYOffset = 0f
    @JvmField
    protected var yOffset = 0f
    @JvmField
    protected var hideTime: Long = 0

    /**
     * The rendered player and their health change continuously, so this entry always
     * re-renders live and is never served from the `hud_frame_limit` recording.
     */
    override fun dynamicContent(): Boolean = true

    override fun tickable(): Boolean = true

    override fun tick() {
        lastYawOffset = yawOffset
        yawOffset *= .93f
        lastYOffset = yOffset
    }

    override fun render(ctx: AxoRenderContext, delta: Float) {
        if (autoHide.get()) {
            if (isPerformingAction()) {
                hideTime = -1
            } else if (hideTime == -1L) {
                hideTime = System.currentTimeMillis()
            }

            if (hideTime != -1L && System.currentTimeMillis() - hideTime > 500) {
                return
            }
        }
        super.render(ctx, delta)
    }

    override fun renderComponent(ctx: AxoRenderContext, delta: Float) {
        renderPlayer(ctx, false, getRawTrueContentX().toDouble(), getRawTrueContentY().toDouble(), delta)
    }

    override fun renderPlaceholderComponent(ctx: AxoRenderContext, delta: Float) {
        renderPlayer(ctx, true, getRawTrueContentX().toDouble(), getRawTrueContentY().toDouble(), 0f)
    }

    protected abstract fun renderPlayer(ctx: AxoRenderContext, placeholder: Boolean, x: Double, y: Double, delta: Float)

    override fun getId(): AxoIdentifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(anchor)
        options.add(dynamicRotation)
        options.add(rotation)
        options.add(autoHide)
        return options
    }

    override fun getAnchor(): AnchorPoint = anchor.get()

    protected abstract fun isPerformingAction(): Boolean

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "playerhud")
    }
}
