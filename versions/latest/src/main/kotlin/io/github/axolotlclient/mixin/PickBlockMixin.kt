/*
 * Copyright © 2026 moehreag <moehreag@gmail.com> & Contributors
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

import io.github.axolotlclient.modules.hud.HudManager
import io.github.axolotlclient.modules.hud.gui.hud.CoordsHud
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * coordinates/middle_click_looking_at (Alpine parity): pick-blocking a block also
 * announces the block you were looking at — its name and exact coordinates go to the
 * chat and the bare "x y z" onto the clipboard, ready to paste to a teammate. The
 * vanilla pick itself is untouched; this only adds the announcement.
 */
@Mixin(Minecraft::class)
abstract class PickBlockMixin {

    @Inject(method = ["pickBlockOrEntity"], at = [At("HEAD")])
    private fun `axolotlclient$announceLookedAtBlock`(ci: CallbackInfo) {
        val self = this as Minecraft
        val hud = HudManager.getInstance().get(CoordsHud.ID) as CoordsHud?
        if (!HudManager.getInstance().hudsEnabled() || hud == null || !hud.isEnabled()) {
            return
        }
        val hit = self.hitResult
        val level = self.level
        if (!hud.middleClickLookingAt.get() || hit !is BlockHitResult || level == null) {
            return
        }
        val pos = hit.blockPos
        val name = level.getBlockState(pos).block.name.getString()
        val coords = pos.x.toString() + " " + pos.y + " " + pos.z
        self.keyboardHandler.setClipboard(coords)
        self.gui.hud.getChat().addClientSystemMessage(Component.translatable("coordshud.looking_at", name, coords))
        self.gui.hud.getChat().addClientSystemMessage(Component.translatable("coordshud.looking_at.copied"))
    }
}
