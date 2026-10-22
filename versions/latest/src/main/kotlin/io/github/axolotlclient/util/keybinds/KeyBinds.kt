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

package io.github.axolotlclient.util.keybinds

import io.github.axolotlclient.AxolotlClientCommon
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import java.util.Collections

class KeyBinds {
    private var binds: MutableList<KeyMapping>? = ArrayList()

    fun register(bind: KeyMapping): KeyMapping {
        val list = binds ?: throw IllegalStateException("Keybind registered too late!")
        list.add(bind)

        return bind
    }

    fun registerWithSimpleAction(bind: KeyMapping, action: Runnable): KeyMapping {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { _ ->
            if (bind.consumeClick()) {
                action.run()
            }
        })
        return register(bind)
    }

    fun process(keys: Array<KeyMapping>): Array<KeyMapping> {
        val keyBinds = ArrayList<KeyMapping>()
        Collections.addAll(keyBinds, *keys)
        val registered = binds!!
        binds = null
        keyBinds.addAll(registered)
        return keyBinds.toTypedArray()
    }

    companion object {
        @JvmField
        val CATEGORY_AXOLOTLCLIENT: KeyMapping.Category =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "title"))

        private val instance = KeyBinds()

        @JvmStatic
        fun getInstance(): KeyBinds = instance
    }
}
