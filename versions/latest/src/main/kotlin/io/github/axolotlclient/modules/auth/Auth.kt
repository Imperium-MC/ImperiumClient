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

package io.github.axolotlclient.modules.auth

import java.util.Optional
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

import com.mojang.authlib.minecraft.UserApiService
import com.mojang.authlib.services.FriendsService
import com.mojang.authlib.services.MinecraftServicesDiscoveryService
import io.github.axolotlclient.AxolotlClient
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.api.API
import io.github.axolotlclient.api.util.UUIDHelper
import io.github.axolotlclient.mixin.DownloadedPackSourceAccessor
import io.github.axolotlclient.mixin.MinecraftClientAccessor
import io.github.axolotlclient.mixin.ServerPackManagerAccessor
import io.github.axolotlclient.mixin.SplashManagerAccessor
import io.github.axolotlclient.modules.Module
import io.github.axolotlclient.modules.auth.skin.SkinManager
import io.github.axolotlclient.util.ThreadExecuter
import io.github.axolotlclient.util.notifications.Notifications
import io.github.axolotlclient.util.options.GenericOption
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.social.PlayerSocialManager
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler
import net.minecraft.client.multiplayer.ProfileKeyPairManager
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment
import net.minecraft.client.multiplayer.chat.report.ReportingContext
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.Services
import net.minecraft.util.Util

class Auth : Accounts(), Module {
    @JvmField
    val showButton = BooleanOption("auth.showButton", false)
    @JvmField
    val skinManagerAnimations = BooleanOption("skins.manage.animations", true)
    private val mc: Minecraft = Minecraft.getInstance()
    // NOTE (wave 6): constructed lazily, not in a field initializer - the original Java assigned
    // msApi in init(), after AxolotlClientCommon.instance exists; eager construction here runs
    // during companion <clinit> (registerModule(Auth.getInstance())) and MSApi's constructor
    // calls AxolotlClientCommon.getInstance(), which Preconditions-checks the instance.
    val msApi: MSApi by lazy { MSApi(this, Supplier { mc.options.languageCode }) }
    private val viewAccounts = GenericOption("viewAccounts", "clickToOpen") {
        mc.gui.setScreen(AccountsScreen(mc.gui.screen()))
    }
    private val loadingTexture: MutableSet<String> = HashSet()
    private val textures: MutableMap<String, Identifier> = WeakHashMap()
    val skinManager = SkinManager()

    override fun init() {
        load()
        if (isContained(mc.user.sessionId)) {
            setCurrent(
                accounts.stream().filter { account -> account.uuid == UUIDHelper.toUndashed(mc.user.profileId) }
                    .toList().first()
            )
            val currentAccount = getCurrent()
            if (currentAccount != null && currentAccount.needsRefresh()) {
                currentAccount.refresh(msApi).thenRun(Runnable { this.save() })
            }
        } else {
            setCurrent(Account(mc.user.name, UUIDHelper.toUndashed(mc.user.profileId), mc.user.accessToken))
        }

        category.add(showButton, viewAccounts, skinManagerAnimations)
        AxolotlClient.config().general.add(category)
    }

