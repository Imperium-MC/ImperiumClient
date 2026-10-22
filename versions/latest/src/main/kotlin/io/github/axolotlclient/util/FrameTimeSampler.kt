/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util

import net.minecraft.client.Minecraft
import java.util.function.LongConsumer

/**
 * Per-frame sampling point for the M3 performance benchmarks: `GameRendererMixin` calls
 * [sample] once per rendered frame (after the world, before the GUI), and the
 * sampler reads `Minecraft.getFrameTimeNs()` — the same number the debug FPS overlay
 * derives its figure from. Idle unless a consumer is installed; the gametest is the only
 * installer.
 */
object FrameTimeSampler {

    @Volatile
    private var consumer: LongConsumer? = null

    @JvmStatic
    fun install(frameTimeNanosConsumer: LongConsumer) {
        consumer = frameTimeNanosConsumer
    }

    @JvmStatic
    fun clear() {
        consumer = null
    }

    /**
     * Called once per rendered frame from the render thread. Reads the previous frame's
     * duration, exactly as `Minecraft.getFps()` does.
     */
    @JvmStatic
    fun sample() {
        val active = consumer
        if (active != null) {
            active.accept(Minecraft.getInstance().frameTimeNs)
        }
    }
}
