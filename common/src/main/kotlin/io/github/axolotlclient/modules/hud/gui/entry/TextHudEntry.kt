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

package io.github.axolotlclient.modules.hud.gui.entry

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.modules.hud.gui.component.DynamicallyPositionable
import io.github.axolotlclient.modules.hud.gui.layout.AnchorPoint
import io.github.axolotlclient.modules.hud.util.DefaultOptions
import io.github.axolotlclient.util.ClientColors

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
abstract class TextHudEntry(width: Int, height: Int, backgroundAllowed: Boolean) :
    BoxHudEntry(width, height, backgroundAllowed), DynamicallyPositionable {

    @JvmField
    protected val textColor: ColorOption = ColorOption("textcolor", ClientColors.WHITE)
    @JvmField
    protected val shadow: BooleanOption = BooleanOption("shadow", getShadowDefault())
    @JvmField
    protected val anchor: EnumOption<AnchorPoint> = DefaultOptions.getAnchorPoint(getDefaultAnchor(), this)

    protected open fun getShadowDefault(): Boolean = true

    /**
     * Text HUDs are data readouts (FPS, CPS, coordinates, timers, live lists) whose content
     * changes on its own, so they always re-render live and are never served from the
     * `hud_frame_limit` recording. The few entries whose text only changes through
     * discrete events opt back out with their own override.
     */
    override fun dynamicContent(): Boolean = true

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(textColor)
        options.add(shadow)
        options.add(anchor)
        return options
    }

    override fun getAnchor(): AnchorPoint = anchor.get()

    protected open fun getDefaultAnchor(): AnchorPoint = AnchorPoint.TOP_LEFT
}
