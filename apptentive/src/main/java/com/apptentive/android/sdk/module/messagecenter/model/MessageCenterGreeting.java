/*
 * Copyright (c) 2016, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.messagecenter.model;

public class MessageCenterGreeting implements MessageCenterListItem {
	public final String title;
	public final String body;
	public final String avatar;

	public MessageCenterGreeting(String title, String body, String avatar) {
		this.title = title;
		this.body = body;
		this.avatar = avatar;
	}

	@Override
	public int getListItemType() {
		return GREETING;
	}
}
