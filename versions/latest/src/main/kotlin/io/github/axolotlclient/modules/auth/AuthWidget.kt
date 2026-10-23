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

import io.github.axolotlclient.api.API
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB

class AuthWidget(x: Int, y: Int) : Button.Plain(
    x, y, Minecraft.getInstance().font.width(Auth.getInstance().getCurrent()!!.name) + 28, 20,
    Component.literal("    " + Auth.getInstance().getCurrent()!!.name),
    {
        Minecraft.getInstance().gui.setScreen(AccountsScreen(Minecraft.getInstance().gui.screen()))
    },
    DEFAULT_NARRATION
) {

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractContents(graphics, mouseX, mouseY, delta)
        val texture = Auth.getInstance().getSkinTexture(Auth.getInstance().getCurrent()!!)
        PlayerFaceExtractor.extractRenderState(graphics, texture, x + 1, y + 1, height - 2, true, false, ARGB.color(alpha, -1))
        if (API.getInstance().apiOptions.enabled.get()) {
            graphics.pose().pushMatrix()
            graphics.pose().translate((x + height - 1).toFloat(), (y + height - 1).toFloat())
            graphics.pose().scale(0.25f, 0.25f)
            graphics.pose().translate(-8f, -8f)
            val color = ARGB.color(alpha, API.getInstance().getIndicatorColor())
            graphics.fill(0, 4, 16, 12, color)
            graphics.fill(4, 0, 12, 16, color)
            graphics.fill(2, 2, 14, 14, color)
            graphics.pose().popMatrix()
        }
    }
}
