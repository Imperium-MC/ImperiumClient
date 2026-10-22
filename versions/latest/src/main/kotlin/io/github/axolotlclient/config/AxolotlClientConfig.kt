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

package io.github.axolotlclient.config

import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.options.Option
import io.github.axolotlclient.AxolotlClientConfig.api.ui.ConfigUI
import io.github.axolotlclient.AxolotlClientConfig.api.util.Color
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.ColorOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.OptionBase
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringArrayOption
import io.github.axolotlclient.AxolotlClientConfig.impl.ui.RecreatableScreen
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.bridge.AxoMinecraftClient
import io.github.axolotlclient.config.screen.CreditsScreen
import io.github.axolotlclient.config.screen.ProfilesScreen
import io.github.axolotlclient.imperium.emotes.EmoteWheelConfig
import io.github.axolotlclient.imperium.enforcement.EnforcementConfig
import io.github.axolotlclient.imperium.media.MediaConfig
import io.github.axolotlclient.mixin.OverlayTextureAccessor
import io.github.axolotlclient.util.ClientColors
import io.github.axolotlclient.util.options.GenericOption
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.texture.DynamicTexture
import java.util.Locale

class AxolotlClientConfig : AxolotlClientConfigCommon() {

    enum class TitleScreenFriendsButtonMode(val isIcon: Boolean) {
        ICON(true),
        BUTTON(false);

        override fun toString(): String {
            return "title_screen_friends_button_mode." + super.toString().lowercase(Locale.ROOT)
        }
    }

    @JvmField
    val lowShield = BooleanOption("lowShield", false)

    @JvmField
    val customLoadingScreenColor = BooleanOption("custom_loading_bg_color", false)

    @JvmField
    val loadingScreenColor = ColorOption("loadingBgColor", Color(239, 50, 61, 255))

    @JvmField
    val openCredits = GenericOption(
        "Credits", "Open Credits",
        GenericOption.ActionListener {
            Minecraft.getInstance().gui.setScreen(CreditsScreen(Minecraft.getInstance().gui.screen()))
        }
    )

    @JvmField
    val titleScreenFriendsButtonMode: EnumOption<TitleScreenFriendsButtonMode> =
        EnumOption("title_screen_friends_button_mode", TitleScreenFriendsButtonMode::class.java, TitleScreenFriendsButtonMode.ICON)

    val options: MutableList<Option<*>> = ArrayList()

    init {
        general.add(customLoadingScreenColor)
        general.add(loadingScreenColor)
        general.add(titleScreenFriendsButtonMode)
        general.add(openCredits)

        ConfigUI.getInstance().runWhenLoaded {
            general.options.removeIf { o: Option<*> -> o.name == "configStyle" }
            // Note: kept the original order (prefix first, then the "rounded" filter, which can
            // never match on the prefixed string) to stay behavior-identical to the Java code.
            val themes = ConfigUI.getInstance().styleNames
                .map { s -> "configStyle." + s }
                .filter { s -> AxolotlClientCommon.SHADERS_SUPPORTED || !s.startsWith("rounded") }
                .toTypedArray()
            if (themes.size > 1) {
                general.add(
                    object : StringArrayOption(
                        "configStyle", themes,
                        "configStyle." + ConfigUI.getInstance().currentStyle.name,
                        OptionBase.ChangeListener { s: String ->
                            ConfigUI.getInstance().setStyle(s.split(".")[1])

                            AxoMinecraftClient.getInstance().execute(Runnable {
                                val newScreen: Screen? = RecreatableScreen.tryRecreate(Minecraft.getInstance().gui.screen())
                                if (newScreen != null) {
                                    Minecraft.getInstance().gui.setScreen(newScreen)
                                }
                            })
                        }
                    ) {
                        override fun fromSerializedValue(value: String) {
                            super.fromSerializedValue(value)
                            changeListener.onChange(get())
                        }
                    }
                )
                AxolotlClient.getInstance().configManager.load()
            } else {
                AxolotlClient.getInstance().configManager.load()
            }
        }

        rendering.add(lowShield)

        // Local rules enforcement (W4-C): outgoing-packet interceptor scoped to ImperiumMC.
        config.add(EnforcementConfig.category)

        // Wave 5 media: emote wheel slots (W5-A) and the now-playing toggle (W5-B).
        config.add(EmoteWheelConfig.category)
        config.add(MediaConfig.category)

        general.add(
            GenericOption(
                "profiles.title", "profiles.configure",
                GenericOption.ActionListener {
                    Minecraft.getInstance().gui.setScreen(ProfilesScreen(Minecraft.getInstance().gui.screen()))
                }
            ), false
        )
    }

    override fun updateWindowTitle(useCustom: Boolean) {
        Minecraft.getInstance().updateTitle()
    }

    override fun updateHitColor(value: Color) {
        val texture: DynamicTexture =
            ((Minecraft.getInstance().gameRenderer.overlayTexture()) as OverlayTextureAccessor).`axolotlclient$getTexture`()
        texture.pixels.fillRect(0, 0, 16, 8, ClientColors.ARGB.invertAlpha(value.toInt()))
        texture.upload()
    }
}
