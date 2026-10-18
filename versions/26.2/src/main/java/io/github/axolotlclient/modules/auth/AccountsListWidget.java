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

package io.github.axolotlclient.modules.auth;

import java.util.List;

import io.github.axolotlclient.AxolotlClientCommon;
import lombok.Getter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class AccountsListWidget extends ObjectSelectionList<AccountsListWidget.Entry> {

	private final AccountsScreen screen;

	public AccountsListWidget(AccountsScreen screen, Minecraft client, int width, int height, int top, int bottom, int entryHeight) {
		super(client, width, bottom - top, top, entryHeight);
		this.screen = screen;
	}

	public void setAccounts(List<Account> accounts) {
		accounts.forEach(account -> addEntry(new Entry(screen, account)));
	}

	@Override
	public int getRowWidth() {
		return super.getRowWidth() + 85;
	}

	@Override
	protected int scrollBarX() {
		return super.scrollBarX() + 30;
	}

	@Override
	public boolean isFocused() {
		return this.screen.getFocused() == this;
	}

	@Environment(EnvType.CLIENT)
	public static class Entry extends ObjectSelectionList.Entry<AccountsListWidget.Entry> {

		private static final Identifier checkmark =
			Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "textures/check.png");
		private static final Identifier warningSign =
			Identifier.fromNamespaceAndPath(AxolotlClientCommon.MODID, "textures/warning.png");

		private final AccountsScreen screen;
		@Getter
		private final Account account;
		private final Minecraft client;

		public Entry(AccountsScreen screen, Account account) {
			this.screen = screen;
			this.account = account;
			this.client = Minecraft.getInstance();
		}

		@Override
		public Component getNarration() {
			return Component.literal(account.getName());
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			if (Auth.getInstance().getCurrent().equals(account)) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, checkmark, getContentX() - 35, getContentY() + 1, 0, 0, 32, 32, 32, 32);
			} else if (account.isExpired()) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, warningSign, getContentX() - 35, getContentY() + 1, 0, 0, 32, 32, 32, 32);
			}


			Identifier texture = Auth.getInstance().getSkinTexture(account);
			PlayerFaceExtractor.extractRenderState(graphics, texture, getContentX() - 1, getContentY() - 1, 33, true, false, -1);

			graphics.text(client.font, account.getName(), getContentX() + 3 + 33, getContentY() + 1, -1);
			graphics.text(client.font, account.getUuid(), getContentX() + 3 + 33, getContentY() + 12, 0xFF808080);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			this.screen.select(this);
			if (doubleClick) {
				if (!getAccount().equals(Auth.getInstance().getCurrent())) {
					screen.select(null);
					Auth.getInstance().login(account);
				}
			}

			return false;
		}
	}
}
