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

package io.github.axolotlclient.util

import io.github.axolotlclient.AxolotlClient
import java.util.concurrent.atomic.AtomicBoolean
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * farming protections (Alpine parity, wave 3): the bottom block of a still-growing sugar
 * cane or bamboo stack is not breakable while anything of the same crop grows above it —
 * one misclick on the bottom block fells the whole plant, while harvesting from the top
 * keeps it producing. The veto is enforced in `MultiPlayerGameModeMixin` at the head of
 * all three vanilla destroy paths, so no break packet is ever sent and no local break
 * prediction starts.
 *
 * The render-hides (`disable_cactus_rendering`, `disable_string_rendering`) instead veto
 * the block in `SectionCompilerMixin` — the chunk mesh builder asks each block state for
 * its [net.minecraft.world.level.block.RenderShape], and INVISIBLE means "no model in the
 * mesh". Hiding is scoped to that call site, so cactus items, held cactus and the targeted
 * block's outline are untouched. Toggling either re-meshes the world
 * ([remeshWorld], wired through the config's `onFarmingRenderHidesChanged` hook) because
 * the baked chunk geometry is otherwise static.
 */
object FarmingProtections {

    /**
     * Pure veto logic: is `state` the bottom block of a crop stack that is still growing
     * above (i.e. the block below is a different block, the block above the same crop)?
     * Harvesting the top of a stack, breaking a lonely plant with nothing above, and any
     * block that is not cane or bamboo are all allowed through.
     */
    @JvmStatic
    fun isProtectedStack(state: BlockState, below: BlockState, above: BlockState): Boolean {
        val config = AxolotlClient.config() ?: return false
        return when {
            state.`is`(Blocks.SUGAR_CANE) -> config.farmingProtectBottomCane.get() &&
                !below.`is`(Blocks.SUGAR_CANE) && above.`is`(Blocks.SUGAR_CANE)
            state.`is`(Blocks.BAMBOO) -> config.farmingProtectBottomBamboo.get() &&
                !below.`is`(Blocks.BAMBOO) && above.`is`(Blocks.BAMBOO)
            else -> false
        }
    }

    /**
     * Whether breaking `pos` must be vetoed right now. Reads at most three block states;
     * without a level (menus, early startup) nothing is vetoed.
     */
    @JvmStatic
    fun shouldVetoBreak(pos: BlockPos): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        return isProtectedStack(
            level.getBlockState(pos),
            level.getBlockState(pos.below()),
            level.getBlockState(pos.above())
        )
    }

    /**
     * Whether the chunk mesh builder must skip this block state's model (farming
     * render-hides). Cactus hides the planted cactus blocks; string hides tripwire.
     */
    @JvmStatic
    fun shouldHideBlockState(state: BlockState): Boolean {
        val config = AxolotlClient.config() ?: return false
        if (config.farmingDisableCactusRendering.get() && state.`is`(Blocks.CACTUS)) {
            return true
        }
        return config.farmingDisableStringRendering.get() && state.`is`(Blocks.TRIPWIRE)
    }

    /** Set by the config change hook, drained on the client thread by [tick]. */
    private val remeshPending = AtomicBoolean(false)

    /**
     * Flags a full chunk re-mesh; called by the version config whenever a farming
     * render-hide toggle flips (the baked chunk geometry is static, so toggles need a
     * re-mesh to take effect). Thread-safe by construction — the option can also change
     * during config load on background threads; the actual re-mesh runs on [tick].
     */
    @JvmStatic
    fun requestRemesh() {
        remeshPending.set(true)
    }

    /**
     * Client-thread drain: performs one pending re-mesh. 26.3's re-mesh entrypoint is the
     * level extractor's allChanged — it flags the compiled-geometry invalidation the next
     * extract pass feeds to the LevelRenderer. A no-op while no world is loaded.
     */
    @JvmStatic
    fun tick() {
        if (!remeshPending.compareAndSet(true, false)) {
            return
        }
        val client = Minecraft.getInstance()
        if (client.level != null) {
            client.levelExtractor.allChanged()
        }
    }
}
