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

import io.github.axolotlclient.util.OSUtil
import java.net.URI
import java.util.concurrent.CompletableFuture
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.layouts.FrameLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class PrivacyNoticeScreen(
    private val parent: Screen?,
    private val accepted: CompletableFuture<Boolean>
) : Screen(Component.translatable("api.privacyNotice")) {

    override fun init() {
        val frame = FrameLayout(width, height)
        val layout = frame.addChild(LinearLayout.vertical()).spacing(20)
        layout.defaultCellSetting().alignHorizontallyCenter()
        layout.addChild(StringWidget(title, font))
        layout.addChild(MultiLineTextWidget(Component.translatable("api.privacyNotice.description"), font))
            .setCentered(true).setMaxWidth(width - 50)
        val buttons = layout.addChild(LinearLayout.horizontal()).spacing(4)
        buttons.addChild(
            Button.builder(Component.translatable("api.privacyNotice.accept")) {
                minecraft.gui.setScreen(parent)
                APIOptions.getInstance().privacyAccepted.set(Options.PrivacyPolicyState.ACCEPTED)
                accepted.complete(true)
            }.width(100).build()
        )
        buttons.addChild(
            Button.builder(Component.translatable("api.privacyNotice.openPolicy")) {
                OSUtil.getOS().open(TERMS_URI)
            }.width(100).build()
        )
        buttons.addChild(
            Button.builder(Component.translatable("api.privacyNotice.deny")) {
                minecraft.gui.setScreen(parent)
                APIOptions.getInstance().enabled.set(false)
                APIOptions.getInstance().privacyAccepted.set(Options.PrivacyPolicyState.DENIED)
                accepted.complete(false)
            }.width(100).build()
        )
        frame.arrangeElements()
        frame.visitWidgets(this::addRenderableWidget)
    }

    companion object {
        private val TERMS_URI: URI = URI.create(Constants.TERMS)
    }
}
