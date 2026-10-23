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

import io.github.axolotlclient.AxolotlClientCommon
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class AccountsListWidget(
    private val screen: AccountsScreen, client: Minecraft, width: Int, height: Int, top: Int, bottom: Int,
    entryHeight: Int
) : ObjectSelectionList<AccountsListWidget.Entry>(client, width, bottom - top, top, entryHeight) {

    fun setAccounts(accounts: List<Account>) {
        accounts.forEach { account -> addEntry(Entry(screen, account)) }
    }

    override fun getRowWidth(): Int = super.getRowWidth() + 85

    override fun scrollBarX(): Int = super.scrollBarX() + 30

    override fun isFocused(): Boolean = this.screen.focused === this

    class Entry(private val screen: AccountsScreen, @JvmField val account: Account) :
        ObjectSelectionList.Entry<AccountsListWidget.Entry>() {

        private val client: Minecraft = Minecraft.getInstance()

        override fun getNarration(): Component = Component.literal(account.name)

        override fun extractContent(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
            if (Auth.getInstance().getCurrent() == account) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, checkmark, getContentX() - 35, getContentY() + 1, 0f, 0f, 32, 32, 32, 32)
            } else if (account.isExpired) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, warningSign, getContentX() - 35, getContentY() + 1, 0f, 0f, 32, 32, 32, 32)
            }

            val texture = Auth.getInstance().getSkinTexture(account)
            PlayerFaceExtractor.extractRenderState(graphics, texture, getContentX() - 1, getContentY() - 1, 33, true, false, -1)

            graphics.text(client.font, account.name, getContentX() + 3 + 33, getContentY() + 1, -1)
            graphics.text(client.font, account.uuid, getContentX() + 3 + 33, getContentY() + 12, 0xFF808080.toInt())
        }

        override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
            this.screen.select(this)
            if (doubleClick) {
                if (account != Auth.getInstance().getCurrent()) {
                    screen.select(null)
                    Auth.getInstance().login(account)
                }
            }

            return false
        }

        companion object {
            private val checkmark =
                Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "textures/check.png")
            private val warningSign =
                Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "textures/warning.png")
        }
    }
}
