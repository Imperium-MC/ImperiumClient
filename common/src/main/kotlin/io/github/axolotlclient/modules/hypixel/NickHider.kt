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

import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import io.github.axolotlclient.api.util.BiContainer
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.util.AxoText

class NickHider : AbstractHypixelMod {
    @JvmField
    val hiddenNameSelf = StringOption("hiddenNameSelf", "You")
    @JvmField
    val hiddenNameOthers = StringOption("hiddenNameOthers", "Player")
    @JvmField
    val hideOwnName = BooleanOption("hideOwnName", false)
    @JvmField
    val hideOtherNames = BooleanOption("hideOtherNames", false)
    @JvmField
    val hideOwnSkin = BooleanOption("hideOwnSkin", false)
    @JvmField
    val hideOtherSkins = BooleanOption("hideOtherSkins", false)
    private val category: OptionCategory = OptionCategory.create("nickhider")

    override fun init() {
        category.add(hiddenNameSelf)
        category.add(hiddenNameOthers)
        category.add(hideOwnName)
        category.add(hideOtherNames)
        category.add(hideOwnSkin)
        category.add(hideOtherSkins)
    }

    override fun getCategory(): OptionCategory = category

    fun editMessage(message: AxoText): AxoText {
        val minecraft = AxoMinecraftClient.getInstance()

        if (hideOwnName.get() || hideOtherNames.get()) {
            val msg = message.`br$getRawString`()

            val replacements = ArrayList<BiContainer<String, String>>()
            if (minecraft.`br$getPlayer`() != null) {
                // TODO: is .br$getGameProfile().br$getName() good?
                val playerName = minecraft.`br$getPlayer`()!!.`br$getName`()
                if (hideOwnName.get() && msg.contains(playerName)) {
                    replacements.add(BiContainer.of(playerName, hiddenNameSelf.get()))
                }
            }

            if (hideOtherNames.get() && minecraft.`br$getWorld`() != null) {
                for (player in minecraft.`br$getWorld`()!!.`br$getPlayers`()) {
                    if (player === minecraft.`br$getPlayer`()) {
                        continue
                    }

                    // TODO: is .br$getGameProfile().br$getName() good?
                    if (msg.contains(player.`br$getName`())) {
                        replacements.add(BiContainer.of(player.`br$getName`(), hiddenNameOthers.get()))
                    }
                }
            }

            if (replacements.isNotEmpty()) {
                return editComponent(message, replacements, AxoText.literal(""))
            }
        }
        return message
    }

    fun editComponent(text: AxoText, find: String, replace: String): AxoText =
        editComponent(text, listOf(BiContainer.of(find, replace)), AxoText.literal(""))

    private fun editComponent(
        component: AxoText,
        replacements: List<BiContainer<String, String>>,
        edited: AxoText.Mutable
    ): AxoText.Mutable {
        component.`br$visit` { edit, style ->
            var e = edit
            for (entry in replacements) {
                e = e.replace(entry.left!!, entry.right!!)
            }
            edited.`br$append`(AxoText.literal(e).`br$setStyle`(style))
        }

        return edited
    }

    companion object {
        private val Instance = NickHider()

        @JvmStatic
        fun getInstance(): NickHider = Instance
    }
}
