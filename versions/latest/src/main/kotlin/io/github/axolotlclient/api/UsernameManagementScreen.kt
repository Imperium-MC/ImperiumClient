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

import com.google.common.collect.ImmutableList
import io.github.axolotlclient.api.requests.AccountUsernameRequest
import io.github.axolotlclient.api.types.User
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class UsernameManagementScreen(private val parent: Screen?) : Screen(Component.translatable("api.account.usernames")) {

    private val layout: HeaderAndFooterLayout = HeaderAndFooterLayout(this)
    private var widget: UsernameListWidget? = null

    override fun init() {
        layout.setHeaderHeight(45)
        layout.setFooterHeight(55)
        layout.addTitleHeader(title, this.font)

        layout.addToFooter(Button.builder(CommonComponents.GUI_BACK) { onClose() }.build())
        widget = if (API.getInstance().isAuthenticated()) {
            UsernameListWidget(API.getInstance().self!!.previousUsernames!!)
        } else {
            UsernameListWidget(emptyList())
        }
        layout.addToContents(widget!!)

        layout.arrangeElements()

        layout.visitWidgets(this::addRenderableWidget)
    }

    override fun repositionElements() {
        layout.arrangeElements()
        widget!!.updateSize(width, layout)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    private inner class UsernameListWidget(names: List<User.OldUsername>?) :
        ContainerObjectSelectionList<UsernameManagementScreen.UsernameListWidget.UsernameListEntry>(
            this@UsernameManagementScreen.minecraft, this@UsernameManagementScreen.width,
            this@UsernameManagementScreen.layout.getContentHeight(),
            this@UsernameManagementScreen.layout.getHeaderHeight(), 20
        ) {

        init {
            names!!.forEach { n -> addEntry(UsernameListEntry(n)) }
        }

        override fun getRowWidth(): Int {
            return 310
        }

        private inner class UsernameListEntry(name: User.OldUsername) :
        ContainerObjectSelectionList.Entry<UsernameManagementScreen.UsernameListWidget.UsernameListEntry>() {

            private val visibility: Button
            private val delete: Button
            private val username: String?

            init {
                visibility = Button.builder(
                    Component.translatable("api.account.usernames.public", name.isPub)
                ) { w ->
                    name.isPub = !name.isPub
                    w.setMessage(Component.translatable("api.account.usernames.public", name.isPub))
                    AccountUsernameRequest.post(name.name!!, name.isPub)
                }.width(100).build()
                delete = Button.builder(
                    Component.translatable("api.account.usernames.delete")
                ) {
                    minecraft.gui.setScreen(
                        ConfirmScreen(
                            { b: Boolean ->
                                if (b) {
                                    AccountUsernameRequest.delete(name.name!!)
                                        .thenRun { this@UsernameListWidget.removeEntry(this@UsernameListEntry) }
                                }
                                minecraft.gui.setScreen(this@UsernameManagementScreen)
                            },
                            Component.translatable("api.account.confirm_deletion"),
                            Component.translatable("api.account.usernames.delete.desc")
                        )
                    )
                }.width(100).build()
                this.username = name.name
            }

            override fun children(): List<GuiEventListener> {
                return listOf(visibility, delete)
            }

            override fun extractContent(
                graphics: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                tickDelta: Float
            ) {
                val deleteX = this@UsernameListWidget.scrollBarX() - delete.width - 10
                delete.setPosition(deleteX, contentY - 2)
                visibility.setPosition(deleteX - visibility.width - 5, contentY - 2)
                delete.extractRenderState(graphics, mouseX, mouseY, tickDelta)
                visibility.extractRenderState(graphics, mouseX, mouseY, tickDelta)
                graphics.text(font, username, contentX, contentYMiddle - 9 / 2, -1)
            }

            override fun narratables(): List<NarratableEntry> {
                return ImmutableList.of(visibility, delete)
            }
        }
    }
}
