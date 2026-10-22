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
import com.llamalad7.mixinextras.sugar.Share
import com.llamalad7.mixinextras.sugar.ref.LocalRef
import io.github.axolotlclient.AxolotlClientCommon
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.SplashManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.io.BufferedReader
import java.io.IOException
import java.util.stream.Stream

// File-level instead of a companion @Unique static: Mixin requires non-shadow static
// fields in the mixin class to be private, which Kotlin cannot pair with @JvmField.
private val EXTRA_SPLASHES: Identifier = Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "texts/splashes.txt")

@Mixin(SplashManager::class)
abstract class SplashTextResourceSupplierMixin {

    @Inject(
        method = ["prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/List;"],
        at = [At("HEAD")]
    )
    private fun addCustomSplashesLoad(
        resourceManager: ResourceManager, profiler: ProfilerFiller,
        cir: CallbackInfoReturnable<List<Component>>,
        @Share("axolotlclient_splashes") reader: LocalRef<BufferedReader?>
    ) {
        try {
            reader.set(Minecraft.getInstance().resourceManager.openAsReader(EXTRA_SPLASHES))
        } catch (_: IOException) {
        }
    }

    @WrapOperation(
        method = ["prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/List;"],
        at = [At(value = "INVOKE", target = "Ljava/io/BufferedReader;lines()Ljava/util/stream/Stream;")]
    )
    private fun `axolotlclient$addCustomSplashes`(
        instance: BufferedReader, original: Operation<Stream<String>>,
        @Share("axolotlclient_splashes") reader: LocalRef<BufferedReader?>
    ): Stream<String> {
        val stream = original.call(instance)
        return Stream.concat(stream, original.call(reader.get()))
    }

    @Inject(
        method = ["prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/List;"],
        at = [At("RETURN")]
    )
    private fun addCustomSplashesClose(
        resourceManager: ResourceManager, profiler: ProfilerFiller,
        cir: CallbackInfoReturnable<List<Component>>,
        @Share("axolotlclient_splashes") reader: LocalRef<BufferedReader?>
    ) {
        try {
            reader.get()!!.close()
        } catch (_: IOException) {
        }
    }
}
