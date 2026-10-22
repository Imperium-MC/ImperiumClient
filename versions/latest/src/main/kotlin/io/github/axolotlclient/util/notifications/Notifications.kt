/*
 * Copyright © 2025 moehreag <moehreag@gmail.com> & Contributors
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

package io.github.axolotlclient.util.notifications

import io.github.axolotlclient.util.notifications.NotificationProvider
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.network.chat.Component

class Notifications : NotificationProvider {

    override fun addStatus(titleKey: String, descKey: String, vararg args: Any?) {
        // Component.translatable's vararg is @NullMarked non-null; pass the raw array through
        // unchanged (including any nulls, as the Java version did).
        @Suppress("UNCHECKED_CAST")
        val arguments = args as Array<Any>
        addStatus(Component.translatable(titleKey, *arguments), Component.translatable(descKey, *arguments))
    }

    fun addStatus(title: Component, description: Component) {
        Minecraft.getInstance().gui.toastManager().addToast(
            AxolotlClientToast.multiline(Minecraft.getInstance(), title, description)
        )
    }

    fun addStatus(toast: Toast) {
        Minecraft.getInstance().gui.toastManager().addToast(toast)
    }

    companion object {
        private val Instance = Notifications()

        @JvmStatic
        fun getInstance(): Notifications = Instance
    }
}
