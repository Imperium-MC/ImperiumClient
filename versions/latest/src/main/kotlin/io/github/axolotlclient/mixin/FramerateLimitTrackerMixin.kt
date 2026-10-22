/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

import com.mojang.blaze3d.platform.FramerateLimitTracker
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.util.FrameLimiter
import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Frame limiter seam (performance category, M3): vanilla reads `getFramerateLimit()`
 * once per frame and paces the frame with it, so clamping the returned value is the whole
 * integration — the vanilla `FramerateLimiter` then does the sleeping for us, with
 * its own overshoot smoothing intact.
 *
 * Input tracking rides along on vanilla's `onInputReceived`, so idle detection
 * uses exactly the same signals (mouse move, key press) as the vanilla inactivity option.
 *
 * Our cap only ever lowers the vanilla value ([FrameLimiter.applyCap]), so the
 * two limiters can never race: vanilla's inactivity throttle or the user's max framerate
 * wins whenever it is stricter.
 */
@Mixin(FramerateLimitTracker::class)
abstract class FramerateLimitTrackerMixin {

    @Inject(method = ["onInputReceived"], at = [At("HEAD")])
    private fun `axolotlclient$notePerformanceInput`(ci: CallbackInfo) {
        FrameLimiter.noteInput(Util.getMillis())
    }

    @Inject(method = ["getFramerateLimit"], at = [At("RETURN")], cancellable = true)
    private fun `axolotlclient$applyPerformanceLimiter`(cir: CallbackInfoReturnable<Int>) {
        val vanillaLimit = cir.getReturnValueI()
        if (vanillaLimit <= 0) {
            return
        }
        val mc = Minecraft.getInstance()
        // Never throttle while a loading/reload overlay is up (world load, resource reload),
        // and never slow a client gametest run — its tick waits are wall-clock sensitive.
        if (mc.gui.overlay() != null || System.getProperty("fabric.client.gametest") != null) {
            return
        }
        val window = mc.getWindow()
        val ownCap = FrameLimiter.capFor(
            AxolotlClient.config().frameLimiter.get(),
            window.isIconified,
            window.isFocused,
            FrameLimiter.idleMillis(Util.getMillis()),
            AxolotlClient.config().backgroundFps.get(),
            AxolotlClient.config().afkFps.get(),
            AxolotlClient.config().afkDelay.get(),
            AxolotlClient.config().longAfkFps.get(),
            AxolotlClient.config().longAfkDelay.get()
        )
        val effective = FrameLimiter.applyCap(vanillaLimit, ownCap)
        if (effective != vanillaLimit) {
            cir.setReturnValue(effective)
        }
    }
}
