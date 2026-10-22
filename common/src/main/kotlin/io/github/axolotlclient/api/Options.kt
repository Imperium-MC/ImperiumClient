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

import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import java.util.function.Supplier

import io.github.axolotlclient.AxolotlClientCommon
import io.github.axolotlclient.AxolotlClientConfig.api.manager.ConfigManager
import io.github.axolotlclient.AxolotlClientConfig.api.options.OptionCategory
import io.github.axolotlclient.AxolotlClientConfig.impl.managers.JsonConfigManager
import io.github.axolotlclient.AxolotlClientConfig.impl.options.BooleanOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.EnumOption
import io.github.axolotlclient.AxolotlClientConfig.impl.options.StringOption
import io.github.axolotlclient.api.requests.AccountSettingsRequest
import io.github.axolotlclient.api.types.AccountSettings
import io.github.axolotlclient.api.types.PkSystem
import io.github.axolotlclient.modules.Module
import io.github.axolotlclient.util.ThreadExecuter

abstract class Options : Module {

    // needs a real (non-private) backing field: the Java APIOptions subclass assigns it directly,
    // and Kotlin `protected` is not package-visible (the API class reads it from the same package)
    @JvmField
    var openPrivacyNoteScreen: Supplier<CompletableFuture<Boolean>> =
        Supplier { CompletableFuture.completedFuture(false) }

    @JvmField
    var privacyAccepted: EnumOption<PrivacyPolicyState> = EnumOption(
        "api.privacy_policy_accepted", PrivacyPolicyState::class.java, PrivacyPolicyState.UNSET
    ) { state: PrivacyPolicyState ->
        if (!state.isAccepted() && API.getInstance().isAuthenticated()) {
            API.getInstance().shutdown()
        }
        AxolotlClientCommon.getInstance().saveConfig()
    }

    @JvmField
    val sendStatusUpdates = BooleanOption("api.send_status_updates", true)

    @JvmField
    val statusUpdateNotifs = BooleanOption("statusUpdateNotifs", true)

    @JvmField
    val friendRequestsEnabled = BooleanOption("friendRequestsEnabled", true)

    @JvmField
    val channelInvitesEnabled = BooleanOption("api.channels.invites.enabled", false)

    @JvmField
    val detailedLogging = BooleanOption("detailedLogging", false)

    @JvmField
    val enabled = BooleanOption("enabled", false) { value: Boolean ->
        if (value) {
            if (!privacyAccepted.get().isAccepted()) {
                openPrivacyNoteScreen.get().thenAccept { v: Boolean ->
                    if (v) ThreadExecuter.scheduleTask(Runnable { API.getInstance().restart() })
                }
            } else {
                ThreadExecuter.scheduleTask(Runnable { API.getInstance().restart() })
            }
        } else {
            ThreadExecuter.scheduleTask(Runnable { API.getInstance().shutdown() })
        }
    }

    @JvmField
    val updateNotifications = BooleanOption("api.update_notifications", false)

    @JvmField
    val displayNotes = BooleanOption("api.display_notes", true)

    @JvmField
    val addShortcutButtons = BooleanOption("api.add_shortcut_buttons", true)

    @JvmField
    val allowFriendsServerJoin = BooleanOption("api.allow_friends_server_join", false)

    private val pkConfig = OptionCategory.create("pluralkit_sensitive")
    private val pkConfigManager = JsonConfigManager(
        AxolotlClientCommon.resolveConfigFile("pluralkit_sensitive.json"), pkConfig
    )

    @JvmField
    val pkToken = StringOption("api.pk_token", "") { s: String ->
        PkSystem.fromToken(s).thenAccept { sys: PkSystem? ->
            if (sys != null) {
                API.getInstance().self!!.system = sys
            }
        }
        pkConfigManager.save()
    }

    @JvmField
    val autoproxy = BooleanOption("api.pk_autoproxy", false)

    @JvmField
    var autoproxyMode = EnumOption("api.pk_proxymode", PkSystem.ProxyMode::class.java, PkSystem.ProxyMode.PROXY_OFF)

    @JvmField
    val autoproxyMember = StringOption("api.pk_autoproxy_member", "") { s: String ->
        if (API.getInstance().self != null && API.getInstance().self!!.system != null) {
            API.getInstance().self!!.system!!.updateAutoproxyMember(s)
        }
    }

    // real field (was package-private in Java): the Java APIOptions subclass reads and wraps it directly
    @JvmField
    var settingUpdated: Consumer<Boolean> = Consumer {}

    @JvmField
    val showRegistered = BooleanOption("api.account.settings.show_registered", true, settingUpdated::accept)

    @JvmField
    val retainUsernames = BooleanOption("api.account.settings.retain_usernames", true, settingUpdated::accept)

    @JvmField
    val showLastOnline = BooleanOption("api.account.settings.show_last_online", true, settingUpdated::accept)

    @JvmField
    val showActivity = BooleanOption("api.account.settings.show_activity", true, settingUpdated::accept)

    @JvmField
    val allowFriendsImageAccess =
        BooleanOption("api.account.settings.allow_friends_image_access", true, settingUpdated::accept)

    // protected real fields: the Java APIOptions subclass touches these directly
    @JvmField
    protected val category = OptionCategory.create("api.category")

    @JvmField
    protected val badgeOptions = OptionCategory.create("api.badge_options")

    @JvmField
    protected val pluralkit = OptionCategory.create("api.pluralkit")

    @JvmField
    protected val account = OptionCategory.create("api.account")

    override fun init() {
        settingUpdated = Consumer { b: Boolean ->
            AccountSettingsRequest.update(
                AccountSettings(
                    showRegistered.get(), retainUsernames.get(), showLastOnline.get(),
                    showActivity.get(), allowFriendsImageAccess.get()
                )
            )
        }
        pkToken.setMaxLength(65)
        pkConfig.add(pkToken)
        pkConfigManager.load()
        pluralkit.add(pkToken, false)
        pluralkit.add(autoproxy, autoproxyMode, autoproxyMember)
        account.add(showRegistered, retainUsernames, showLastOnline, showActivity, allowFriendsImageAccess)
        val globalConfig = AxolotlClientCommon.getInstance().getConfig()
        badgeOptions.add(globalConfig.showBadges, globalConfig.customBadge, globalConfig.badgeText, globalConfig.tabBadgeMode)
        category.add(pluralkit, badgeOptions)
        category.add(account, false)
        category.add(
            enabled, privacyAccepted, friendRequestsEnabled, sendStatusUpdates, statusUpdateNotifs,
            channelInvitesEnabled, detailedLogging, updateNotifications, displayNotes, addShortcutButtons,
            allowFriendsServerJoin
        )
    }

    enum class PrivacyPolicyState {
        UNSET,
        ACCEPTED {
            override fun isAccepted(): Boolean {
                return true
            }
        },
        DENIED;

        open fun isAccepted(): Boolean {
            return false
        }

        override fun toString(): String {
            return "privacy_policy_state." + name.lowercase(Locale.ROOT)
        }
    }
}
