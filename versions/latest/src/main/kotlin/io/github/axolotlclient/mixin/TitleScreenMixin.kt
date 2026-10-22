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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfigCommon
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.APIOptions
import io.github.axolotlclient.api.FriendsScreen
import io.github.axolotlclient.api.NewsScreen
import io.github.axolotlclient.api.chat.ChatListScreen
import io.github.axolotlclient.api.requests.GlobalDataRequest
import io.github.axolotlclient.imperium.menu.ImperiumMenuScreen
import io.github.axolotlclient.imperium.menu.ImperiumTitleScreen
import io.github.axolotlclient.modules.auth.Auth
import io.github.axolotlclient.modules.auth.AuthWidget
import io.github.axolotlclient.util.ThreadExecuter
import net.minecraft.SharedConstants
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.FriendsButton
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyArg
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.net.URI

@Mixin(TitleScreen::class)
abstract class TitleScreenMixin protected constructor() : Screen(Component.empty()) {

    @Shadow
    private var realmsNotificationsScreen: RealmsNotificationsScreen? = null

    @Shadow
    private var friends: FriendsButton? = null

    @Inject(method = ["createNormalMenuOptions"], at = [At("HEAD")])
    private fun addButtonsPre(y: Int, rowHeight: Int, cir: CallbackInfoReturnable<Int>) {
        var leftButtonY = 10
        if (Auth.getInstance().showButton.get()) {
            val button = addRenderableWidget(AuthWidget(10, leftButtonY))
            leftButtonY += button.height + 4
        }
        if (APIOptions.getInstance().addShortcutButtons.get()) {
            val shortcutButtonY = leftButtonY
            val addApiButtons = Runnable {
                minecraft.execute {
                    var bY = shortcutButtonY
                    if (!AxolotlClient.config().titleScreenFriendsButtonMode.get().isIcon) {
                        addRenderableWidget(
                            Button.builder(
                                Component.translatable("api.friends"),
                                { _ -> minecraft.gui.setScreen(FriendsScreen(this)) }
                            ).bounds(10, bY, 50, 20).build()
                        )
                        bY += 24
                    }
                    addRenderableWidget(
                        Button.builder(
                            Component.translatable("api.chats"),
                            { _ -> minecraft.gui.setScreen(ChatListScreen(this)) }
                        ).bounds(10, bY, 50, 20).build()
                    )
                }
            }
            if (API.getInstance().isSocketConnected()) {
                addApiButtons.run()
            } else {
                API.addStartupListener(addApiButtons, API.ListenerType.ONCE)
            }
        }

        ThreadExecuter.scheduleTask {
            GlobalDataRequest.get().thenAccept { data ->
                var buttonY = 10
                if (APIOptions.getInstance().updateNotifications.get() &&
                    data.success &&
                    data.latestVersion.isNewerThan(AxolotlClient.VERSION)
                ) {
                    addRenderableWidget(
                        Button.builder(
                            Component.translatable("api.new_version_available"),
                            ConfirmLinkScreen.confirmLink(
                                this, URI.create("https://modrinth.com/mod/axolotlclient/versions")
                            )
                        ).bounds(width - 90, buttonY, 80, 20).build()
                    )
                    buttonY += 24
                }
                if (APIOptions.getInstance().displayNotes.get() &&
                    data.success && data.notes.isNotEmpty()
                ) {
                    addRenderableWidget(
                        Button.builder(
                            Component.translatable("api.notes"),
                            { _ -> minecraft.gui.setScreen(NewsScreen(this)) }
                        ).bounds(width - 90, buttonY, 80, 20).build()
                    )
                }
            }
        }
    }

    @Inject(method = ["init"], at = [At("HEAD")], cancellable = true)
    private fun `imperiumclient$v3TitleShell`(ci: CallbackInfo) {
        // UI v3: every vanilla TitleScreen becomes the Imperium shell (docs/design/README.md).
        // The one-shot flag lets the shell's "vanilla menu" icon circle show the original layout.
        if ((this as Any) is ImperiumTitleScreen) {
            return
        }
        if (ImperiumTitleScreen.showVanillaMenu) {
            ImperiumTitleScreen.showVanillaMenu = false
            return
        }
        ci.cancel()
        if (this.minecraft != null) {
            this.minecraft!!.gui.setScreen(ImperiumTitleScreen())
        }
    }

    @Inject(method = ["realmsNotificationsEnabled"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$disableRealmsNotifications`(cir: CallbackInfoReturnable<Boolean>) {
        if (AxolotlClientConfigCommon.instance().titleScreenOptionButtonMode.get().showButton()) {
            this.realmsNotificationsScreen = null
            cir.setReturnValue(false)
        }
    }

    @WrapOperation(
        method = ["createNormalMenuOptions"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/Button;builder(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button\$OnPress;)Lnet/minecraft/client/gui/components/Button\$Builder;",
            ordinal = 3
        )]
    )
    private fun `axolotlclient$noRealmsbutOptionsButton`(
        messageIn: Component, onPressIn: Button.OnPress, original: Operation<Button.Builder>
    ): Button.Builder {
        var message = messageIn
        var onPress = onPressIn
        if (AxolotlClientConfigCommon.instance().titleScreenOptionButtonMode.get().showButton()) {
            message = Component.translatable("config")
            onPress = Button.OnPress { _ -> minecraft.gui.setScreen(ImperiumMenuScreen(this)) }
        }
        return original.call(message, onPress)
    }

    @ModifyArg(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
        )],
        index = 1
    )
    private fun `axolotlclient$setVersionText`(s: String): String {
        return "Minecraft " + SharedConstants.getCurrentVersion().name() + "/Romanica Client " +
            AxolotlClient.VERSION
    }

    @Inject(method = ["realmsNotificationsEnabled"], at = [At("HEAD")], cancellable = true)
    private fun `axolotlclient$noRealmsIcons`(cir: CallbackInfoReturnable<Boolean>) {
        if (AxolotlClientConfigCommon.instance().titleScreenOptionButtonMode.get().showButton()) {
            cir.setReturnValue(false)
        }
    }

    @WrapOperation(
        method = ["init"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;",
            ordinal = 3
        )]
    )
    private fun modifyButtons(
        instance: TitleScreen, listener: GuiEventListener, original: Operation<GuiEventListener>,
        @Local(name = ["language"]) language: SpriteIconButton,
        @Local(name = ["accessibility"]) accessibility: SpriteIconButton,
        @Local(name = ["topPos"]) topPos: LocalIntRef
    ): GuiEventListener {
        if (!AxolotlClient.config().titleScreenFriendsButtonMode.get().isIcon) {
            topPos.set(topPos.get() + 12 - 24)
            language.setPosition(this.width / 2 - 100 - 24, topPos.get())
            accessibility.setPosition(this.width / 2 + 104, topPos.get())
            removeWidget(friends!!)
            if (listener is Button) {
                listener.setY(topPos.get())
            }
        }
        return original.call(instance, listener)
    }

    @Inject(method = ["init"], at = [At("HEAD")])
    private fun checkAPIStart(ci: CallbackInfo) {
        if (!API.getInstance().isSocketConnected() && !Auth.getInstance().getCurrent().isOffline()) {
            API.getInstance().startup(Auth.getInstance().getCurrent())
        }
    }
}
