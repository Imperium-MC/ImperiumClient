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

package io.github.axolotlclient.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Cancellable
import io.github.axolotlclient.modules.screenshotUtils.ScreenshotUtils
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.ClickEvent
import org.slf4j.Logger
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Screen::class)
abstract class ScreenMixin {

    private companion object {
        @JvmStatic
        @WrapOperation(
            method = ["defaultHandleClickEvent"],
            at = [At(
                value = "INVOKE",
                target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V",
                remap = false
            )]
        )
        private fun `axolotlclient$customClickEvents`(
            instance: Logger, s: String, o: Any, original: Operation<Void>,
            clickEvent: ClickEvent, @Cancellable ci: CallbackInfo
        ) {
            if (clickEvent is ScreenshotUtils.CustomClickEvent) {
                clickEvent.doAction()
                ci.cancel()
                return
            }
            original.call(instance, s, o)
        }
    }
}
