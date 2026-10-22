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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.axolotlclient.AxolotlClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {

	@WrapOperation(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V", ordinal = 0))
	private <S> void armorOverlayUV(OrderedSubmitNodeCollector instance, Model<? super S> model, S s, PoseStack poseStack, RenderType renderType, int light, int overlay, int tint, @Nullable UvMapping mapping, int outline, Operation<Void> original, @Local(name = "layerTexture") Identifier layerTexture) {
		if (s instanceof LivingEntityRenderState state && AxolotlClient.config().hitColorOnArmor.get()) {
			// Note: the `whiteOverlayProgress` parameter gets retrieved from a protected method in `LivingEntityRenderer`. It is
			// only overridden by the `CreeperRenderer` in order to provide the white flashing effect. Creepers cannot render
			// Armor (there is no EquipmentLayer for this), the only related part is the charged creeper effect (which is referred to as `creeper_armor`)
			overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);
			renderType = RenderTypes.entityCutoutZOffset(layerTexture);
		}
		original.call(instance, model, s, poseStack, renderType, light, overlay, tint, mapping, outline);
	}

	/**
	 * hit_color/color_trim (Alpine parity): the second {@code submitModel} in
	 * {@code renderLayers} is the armour trim layer (the third is its glint). Applying the
	 * same forced overlay + z-offset render type the armor layer gets makes the trim flash
	 * with the configured hit colour. The glint layer is left vanilla.
	 */
	@WrapOperation(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V", ordinal = 1))
	private <S> void trimOverlayUV(OrderedSubmitNodeCollector instance, Model<? super S> model, S s, PoseStack poseStack, RenderType renderType, int light, int overlay, int tint, @Nullable UvMapping mapping, int outline, Operation<Void> original, @Local(type = net.minecraft.client.resources.palette.PalettedTextureManager.Handle.class) net.minecraft.client.resources.palette.PalettedTextureManager.Handle trimTexture) {
		if (s instanceof LivingEntityRenderState state && AxolotlClient.config().hitColorOnTrims.get()) {
			overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);
			renderType = RenderTypes.entityCutoutZOffset(trimTexture.textureLocation());
		}
		original.call(instance, model, s, poseStack, renderType, light, overlay, tint, mapping, outline);
	}

}