    override fun login(account: Account) {
        if (mc.level != null) {
            return
        }

        if (account.needsRefresh() && !account.isOffline) {
            if (account.isExpired) {
                Notifications.getInstance().addStatus(
                    Component.translatable("auth.notif.title"),
                    Component.translatable("auth.notif.refreshing", account.name)
                )
            }
            account.refresh(msApi).thenAccept { a ->
                if (!a.isExpired) {
                    login(a)
                }
            }.thenRun(Runnable { this.save() })
        } else {
            try {
                API.getInstance().shutdown()
                val mcAccessor = mc as MinecraftClientAccessor
                mcAccessor.`axolotlclient$setSession`(
                    User(account.name, UUIDHelper.fromUndashed(account.uuid), account.authToken, Optional.empty(), Optional.empty())
                )
                val authService: MinecraftServicesDiscoveryService
                if (account.isOffline) {
                    authService = MinecraftServicesDiscoveryService.createOffline(mc.proxy)
                } else {
                    authService = MinecraftServicesDiscoveryService.create(mc.proxy, !mcAccessor.`axolotlclient$isOfflineDevMode`())
                }
                val services = Services.create(authService, mc.gameDirectory)
                mcAccessor.`axolotlclient$setServices`(services)
                val service: UserApiService = authService.createUserApiService(mc.user.accessToken)
                val friendsService: FriendsService = authService.createFriendsService(mc.user.accessToken)
                val updateHandler = RemoteFriendListUpdateHandler(friendsService, mc)
                mcAccessor.`axolotlclient$setRemoteFriendListUpdateHandler`(updateHandler)
                mcAccessor.`axolotlclient$setUserApiService`(service)
                val sourceAccessor = mc.getDownloadedPackSource() as DownloadedPackSourceAccessor
                (sourceAccessor.`axolotlclient$getManager`() as ServerPackManagerAccessor)
                    .`axolotlclient$setDownloader`(
                        sourceAccessor.`axolotlclient$createDownloader`(
                            sourceAccessor.`axolotlcleint$getDownloadQueue`(),
                            { task: Runnable -> mc.schedule(task) }, mc.user, mc.proxy
                        )
                    )
                val socialManager = PlayerSocialManager(mc, service, friendsService, updateHandler)
                if (socialManager.isFriendListEnabled) {
                    updateHandler.start()
                }
                mcAccessor.`axolotlclient$setSocialInteractionsManager`(socialManager)
                mcAccessor.`axolotlclient$setPlayerKeyPairManager`(
                    ProfileKeyPairManager.create(service, mc.user, mc.gameDirectory.toPath())
                )
                mcAccessor.`axolotlclient$setChatReportingContext`(
                    ReportingContext.create(ReportEnvironment.local(), service)
                )
                mcAccessor.`axolotlclient$setProfileFuture`(
                    CompletableFuture.supplyAsync(
                        Supplier { mc.services().sessionService().fetchProfile(mc.user.profileId, true) },
                        Util.nonCriticalIoPool()
                    )
                )
                (mc.gui.splashManager() as SplashManagerAccessor).setUser(mc.user)
                save()
                setCurrent(account)
                Notifications.getInstance().addStatus(
                    Component.translatable("auth.notif.title"),
                    Component.translatable("auth.notif.login.successful", getCurrent()!!.name)
                )
                API.getInstance().startup(account)
            } catch (e: Exception) {
                Notifications.getInstance().addStatus(
                    Component.translatable("auth.notif.title"),
                    Component.translatable("auth.notif.login.failed")
                )
            }
        }
    }

    override fun showAccountsExpiredScreen(account: Account): CompletableFuture<Account> {
        val current = mc.gui.screen()
        val fut = CompletableFuture<Account>()
        mc.execute(Runnable {
            mc.gui.setScreen(
                ConfirmScreen(
                    { bl: Boolean ->
                        if (bl) {
                            msApi.startDeviceAuth().thenRun(Runnable { fut.complete(account) })
                        } else {
                            fut.cancel(true)
                        }
                        mc.gui.setScreen(current)
                    },
                    Component.translatable("auth"),
                    Component.translatable("auth.accountExpiredNotice", account.name)
                )
            )
        })
        return fut
    }

    override fun displayDeviceCode(data: DeviceFlowData) {
        mc.execute(Runnable { mc.gui.setScreen(DeviceCodeDisplayScreen(mc.gui.screen(), data)) })
    }

    private fun loadTexture(uuid: String) {
        if (!loadingTexture.contains(uuid)) {
            loadingTexture.add(uuid)
            ThreadExecuter.scheduleTask(Runnable {
                val uUID = UUIDHelper.fromUndashed(uuid)
                val profileResult = mc.services().sessionService().fetchProfile(uUID, false)
                if (profileResult != null) {
                    mc.getSkinManager().get(profileResult.profile()).thenAccept { playerSkin ->
                        playerSkin.ifPresent { skin -> textures[uuid] = skin.body().texturePath() }
                    }
                }
                loadingTexture.remove(uuid)
            })
        }
    }

    fun getSkinTexture(account: Account): Identifier = getSkinTexture(account.uuid)

    fun getSkinTexture(user: io.github.axolotlclient.api.types.User): Identifier = getSkinTexture(user.uuid)

    fun getSkinTexture(uuid: String): Identifier {
        if (!textures.containsKey(uuid)) {
            loadTexture(uuid)
            return textures[uuid]
                ?: DefaultPlayerSkin.get(UUIDHelper.fromUndashed(uuid)).body().texturePath()
        }
        return textures[uuid]!!
    }

    companion object {
        private val Instance = Auth()

        @JvmStatic
        fun getInstance(): Auth = Instance
    }
}
