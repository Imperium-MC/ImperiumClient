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

import io.github.axolotlclient.modules.auth.skin.SkinManagementScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class AccountsScreen extends Screen {
	private final Screen parent;
	protected AccountsListWidget accountsListWidget;
	private Button loginButton;
	private Button deleteButton;
	private Button refreshButton;
	private Button skinsButton;

	public AccountsScreen(Screen currentScreen) {
		super(Component.translatable("accounts"));
		this.parent = currentScreen;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, this.title, this.width / 2, 33 / 2, -1);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (super.keyPressed(event)) {
			return true;
		} else if (event.key() == 294) {
			this.refresh();
			return true;
		} else if (this.accountsListWidget.getSelected() != null) {
			if (event.key() != 257 && event.key() != 335) {
				return this.accountsListWidget.keyPressed(event);
			} else {
				this.login();
				return true;
			}
		} else {
			return false;
		}
	}

	@Override
	public void init() {

		accountsListWidget = new AccountsListWidget(this, minecraft, width, height, 32, height - 64, 35);
		addRenderableWidget(accountsListWidget);

		accountsListWidget.setAccounts(Auth.getInstance().getAccounts());

		addRenderableWidget(loginButton = Button.builder(Component.translatable("auth.login"), buttonWidget -> login())
			.bounds(this.width / 2 - 154, this.height - 52, 100, 20).build());

		addRenderableWidget(skinsButton = Button.builder(Component.translatable("skins.manage"),
				btn -> minecraft.gui.setScreen(new SkinManagementScreen(
					this, accountsListWidget.getSelected().getAccount())))
			.bounds(this.width / 2 - 50, this.height - 52, 100, 20).build());

		this.addRenderableWidget(Button.builder(Component.translatable("auth.add"), button -> {
			if (!Auth.getInstance().allowOfflineAccounts()) {
				initMSAuth();
			} else {
				minecraft.gui.setScreen(new ConfirmScreen(result -> {
					if (!result) {
						minecraft.gui.setScreen(this);
						initMSAuth();
					} else {
						minecraft.gui.setScreen(new AddOfflineScreen(this));
					}
				}, Component.translatable("auth.add.choose"), Component.empty(),
					Component.translatable("auth.add.offline"),
					Component.translatable("auth.add.ms")
				));
			}
		}).bounds(this.width / 2 + 4 + 50, this.height - 52, 100, 20).build());

		this.deleteButton =
			this.addRenderableWidget(Button.builder(Component.translatable("selectServer.delete"), button -> {
				AccountsListWidget.Entry entry = this.accountsListWidget.getSelected();
				if (entry != null) {
					button.active = false;
					Auth.getInstance().removeAccount(entry.getAccount());
					refresh();
				}
			}).bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());


		this.addRenderableWidget(refreshButton =
			Button.builder(Component.translatable("auth.refresh"), button -> refreshAccount())
				.bounds(this.width / 2 - 154, this.height - 28, 100, 20).build());

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.gui.setScreen(this.parent))
			.bounds(this.width / 2 + 4 + 50, this.height - 28, 100, 20).build());
		updateButtonActivationStates();
	}

	private void initMSAuth() {
		Auth.getInstance().getMsApi().startDeviceAuth().thenRun(() -> minecraft.execute(this::refresh));
	}

	private void refreshAccount() {
		refreshButton.active = false;
		AccountsListWidget.Entry entry = accountsListWidget.getSelected();
		if (entry != null) {
			entry.getAccount().refresh(Auth.getInstance().getMsApi());
		}
	}

	private void updateButtonActivationStates() {
		AccountsListWidget.Entry entry = accountsListWidget.getSelected();
		if (minecraft.level == null && entry != null) {
			loginButton.active = entry.getAccount().isExpired() || !entry.getAccount().equals(Auth.getInstance().getCurrent());
			refreshButton.active = skinsButton.active = !entry.getAccount().isOffline();
			deleteButton.active = true;
		} else {
			loginButton.active = deleteButton.active = refreshButton.active = skinsButton.active = false;
		}
	}

	private void refresh() {
		this.minecraft.gui.setScreen(new AccountsScreen(this.parent));
	}

	private void login() {
		loginButton.active = false;
		AccountsListWidget.Entry entry = accountsListWidget.getSelected();
		if (entry != null) {
			Auth.getInstance().login(entry.getAccount());
		}
	}

	public void select(AccountsListWidget.Entry entry) {
		this.accountsListWidget.setSelected(entry);
		this.updateButtonActivationStates();
	}
}
