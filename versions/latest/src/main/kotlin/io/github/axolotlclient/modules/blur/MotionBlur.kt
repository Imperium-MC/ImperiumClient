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

package io.github.axolotlclient.modules.blur

import com.mojang.blaze3d.resource.CrossFrameResourcePool
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption
import io.github.axolotlclient.modules.AbstractModule
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.resources.Identifier

class MotionBlur : AbstractModule() {
    @JvmField
    val enabled = BooleanOption("enabled", false)
    @JvmField
    val strength = FloatOption("strength", 50f, 1f, 99f)
    @JvmField
    val inGuis = BooleanOption("inGuis", false)
    @JvmField
    val category: OptionCategory = OptionCategory.create("motionBlur")
    private val postChainId: Identifier = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "motion_blur")

    override fun init() {
        category.add(enabled, strength, inGuis)

        AxolotlClient.config().rendering.add(category)
    }

    @Suppress("DEPRECATION")
    fun render(pool: CrossFrameResourcePool) {
        val shader = client.getShaderManager().getPostChain(postChainId, LevelTargetBundle.MAIN_TARGETS)
        if (shader != null) {
            val target = client.gameRenderer.mainRenderTarget()
            shader.process(target, pool)
            // Uniform is set in PostPassMixin
        }
    }

    companion object {
        private val Instance = MotionBlur()

        @JvmStatic
        fun getInstance(): MotionBlur = Instance

        @JvmStatic
        fun getBlur(): Float = getInstance().strength.get() / 100f
    }
}
