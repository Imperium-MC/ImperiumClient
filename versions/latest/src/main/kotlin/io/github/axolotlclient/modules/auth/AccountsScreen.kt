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

import io.github.axolotlclient.modules.auth.skin.SkinManagementScreen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class AccountsScreen(private val parent: Screen?) : Screen(Component.translatable("accounts")) {
    protected var accountsListWidget: AccountsListWidget? = null
    private var loginButton: Button? = null
    private var deleteButton: Button? = null
    private var refreshButton: Button? = null
    private var skinsButton: Button? = null

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        graphics.centeredText(this.font, this.title, this.width / 2, 33 / 2, -1)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (super.keyPressed(event)) {
            return true
        } else if (event.key() == 294) {
            this.refresh()
            return true
        } else if (this.accountsListWidget!!.getSelected() != null) {
            if (event.key() != 257 && event.key() != 335) {
                return this.accountsListWidget!!.keyPressed(event)
            } else {
                this.login()
                return true
            }
        }
        return false
    }

    override fun init() {
        val widget = AccountsListWidget(this, minecraft!!, width, height, 32, height - 64, 35)
        accountsListWidget = widget
        addRenderableWidget(widget)

        widget.setAccounts(Auth.getInstance().accounts)

        val login = Button.builder(Component.translatable("auth.login")) { login() }
            .bounds(this.width / 2 - 154, this.height - 52, 100, 20).build()
        loginButton = login
        addRenderableWidget(login)

        val skins = Button.builder(Component.translatable("skins.manage")) {
            minecraft!!.gui.setScreen(
                SkinManagementScreen(this, accountsListWidget!!.getSelected()!!.account)
            )
        }
            .bounds(this.width / 2 - 50, this.height - 52, 100, 20).build()
        skinsButton = skins
        addRenderableWidget(skins)

        this.addRenderableWidget(
            Button.builder(Component.translatable("auth.add")) {
                if (!Auth.getInstance().allowOfflineAccounts()) {
                    initMSAuth()
                } else {
                    minecraft!!.gui.setScreen(
                        ConfirmScreen(
                            { result: Boolean ->
                                if (!result) {
                                    minecraft!!.gui.setScreen(this)
                                    initMSAuth()
                                } else {
                                    minecraft!!.gui.setScreen(AddOfflineScreen(this))
                                }
                            },
                            Component.translatable("auth.add.choose"), Component.empty(),
                            Component.translatable("auth.add.offline"),
                            Component.translatable("auth.add.ms")
                        )
                    )
                }
            }.bounds(this.width / 2 + 4 + 50, this.height - 52, 100, 20).build()
        )

        val delete = Button.builder(Component.translatable("selectServer.delete")) { button ->
            val entry = this.accountsListWidget!!.getSelected()
            if (entry != null) {
                button.active = false
                Auth.getInstance().removeAccount(entry.account)
                refresh()
            }
        }.bounds(this.width / 2 - 50, this.height - 28, 100, 20).build()
        deleteButton = delete
        this.addRenderableWidget(delete)

        val refreshBtn = Button.builder(Component.translatable("auth.refresh")) { refreshAccount() }
            .bounds(this.width / 2 - 154, this.height - 28, 100, 20).build()
        refreshButton = refreshBtn
        this.addRenderableWidget(refreshBtn)

        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_BACK) { this.minecraft!!.gui.setScreen(this.parent) }
                .bounds(this.width / 2 + 4 + 50, this.height - 28, 100, 20).build()
        )
        updateButtonActivationStates()
    }

    private fun initMSAuth() {
        Auth.getInstance().msApi.startDeviceAuth().thenRun { minecraft!!.execute(Runnable { this.refresh() }) }
    }

    private fun refreshAccount() {
        refreshButton!!.active = false
        val entry = accountsListWidget!!.getSelected()
        if (entry != null) {
            entry.account.refresh(Auth.getInstance().msApi)
        }
    }

    private fun updateButtonActivationStates() {
        val entry = accountsListWidget!!.getSelected()
        if (minecraft!!.level == null && entry != null) {
            loginButton!!.active = entry.account.isExpired || entry.account != Auth.getInstance().getCurrent()
            refreshButton!!.active = !entry.account.isOffline
            skinsButton!!.active = !entry.account.isOffline
            deleteButton!!.active = true
        } else {
            loginButton!!.active = false
            deleteButton!!.active = false
            refreshButton!!.active = false
            skinsButton!!.active = false
        }
    }

    private fun refresh() {
        this.minecraft!!.gui.setScreen(AccountsScreen(this.parent))
    }

    private fun login() {
        loginButton!!.active = false
        val entry = accountsListWidget!!.getSelected()
        if (entry != null) {
            Auth.getInstance().login(entry.account)
        }
    }

    fun select(entry: AccountsListWidget.Entry?) {
        this.accountsListWidget!!.setSelected(entry)
        this.updateButtonActivationStates()
    }
}
