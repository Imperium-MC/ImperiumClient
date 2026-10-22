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

import com.llamalad7.mixinextras.sugar.Local
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.CommandEncoder
import com.mojang.blaze3d.resource.ResourceHandle
import io.github.axolotlclient.modules.blur.HudBlur
import io.github.axolotlclient.modules.blur.MotionBlur
import net.minecraft.client.renderer.PostPass
import net.minecraft.client.renderer.UniformValue
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyArg
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(PostPass::class)
abstract class PostPassMixin {

    @Shadow
    @Final
    private val customUniforms: MutableMap<String, GpuBuffer>? = null

    @ModifyArg(
        method = ["<init>"],
        at = [At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/renderpearl/api/buffers/GpuBuffer;"
        )],
        index = 1
    )
    private fun addWritable(
        i: Int,
        @Local(name = ["uniformGroup"]) entry: Map.Entry<String, List<UniformValue>>
    ): Int {
        if (entry.key == "BlurConfig" || entry.key == "HudBlurConfig") {
            return i or GpuBuffer.USAGE_MAP_WRITE
        }
        return i
    }

    @Inject(
        method = ["lambda\$addToFrame\$1"],
        at = [At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/buffers/GpuBuffer;map(ZZ)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice\$MappedView;"
        )]
    )
    private fun addUniforms(
        outputHandle: ResourceHandle<*>, shaderOrthoMatrix: GpuBufferSlice, targets: Map<*, *>,
        ci: CallbackInfo, @Local(name = ["commandEncoder"]) commandEncoder: CommandEncoder
    ) {
        val uniforms = customUniforms!!
        if (uniforms.containsKey("BlurConfig")) {
            val buf = uniforms["BlurConfig"]!!
            buf.map(false, true).use { mappedView ->
                val std140Builder = Std140Builder.intoBuffer(mappedView.data())
                std140Builder.putFloat(MotionBlur.getBlur())
            }
        }
        if (uniforms.containsKey("HudBlurConfig")) {
            val buf = uniforms["HudBlurConfig"]!!
            buf.map(false, true).use { mappedView ->
                val std140Builder = Std140Builder.intoBuffer(mappedView.data())
                std140Builder.putFloat(HudBlur.radius())
            }
        }
    }
}
