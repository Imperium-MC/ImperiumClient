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
package io.github.axolotlclient.api

import com.mojang.blaze3d.platform.InputConstants
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfig.api.ui.screen.ConfigScreen
import io.github.axolotlclient.api.chat.ChannelInvitesScreen
import io.github.axolotlclient.api.chat.ChatListScreen
import io.github.axolotlclient.api.requests.AccountDataRequest
import io.github.axolotlclient.api.requests.AccountSettingsRequest
import io.github.axolotlclient.util.ThreadExecuter
import io.github.axolotlclient.util.dialogs.DialogProvider
import io.github.axolotlclient.util.keybinds.KeyBinds
import io.github.axolotlclient.util.options.GenericOption
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Supplier
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class APIOptions : Options() {

    override fun init() {
        super.init()
        val client = Minecraft.getInstance()

        openPrivacyNoteScreen = Supplier {
            val fut = CompletableFuture<Boolean>()
            val parent = client.gui.screen()
            client.execute(Runnable { client.gui.setScreen(PrivacyNoticeScreen(parent, fut)) })
            fut
        }
        KeyBinds.getInstance().registerWithSimpleAction(
            KeyMapping("api.chats.sidebar.open", InputConstants.KEY_O, KeyBinds.CATEGORY_AXOLOTLCLIENT)
        ) {
            if (API.getInstance().isAuthenticated()) {
                val parent = client.gui.screen()
                client.execute(Runnable { client.gui.setScreen(ChatsSidebar(parent)) })
            }
        }
        category.add(
            GenericOption("viewFriends", "clickToOpen") {
                val parent = client.gui.screen()
                client.execute(Runnable { client.gui.setScreen(FriendsScreen(parent)) })
            }
        )
        category.add(
            GenericOption("viewChats", "clickToOpen") {
                val parent = client.gui.screen()
                client.execute(Runnable { client.gui.setScreen(ChatListScreen(parent)) })
            }
        )
        category.add(
            GenericOption("api.channels.invites.view", "clickToOpen") {
                val parent = client.gui.screen()
                client.execute(Runnable { client.gui.setScreen(ChannelInvitesScreen(parent)) })
            }
        )
        account.add(
            GenericOption("api.account.usernames", "clickToOpen") {
                val parent = client.gui.screen()
                client.execute(Runnable { client.gui.setScreen(UsernameManagementScreen(parent)) })
            }
        )
        account.add(
            GenericOption("api.account.export", "api.account.export_data") {
                ThreadExecuter.scheduleTask(
                    Runnable {
                        if (!API.getInstance().isAuthenticated()) {
                            API.getInstance().notificationProvider
                                .addStatus("api.account.export.failure.title", "api.error.unauthenticated")
                            return@Runnable
                        }
                        DialogProvider.IMPL.save(
                            "Choose export destination", FabricLoader.getInstance().gameDir, ".json"
                        )
                            .thenAccept { opt -> opt.ifPresent { path -> AccountDataRequest.get(path) } }
                    }
                )
            }
        )
        account.add(
            GenericOption("api.account.delete", "api.account.delete_account") {
                val previous: Screen? = client.gui.screen()
                client.gui.setScreen(
                    ConfirmScreen(
                        { b: Boolean ->
                            if (b) {
                                if (!API.getInstance().isAuthenticated()) {
                                    API.getInstance().notificationProvider
                                        .addStatus("api.account.deletion.failure", "api.error.unauthenticated")
                                    return@ConfirmScreen
                                } else {
                                    AccountSettingsRequest.deleteAccount().thenAccept { r ->
                                        if (r) {
                                            API.getInstance().notificationProvider
                                                .addStatus(
                                                    "api.account.deletion.success",
                                                    "api.account.deletion.success.desc"
                                                )
                                        } else {
                                            API.getInstance().notificationProvider
                                                .addStatus(
                                                    "api.account.deletion.failure",
                                                    "api.account.deletion.failure.desc"
                                                )
                                        }
                                        enabled.set(false)
                                    }
                                }
                            }
                            client.gui.setScreen(previous)
                        },
                        Component.translatable("api.account.confirm_deletion"),
                        Component.translatable("api.account.confirm_deletion.desc")
                    )
                )
            }
        )
        val consumer = settingUpdated
        settingUpdated = Consumer { b: Boolean ->
            if (client.gui.screen() is ConfigScreen) {
                consumer.accept(b)
            }
        }
        if (Constants.ENABLED) {
            AxolotlClient.config().addCategory(category)
        }
    }

    companion object {
        private val Instance: Options = APIOptions()

        @JvmStatic
        fun getInstance(): Options {
            return Instance
        }
    }
}
