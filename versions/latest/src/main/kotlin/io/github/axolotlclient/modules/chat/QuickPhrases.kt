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

package io.github.axolotlclient.modules.chat

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.imperium.ServerGating
import io.github.axolotlclient.modules.AbstractModule
import io.github.axolotlclient.util.FeatureDisablerCommon
import io.github.axolotlclient.util.Util
import io.github.axolotlclient.util.options.ForceableBooleanOption

/**
 * Quick phrases (the AutoGG-class QoL row, config category `quickphrases`): three
 * free-text slots, each with its own keybind, that send the configured text through the
 * ordinary chat-send path.
 */
class QuickPhrases : AbstractModule() {
    @JvmField
    val enabled = ForceableBooleanOption("enabled", true)

    @JvmField
    val phrase1 = StringOption("phrase_1", "gg")
    @JvmField
    val phrase2 = StringOption("phrase_2", "")
    @JvmField
    val phrase3 = StringOption("phrase_3", "")

    private val category = OptionCategory.create("quickphrases")

    private val phraseKeys = arrayOfNulls<AxoKeybinding>(3)

    override fun init() {
        category.add(enabled, phrase1, phrase2, phrase3)
        AxolotlClientCommon.getInstance().getConfig().addCategory(category)

        // Server-denyable through the block_mods channel too (FeatureDisabler pattern).
        FeatureDisablerCommon.registerDeniable(MODULE_ID, enabled)

        for (slot in phraseKeys.indices) {
            val option = phraseOption(slot)
            phraseKeys[slot] = AxoKeybinding.create(AxoKeys.KEY_UNKNOWN, "quick_phrase_" + (slot + 1))
            phraseKeys[slot]!!.`br$registerOnConsumeClick`(Runnable { this.sendPhrase(option) })
        }
    }

    override fun tick() {
        // Re-evaluated every tick so a mid-session deny stops the very next keybind press.
        ServerGating.enforceQoL(enabled, MODULE_ID, LOCK_REASON_KEY)
    }

    private fun phraseOption(slot: Int): StringOption = when (slot) {
        0 -> phrase1
        1 -> phrase2
        else -> phrase3
    }

    /**
     * Sends one phrase slot through the normal chat-send path. Takes the option (not the slot
     * index) so tests can drive the exact path a keypress takes.
     */
    fun sendPhrase(option: StringOption) {
        if (!enabled.get()) {
            return // locked (server deny / block_mods) or toggled off: keybind is a no-op
        }
        if (client.player == null) {
            return
        }
        val phrase = option.get()?.trim() ?: ""
        if (phrase.isEmpty()) {
            return // unconfigured slot: silence, not an error the player has to read
        }
        Util.sendChatMessage(phrase)
    }

    companion object {
        const val MODULE_ID: String = "quick_phrases"
        const val LOCK_REASON_KEY: String = "imperiumclient.gate.quick_phrases"

        private val Instance = QuickPhrases()

        @JvmStatic
        fun getInstance(): QuickPhrases = Instance
    }
}
