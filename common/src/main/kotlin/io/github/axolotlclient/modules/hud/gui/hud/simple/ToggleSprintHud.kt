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

import java.util.ArrayList
import java.util.Random
import java.util.function.Consumer

import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.FloatOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.IntegerOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.bridge.Platform
import io.github.axolotlclient.bridge.events.Events
import io.github.axolotlclient.bridge.key.AxoKeybinding
import io.github.axolotlclient.bridge.key.AxoKeys
import io.github.axolotlclient.bridge.render.AxoRenderContext
import io.github.axolotlclient.bridge.util.AxoI18n
import io.github.axolotlclient.bridge.util.AxoIdentifier
import io.github.axolotlclient.modules.hud.gui.entry.SimpleTextHudEntry
import io.github.axolotlclient.util.options.ForceableBooleanOption

/**
 * This implementation of Hud modules is based on KronHUD.
 * <a href="https://github.com/DarkKronicle/KronHUD">Github Link.</a>
 *
 * <p>License: GPL-3.0</p>
 */
class ToggleSprintHud : SimpleTextHudEntry(100) {
    @JvmField
    val toggleSneak = ForceableBooleanOption("toggleSneak", true)
    private val toggleSprint = BooleanOption("toggleSprint", true)
    private val randomPlaceholder = BooleanOption("randomPlaceholder", false)
    private val placeholder = StringOption("placeholder", "No keys pressed")
    private val hideInsteadOfPlaceholder = BooleanOption("togglesprint.hide_instead_of_placeholder", true)
    @JvmField
    val flyBoost = BooleanOption("fly_boost", true)
    @JvmField
    val flyBoostMultiplier = FloatOption("fly_boost_multiplier", 2.0f, 1.0f, 2.5f)

    // Alpine parity (toggle_sneak_sprint): fly_boost_hold keeps the boost on the sprint key
    // (the pre-existing behaviour); off applies it the whole time you fly. vertical_boost
    // extends it to the climb/sink impulse, and fly_while_swimming applies it to swim
    // movement (both consumed by the Player/LocalPlayer mixins).
    @JvmField
    val flyBoostHold = BooleanOption("fly_boost_hold", true)
    @JvmField
    val verticalBoost = BooleanOption("vertical_boost", false)
    @JvmField
    val flyWhileSwimming = BooleanOption("fly_while_swimming", false)
    private val doubleTap = BooleanOption("double_tap", true)
    private val doubleTapWindow = IntegerOption("double_tap_window_ms", 250, 50, 1000)

    private val sprintToggle: AxoKeybinding = AxoKeybinding.create(AxoKeys.KEY_K, "key.toggleSprint")
    private val sneakToggle: AxoKeybinding = AxoKeybinding.create(AxoKeys.KEY_I, "key.toggleSneak")

    val sprintToggled = BooleanOption("sprintToggled", false)
    val sneakToggled = BooleanOption("sneakToggled", false)

    private val texts: MutableList<String> = ArrayList()
    private var text = ""
    private var lastSneakTap = 0L

    override fun init() {
        sprintToggle.`br$registerOnConsumeClick`(Runnable { sprintToggled.set(toggleSprint.get() && !sprintToggled.get()) })
        sneakToggle.`br$registerOnConsumeClick`(Runnable { sneakToggled.set(toggleSneak.get() && !sneakToggled.get()) })
        Events.KEY_INPUT.register(Consumer { key ->
            if (doubleTap.get() && toggleSneak.get() && isEnabled() &&
                key == client.`br$getGameOptions`().`br$getSneakKeybind`().`br$getBoundKey`()
            ) {
                val now = Platform.getMeasuringTimeMs()
                if (lastSneakTap > 0 && now - lastSneakTap <= doubleTapWindow.get()) {
                    sneakToggled.set(!sneakToggled.get())
                    // Require a fresh pair of taps for the next toggle.
                    lastSneakTap = 0
                } else {
                    lastSneakTap = now
                }
            }
        })
    }

    override fun getSaveOptions(): MutableList<Option<*>> {
        val options = super.getSaveOptions()
        options.add(sprintToggled)
        options.add(sneakToggled)
        return options
    }

    override fun getId(): AxoIdentifier = ID

    override fun tickable(): Boolean = true

    /**
     * Whether the fly-boost multiplier currently applies (read by the Player/LocalPlayer
     * mixins): the module and the boost toggle must be on, and — while
     * `fly_boost_hold` is on — the sprint key must be held.
     */
    fun flyBoostActive(): Boolean {
        if (!isEnabled() || !flyBoost.get()) {
            return false
        }
        return !flyBoostHold.get() || client.`br$getGameOptions`().`br$getSprintKeybind`().`br$isPressed`()
    }

    override fun getConfigurationOptions(): MutableList<Option<*>> {
        val options = super.getConfigurationOptions()
        options.add(toggleSprint)
        options.add(toggleSneak)
        options.add(doubleTap)
        options.add(doubleTapWindow)
        options.add(flyBoost)
        options.add(flyBoostMultiplier)
        options.add(flyBoostHold)
        options.add(verticalBoost)
        options.add(flyWhileSwimming)
        options.add(randomPlaceholder)
        options.add(placeholder)
        options.add(hideInsteadOfPlaceholder)
        return options
    }

    override fun render(ctx: AxoRenderContext, delta: Float) {
        if (hideInsteadOfPlaceholder.get() &&
            !client.`br$getGameOptions`().`br$getSneakKeybind`().`br$isPressed`() &&
            !client.`br$getGameOptions`().`br$getSprintKeybind`().`br$isPressed`() &&
            (!toggleSneak.get() || !sneakToggled.get()) && (!toggleSprint.get() || !sprintToggled.get())
        ) {
            return
        }

        super.render(ctx, delta)
    }

    override fun getValue(): String {
        if (client.`br$getGameOptions`().`br$getSneakKeybind`().`br$isPressed`()) {
            return AxoI18n.translate("sneaking_pressed")
        }

        if (client.`br$getGameOptions`().`br$getSprintKeybind`().`br$isPressed`()) {
            return AxoI18n.translate("sprinting_pressed")
        }

        if (toggleSneak.get() && sneakToggled.get()) {
            return AxoI18n.translate("sneaking_toggled")
        }

        if (toggleSprint.get() && sprintToggled.get()) {
            return AxoI18n.translate("sprinting_toggled")
        }
        return getPlaceholderValue()
    }

    private fun getRandomPlaceholder(): String {
        if (text == "") {
            loadRandomPlaceholder()
        }
        return text
    }

    private fun loadRandomPlaceholder() {
        try {
            AxoMinecraftClient.getInstance().`br$getResourceManager`()
                .`br$getResource`(AxoIdentifier.of("texts/splashes.txt")).`br$asReader`().use { reader ->
                    while (true) {
                        val string = reader.readLine() ?: break
                        val trimmed = string.trim { it <= ' ' }
                        if (trimmed.isNotEmpty()) {
                            texts.add(trimmed)
                        }
                    }

                    text = texts[Random().nextInt(texts.size)]
                }
        } catch (e: Exception) {
            text = ""
        }
    }

    override fun getPlaceholderValue(): String =
        if (randomPlaceholder.get()) getRandomPlaceholder() else placeholder.get()

    companion object {
        @JvmField
        val ID: AxoIdentifier = AxoIdentifier.of("kronhud", "togglesprint")
    }
}
