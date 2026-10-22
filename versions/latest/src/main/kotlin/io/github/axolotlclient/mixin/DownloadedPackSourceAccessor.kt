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

import net.minecraft.client.User
import net.minecraft.client.resources.server.DownloadedPackSource
import net.minecraft.client.resources.server.PackDownloader
import net.minecraft.client.resources.server.ServerPackManager
import net.minecraft.server.packs.DownloadQueue
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.gen.Invoker
import java.net.Proxy
import java.util.concurrent.Executor

@Mixin(DownloadedPackSource::class)
interface DownloadedPackSourceAccessor {

    @Invoker("createDownloader")
    fun `axolotlclient$createDownloader`(downloadQueue: DownloadQueue, executor: Executor, user: User, proxy: Proxy): PackDownloader

    // The historical typo in this accessor's name is load-bearing: Auth.java calls it as-is.
    @Accessor("downloadQueue")
    fun `axolotlcleint$getDownloadQueue`(): DownloadQueue

    @Accessor("manager")
    fun `axolotlclient$getManager`(): ServerPackManager
}
