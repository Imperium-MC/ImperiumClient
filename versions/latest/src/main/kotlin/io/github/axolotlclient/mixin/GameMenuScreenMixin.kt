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

package io.github.axolotlclient.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.sugar.Local
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.APIOptions
import io.github.axolotlclient.api.ChatsSidebar
import io.github.axolotlclient.api.FriendsScreen
import io.github.axolotlclient.imperium.menu.ImperiumMenuScreen
import io.github.axolotlclient.modules.hypixel.HypixelAbstractionLayer
import io.github.axolotlclient.modules.hypixel.HypixelMods
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.layouts.GridLayout
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyArg
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.Objects
import java.util.function.Supplier

@Mixin(PauseScreen::class)
abstract class GameMenuScreenMixin protected constructor(title: Component) : Screen(title) {

    @Inject(method = ["createPauseMenu"], at = [At("TAIL")])
    private fun `axolotlclient$addButtons`(ci: CallbackInfo, @Local(name = ["gridLayout"]) widget: GridLayout) {
        if (API.getInstance().isAuthenticated()) {
            var buttonY = height - 30
            if (APIOptions.getInstance().addShortcutButtons.get()) {
                addRenderableWidget(
                    Button.builder(
                        Component.translatable("api.friends"),
                        { _ -> minecraft.gui.setScreen(FriendsScreen(this)) }
                    ).bounds(10, buttonY, 75, 20).build()
                )
                buttonY -= 25
            }
            addRenderableWidget(
                Button.builder(
                    Component.translatable("api.chats"),
                    { _ -> minecraft.gui.setScreen(ChatsSidebar(this)) }
                ).bounds(10, buttonY, 75, 20).build()
            )
        }
        if (AxolotlClientConfigCommon.instance().gameMenuScreenOptionButtonMode.get().showButton()) {
            addRenderableWidget(
                object : Button(
                    widget.x + widget.width,
                    widget.y + 50, 20, 20,
                    Component.translatable("config"),
                    Button.OnPress { _ -> minecraft.gui.setScreen(ImperiumMenuScreen(this)) },
                    Button.CreateNarration { supplier: Supplier<net.minecraft.network.chat.MutableComponent> -> supplier.get() }
                ) {
                    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
                        extractDefaultSprite(graphics)
                        graphics.blit(
                            RenderPipelines.GUI_TEXTURED, AxolotlClientCommon.BADGE_PATH as Identifier,
                            this.x + 2, this.y + 2, 0f, 0f, this.width - 4, this.height - 4, this.width - 4, this.height - 4
                        )
                    }
                }
            )
        }
    }

    @ModifyArg(
        method = ["createPauseMenu"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button\$OnPress;)Lnet/minecraft/client/gui/components/Button\$Builder;",
            ordinal = 1
        )],
        index = 1
    )
    private fun `axolotlclient$clearFeatureRestrictions`(onPress: Button.OnPress): Button.OnPress {
        return Button.OnPress { buttonWidget: Button ->
            if (Objects.equals(
                    HypixelMods.getInstance().cacheMode.get(),
                    HypixelMods.HypixelApiCacheMode.ON_CLIENT_DISCONNECT
                )
            ) {
                HypixelAbstractionLayer.getInstance().clearPlayerData()
            }
            onPress.onPress(buttonWidget)
        }
    }

    @WrapMethod(method = ["lambda\$createPauseMenu\$10"])
    private fun confirmDisconnect(button: Button, original: Operation<Void>) {
        val server = minecraft.currentServer
        if (server != null && AxolotlClient.config().confirmDisconnect.get()) {
            minecraft.gui.setScreen(
                ConfirmScreen(
                    { confirmed: Boolean ->
                        if (confirmed) original.call(button) else minecraft.gui.setScreen(null)
                    },
                    Component.translatable("confirm_disconnect.title"),
                    Component.translatable("confirm_disconnect.message", server.ip)
                )
            )
        } else {
            original.call(button)
        }
    }
}
