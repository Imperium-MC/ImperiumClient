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

package io.github.axolotlclient.mixin

import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.scrollableTooltips.ScrollableTooltips
import io.github.axolotlclient.modules.zoom.Zoom
import net.minecraft.client.MouseHandler
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyArg
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import kotlin.math.sign

@Mixin(MouseHandler::class)
abstract class MouseMixin {

    @Inject(
        method = ["onScroll"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z")],
        cancellable = true
    )
    private fun `axolotlclient$scrollTooltips`(window: Long, scrollDeltaX: Double, scrollDeltaY: Double, ci: CallbackInfo) {
        if (ScrollableTooltips.getInstance().enabled.get() && sign(scrollDeltaY) != 0.0) {
            if (ScrollableTooltips.getInstance().onScroll(scrollDeltaY > 0)) {
                ci.cancel()
            }
        }
    }

    @ModifyArg(
        method = ["onScroll"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/ScrollWheelHandler;getNextScrollWheelSelection(DII)I")]
    )
    private fun `axolotlclient$scrollZoom`(scrollAmount: Double): Double {
        if (scrollAmount != 0.0 && Zoom.getInstance().scroll(scrollAmount)) {
            return 0.0
        }
        return scrollAmount
    }

    @Inject(method = ["setIgnoreFirstMove"], at = [At("HEAD")])
    private fun `axolotlclient$onResolutionChanged`(ci: CallbackInfo) {
        // Resize and rebuild!
        HudManagerCommon.getInstance()!!.refreshAllBounds()
    }
}
