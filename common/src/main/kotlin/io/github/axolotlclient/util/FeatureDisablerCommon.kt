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

package io.github.axolotlclient.util

import com.google.common.base.Preconditions
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.freelook.Freelook
import io.github.axolotlclient.modules.hud.HudManagerCommon
import io.github.axolotlclient.modules.hud.gui.hud.simple.ToggleSprintHud
import io.github.axolotlclient.modules.snaplook.SnapLook
import io.github.axolotlclient.util.options.ForceableBooleanOption
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Supplier

abstract class FeatureDisablerCommon protected constructor() {
    private val disabledServers = HashMap<ForceableBooleanOption, Array<out String>>()
    private val conditions = HashMap<ForceableBooleanOption, Supplier<Boolean>>()
    private var currentAddress = ""

    init {
        Preconditions.checkState(instance == null, "singleton already initialized")
        instance = this
    }

    fun init() {
        setServers(
            Freelook.getInstance().enabled,
            Supplier { Freelook.getInstance().needsDisabling() },
            "hypixel", "mineplex", "gommehd", "nucleoid", "mccisland", "mcpvp.club"
        )
        setServers(SnapLook.getInstance().enabled, NONE, "hypixel", "mineplex", "gommehd", "nucleoid", "mccisland", "mcpvp.club")
        setServers(
            (HudManagerCommon.getInstance().get(ToggleSprintHud.ID) as ToggleSprintHud).toggleSneak,
            NONE,
            "hypixel",
            "gommehd"
        )

        Events.CONNECTION_PLAY_READY.register(Consumer { info ->
            if (info != null) {
                onServerJoin(info.`br$getIp`())
            }
        })

        Events.DISCONNECT.register(this::clear)

        registerChannel()
    }

    protected abstract fun registerChannel()

    fun onServerJoin(address: String) {
        currentAddress = address
        update()
    }

    fun clear() {
        disabledServers.keys.forEach { option -> option.setForceOff(false, "") }
        FEATURES.values.forEach { option -> option.setForceOff(false, "") }
    }

    fun update() {
        disabledServers.forEach { (option, servers) -> disableOption(option, servers, currentAddress) }
    }

    private fun setServers(option: ForceableBooleanOption, condition: Supplier<Boolean>, vararg servers: String) {
        disabledServers[option] = servers
        conditions[option] = condition
    }

    private fun disableOption(option: ForceableBooleanOption, servers: Array<out String>, currentServer: String) {
        var ban = false
        for (s in servers) {
            if (currentServer.lowercase(Locale.ROOT).contains(s.lowercase(Locale.ROOT))) {
                ban = conditions[option]!!.get()
                break
            }
        }

        if (option.isForceOff != ban) {
            option.setForceOff(ban, "ban_reason")
        }
    }

    companion object {
        private val NONE = Supplier<Boolean> { true }

        @JvmField
        protected val CHANNEL_NAME: AxoIdentifier = AxoIdentifier.of(AxolotlClientCommon.MODID, "block_mods")

        // Features that can be disabled on the server's behalf
        // If something should be added here, feel free to ping us via your favorite way.
        @JvmField
        protected val FEATURES: HashMap<String, ForceableBooleanOption> = CommonUtil.make {
            val features = HashMap<String, ForceableBooleanOption>()
            features["freelook"] = Freelook.getInstance().enabled
            features["snaplook"] = SnapLook.getInstance().enabled
            features["timechanger"] = AxolotlClientCommon.getInstance().getConfig().timeChangerEnabled
            features["lowfire"] = AxolotlClientCommon.getInstance().getConfig().lowFire
            features["fullbright"] = AxolotlClientCommon.getInstance().getConfig().fullBright
            features
        }

        private var instance: FeatureDisablerCommon? = null

        @JvmStatic
        fun getInstance(): FeatureDisablerCommon? = instance

        /**
         * Registers a feature id as server-denyable after class init. Used by the latest-version
         * gated modules (auto_fish, auto_click), which cannot be listed in the static map above:
         * they call this from their `init()` before the disabler's channel is registered.
         */
        @JvmStatic
        fun registerDeniable(id: String, option: ForceableBooleanOption) {
            FEATURES[id] = option
        }
    }
}
