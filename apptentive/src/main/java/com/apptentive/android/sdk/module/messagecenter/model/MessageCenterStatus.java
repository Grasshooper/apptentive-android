/*
 * Copyright (c) 2016, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.messagecenter.model;

import org.json.JSONObject;

public class MessageCenterStatus extends JSONObject implements MessageCenterListItem {

	public final String body;
	public final Integer icon;

	public MessageCenterStatus(String body, Integer icon) {
		this.body = body;
		this.icon = icon;
	}

	@Override
	public int getListItemType() {
		return STATUS;
	}
}
