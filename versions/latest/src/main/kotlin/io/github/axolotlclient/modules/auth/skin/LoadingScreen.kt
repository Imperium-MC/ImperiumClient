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

package io.github.axolotlclient.modules.auth.skin

import net.minecraft.client.gui.components.LoadingDotsWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class LoadingScreen(title: Component, private val description: Component) : Screen(title) {

    override fun init() {
        val headerHeight = 33
        val titleWidget = StringWidget(
            width / 2 - font.width(title) / 2, headerHeight / 2 - font.lineHeight / 2,
            font.width(title), font.lineHeight, title, font
        )
        addRenderableWidget(titleWidget)

        val loadingPlaceholder = LoadingDotsWidget(font, description)
        loadingPlaceholder.setPosition(width / 2 - loadingPlaceholder.width / 2, height / 2 - loadingPlaceholder.height / 2)
        addRenderableWidget(loadingPlaceholder)
    }
}
