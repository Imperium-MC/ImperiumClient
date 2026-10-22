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

import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.BiFunction
import java.util.function.Function
import java.util.regex.Pattern
import java.util.stream.Collectors

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringArrayOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.PlatformDispatch
import io.github.axolotlclient.util.ThreadExecuter
import io.github.axolotlclient.util.options.GenericOption

class AutoBoop : AbstractHypixelMod {
    protected val filters: MutableList<String> = ArrayList()
    protected val cat: OptionCategory = OptionCategory.create("autoboop")
    protected val enabled = BooleanOption("enabled", "autoboop.enabled.tooltip", false)
    protected val filterList: GenericOption =
        object : GenericOption(
            "autoboop.filterlist", "autoboop.filterlist.configure",
            GenericOption.ActionListener { PlatformDispatch.`autoBoop$openFiltersScreen`(filters) }
        ) {
            override fun toSerializedValue(): String =
                filters.stream().map { s -> s.toByteArray(StandardCharsets.UTF_8) }
                    .map { s -> Base64.getEncoder().encodeToString(s) }
                    .collect(Collectors.joining(","))

            override fun fromSerializedValue(s: String?) {
                filters.clear()
                filters.addAll(
                    s!!.split(",").stream().map { v -> Base64.getDecoder().decode(v) }
                        .map { v -> String(v, StandardCharsets.UTF_8) }.toList()
                )
            }
        }
    protected val filterListMode = StringArrayOption(
        "autoboop.filterlist.mode",
        *FilterListMode.entries.stream().map { obj -> obj.id }.toList().toTypedArray()
    )

    fun handleMessage(message: String) {
        if (!enabled.get()) {
            return
        }

        ThreadExecuter.scheduleTask { // execute off-thread since the string manipulation for the filter list could potentially take a bit
            val matcher = FRIEND_JOINED.matcher(message)
            if (matcher.matches()) {
                val player = matcher.group(1)
                if (FilterListMode.fromId(filterListMode.get())
                    .func.apply(player, filters)
                ) {
                    CompletableFuture.runAsync(
                        {
                            AxoMinecraftClient.getInstance().`br$sendToServer`("/boop $player")
                            AxolotlClientCommon.getInstance().logger.info("Booped $player")
                        },
                        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS, AxoMinecraftClient.getInstance())
                    )
                }
            }
        }
    }

    override fun init() {
        cat.add(enabled)
        cat.add(filterListMode)
        cat.add(filterList)
    }

    override fun getCategory(): OptionCategory = cat

    private enum class FilterListMode(val id: String, val func: BiFunction<String, List<String>, Boolean>) {
        DISABLED("disabled", BiFunction { _, _ -> true }),
        WHITELIST("whitelist", BiFunction { s, list -> list.contains(s) }),
        BLACKLIST("blacklist", BiFunction { s, list -> !list.contains(s) });

        companion object {
            private val idMap = entries.associateBy { it.id }

            @JvmStatic
            fun fromId(id: String): FilterListMode =
                idMap[id] ?: throw IllegalArgumentException("Could not resolve mode: $id")
        }
    }

    companion object {
        private val FRIEND_JOINED = Pattern.compile("^Friend > (\\b[A-Za-z0-9_§]{3,16}\\b) joined\\.\$")

        private val instance = AutoBoop()

        @JvmStatic
        fun getInstance(): AutoBoop = instance
    }
}
