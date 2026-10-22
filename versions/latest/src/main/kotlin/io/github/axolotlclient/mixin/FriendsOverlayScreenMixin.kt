/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.api.FriendsScreen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.tabs.TabNavigationBar
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.jspecify.annotations.Nullable
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.util.function.Supplier

@Mixin(FriendsOverlayScreen::class)
abstract class FriendsOverlayScreenMixin private constructor(title: Component) : Screen(title) {

    @Shadow
    private var tabNavigationBar: TabNavigationBar? = null

    @Shadow
    @Final
    private val backgroundScreen: @Nullable Screen? = null

    @Unique
    private var axolotlclientButton: Button? = null

    @Inject(
        method = ["init"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/friends/FriendsOverlayScreen;repositionElements()V")]
    )
    private fun addButton(ci: CallbackInfo) {
        val nav = tabNavigationBar ?: return
        axolotlclientButton = addRenderableWidget(
            object : Button(
                nav.right + 4, nav.y, 20, 20,
                Component.translatable("config"),
                Button.OnPress { _ -> minecraft.gui.setScreen(if (backgroundScreen is FriendsScreen) backgroundScreen else FriendsScreen(backgroundScreen)) },
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

    @Inject(method = ["repositionElements"], at = [At("TAIL")])
    private fun repositionButton(ci: CallbackInfo) {
        val nav = tabNavigationBar
        if (axolotlclientButton != null && nav != null) {
            axolotlclientButton!!.setPosition(nav.right + 4, nav.y)
        }
    }

    @Inject(method = ["mouseClicked"], at = [At("HEAD")], cancellable = true)
    private fun includeButtonClicks(event: MouseButtonEvent, doubleClick: Boolean, cir: CallbackInfoReturnable<Boolean>) {
        if (axolotlclientButton != null && axolotlclientButton!!.isMouseOver(event.x(), event.y())) {
            if (axolotlclientButton!!.mouseClicked(event, doubleClick)) cir.setReturnValue(true)
        }
    }
}
