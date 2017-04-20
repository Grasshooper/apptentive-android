/*
 * Copyright (c) 2016, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.model;

import com.apptentive.android.sdk.ApptentiveLog;

import org.json.JSONException;
import org.json.JSONObject;

public class ConversationTokenRequest extends JSONObject {
	public ConversationTokenRequest() {
	}

	public void setDevice(DevicePayload device) {
		try {
			put(DevicePayload.KEY, device);
		} catch (JSONException e) {
			ApptentiveLog.e("Error adding %s to ConversationTokenRequest", DevicePayload.KEY);
		}
	}

	public void setSdk(SdkPayload sdk) {
		try {
			put(SdkPayload.KEY, sdk);
		} catch (JSONException e) {
			ApptentiveLog.e("Error adding %s to ConversationTokenRequest", SdkPayload.KEY);
		}
	}

	public void setPerson(PersonPayload person) {
		try {
			put(PersonPayload.KEY, person);
		} catch (JSONException e) {
			ApptentiveLog.e("Error adding %s to ConversationTokenRequest", PersonPayload.KEY);
		}
	}

	public void setAppRelease(AppReleasePayload appRelease) {
		try {
			put(AppReleasePayload.KEY, appRelease);
		} catch (JSONException e) {
			ApptentiveLog.e("Error adding %s to ConversationTokenRequest", AppReleasePayload.KEY);
		}
	}
}
