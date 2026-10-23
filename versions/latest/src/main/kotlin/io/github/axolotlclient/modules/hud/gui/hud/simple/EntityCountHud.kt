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

package io.github.axolotlclient.modules.hud.gui.hud.simple

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

/**
 * Loaded entity count (Alpine "entity_count" module, parity spec M4).
 *
 * <p>The bridge has no entity-count accessor, so this module lives in versions/latest
 * (CooldownsHud precedent) and reads the client level directly. Alpine's
 * `display_total_count` setting chooses between the total number of loaded
 * entities (`ClientLevel.getEntityCount()`) and the render-culled subset
 * (`entitiesForRendering()`). Counting happens once per tick, never per frame.</p>
 */
class EntityCountHud : SimpleTextHudEntry(true) {

    /** Alpine display_total_count: show all loaded entities instead of the render subset. */
    @JvmField
    val displayTotalCount = BooleanOption("display_total_count", true)

    private val mc: Minecraft = super.client as Minecraft
    private var count = 0

    override fun tickable(): Boolean = true

    override fun tick() {
        val level = mc.level
        if (level == null) {
            count = 0
            return
        }
        count = if (displayTotalCount.get()) {
            level.entityCount
        } else {
            var entities = 0
            for (entity in level.entitiesForRendering()) {
                entities++
            }
            entities
        }
    }

    override fun getValue(): String {
        if (mc.level == null) {
            return getPlaceholderValue()
        }

        return count.toString()
    }

    override fun getPlaceholderValue(): String = "127"

    override fun getLabel(): String = AxoI18n.translate("entitycounthud.entities")

    override fun getId(): Identifier = ID

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(displayTotalCount)
        return options
    }

    companion object {
        @JvmField
        val ID: Identifier = Identifier.fromNamespaceAndPath("imperiumclient", "entitycounthud")
    }
}
