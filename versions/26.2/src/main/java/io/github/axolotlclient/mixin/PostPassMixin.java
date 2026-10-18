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

package io.github.axolotlclient.mixin;

import java.util.List;
import java.util.Map;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.CommandEncoder;
import io.github.axolotlclient.modules.blur.MotionBlur;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostPass.class)
public class PostPassMixin {

	@Shadow
	@Final
	private Map<String, GpuBuffer> customUniforms;

	@ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"), index = 1)
	private @GpuBuffer.Usage int addWritable(@GpuBuffer.Usage int i, @Local(name = "uniformGroup") Map.Entry<String, List<UniformValue>> entry) {
		if ("BlurConfig".equals(entry.getKey())) {
			return i | GpuBuffer.USAGE_MAP_WRITE;
		}
		return i;
	}

	@Inject(method = "lambda$addToFrame$1", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/GpuBuffer;map(ZZ)Lcom/mojang/blaze3d/buffers/GpuBufferSlice$MappedView;"))
	private void addUniforms(ResourceHandle<?> outputHandle, GpuBufferSlice shaderOrthoMatrix, Map<?, ?> targets, CallbackInfo ci, @Local(name = "commandEncoder") CommandEncoder commandEncoder) {
		if (customUniforms.containsKey("BlurConfig")) {
			var buf = customUniforms.get("BlurConfig");
			try (var mappedView = buf.map(false, true)) {
				Std140Builder std140Builder = Std140Builder.intoBuffer(mappedView.data());
				std140Builder.putFloat(MotionBlur.getBlur());
			}
		}
	}
}
