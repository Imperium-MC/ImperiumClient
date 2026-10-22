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

package io.github.axolotlclient.mixin.api

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.multiplayer.FriendsMultiplayerScreen
import io.github.axolotlclient.api.requests.FriendRequest
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At

// No file-level val / companion constant here: any extra class in this declared mixin
// package (file facade `...MixinKt` or `...$Companion`) is rejected by the mixin subsystem
// with IllegalClassLoadError when the injected handler references it ("cannot be referenced
// directly"). The mod-list probe below runs inline instead — the loaded-mod set is fixed
// after launch, so the value cannot change between calls.
@Mixin(JoinMultiplayerScreen::class)
abstract class JoinMulitplayerScreenMixin protected constructor(title: Component) : Screen(title) {

    @Shadow
    @Final
    private val lastScreen: Screen? = null

    @WrapOperation(
        method = ["init"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/HeaderAndFooterLayout;addTitleHeader(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/Font;)V"
        )]
    )
    private fun modifyHeader(
        instance: HeaderAndFooterLayout, message: Component, font: Font, original: Operation<Void>
    ) {
        if (API.getInstance().isAuthenticated() && !FabricLoader.getInstance().isModLoaded("world-host")) {
            instance.setHeaderHeight(60)
            val header = instance.addToHeader(LinearLayout.vertical()).spacing(8)
            header.addChild(StringWidget(message, font), LayoutSettings::alignHorizontallyCenter)
            val buttons = header.addChild(LinearLayout.horizontal()).spacing(4)
            buttons.addChild(
                Button.builder(Component.translatable("api.servers")) { _ -> }.width(100).build()
            ).active = false
            val friends = buttons.addChild(
                Button.builder(
                    Component.translatable("api.servers.friends", "...")
                ) { _ -> minecraft.gui.setScreen(FriendsMultiplayerScreen(lastScreen)) }.width(100).build()
            )
            FriendRequest.getInstance().getOnlineFriendCount()
                .thenAccept { count -> friends.setMessage(Component.translatable("api.servers.friends", count)) }
        } else {
            original.call(instance, message, font)
        }
    }
}
