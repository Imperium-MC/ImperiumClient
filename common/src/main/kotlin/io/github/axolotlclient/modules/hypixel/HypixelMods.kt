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

package io.github.axolotlclient.modules.hypixel

import java.util.ArrayList
import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.api.Request
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.modules.AbstractCommonModule
import io.github.axolotlclient.modules.hypixel.bedwars.BedwarsMod

class HypixelMods : AbstractCommonModule() {
    @JvmField
    val cacheMode = EnumOption("cache_mode", HypixelApiCacheMode::class.java, HypixelApiCacheMode.ON_CLIENT_DISCONNECT)

    private val category: OptionCategory = OptionCategory.create("hypixel-mods")
    private val subModules: MutableList<AbstractHypixelMod> = ArrayList()
    private val removeLobbyJoinMessages = BooleanOption("removeLobbyJoinMessages", false)

    private val modApi = HypixelModApi()

    override fun init() {
        category.add(cacheMode)
        category.add(removeLobbyJoinMessages)

        addSubModule(LevelHead.getInstance())
        addSubModule(AutoGG.getInstance())
        addSubModule(AutoTip.getInstance())
        addSubModule(NickHider.getInstance())
        addSubModule(AutoBoop.getInstance())
        addSubModule(Skyblock.getInstance())
        addSubModule(BedwarsMod.getInstance())
        addSubModule(StatsMod.getInstance())

        subModules.forEach { it.init() }

        AxolotlClientConfigCommon.instance().addCategory(category)

        Events.END_RESOURCE_RELOAD.register(HypixelMessages.getInstance())

        Events.RECEIVE_CHAT_MESSAGE.register(Consumer { event ->
            AutoBoop.getInstance().handleMessage(event.getOriginalMessage())
            HypixelMessages.getInstance().process(removeLobbyJoinMessages, "lobby_join", event)
        })

        modApi.init()
    }

    override fun tick() {
        subModules.forEach { abstractHypixelMod ->
            if (abstractHypixelMod.tickable()) {
                abstractHypixelMod.tick()
            }
        }
    }

    private fun addSubModule(mod: AbstractHypixelMod) {
        this.subModules.add(mod)
        val category = mod.getCategory()
        if (category != null) {
            this.category.add(category)
        }
    }

    enum class HypixelApiCacheMode {
        ON_CLIENT_DISCONNECT, ON_PLAYER_DISCONNECT,
    }

    fun getStatus(): Request? = modApi.getStatus()

    companion object {
        @JvmField
        val INSTANCE = HypixelMods()

        @JvmStatic
        fun getInstance(): HypixelMods = INSTANCE
    }
}
