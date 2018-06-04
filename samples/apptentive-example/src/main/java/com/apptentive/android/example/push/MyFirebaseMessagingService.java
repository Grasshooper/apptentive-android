/*
 * Copyright (c) 2017, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.example.push;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.apptentive.android.example.ExampleApplication;
import com.apptentive.android.example.R;
import com.apptentive.android.sdk.Apptentive;
import com.apptentive.android.sdk.ApptentiveLog;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		super.onMessageReceived(remoteMessage);
		Log.e(ExampleApplication.TAG, "onMessageReceived()");
		logPushBundle(remoteMessage);
		final Map<String, String> data = remoteMessage.getData();

		if (Apptentive.isApptentivePushNotification(data)) {
			Apptentive.buildPendingIntentFromPushNotification(new Apptentive.PendingIntentCallback() {
				@Override
				public void onPendingIntent(PendingIntent pendingIntent) {
					if (pendingIntent != null) {
						String title = Apptentive.getTitleFromApptentivePush(data);
						String body = Apptentive.getBodyFromApptentivePush(data);


						ApptentiveLog.e("Title: " + title);
						ApptentiveLog.e("Body: " + body);

						Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
						NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(MyFirebaseMessagingService.this)
								.setSmallIcon(R.drawable.notification)
								.setContentTitle(title)
								.setContentText(body)
								.setAutoCancel(true)
								.setSound(defaultSoundUri)
								.setContentIntent(pendingIntent);
						NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
						notificationManager.notify(0, notificationBuilder.build());
					}
				}
			}, data);
		}
	}

	private static void logPushBundle(RemoteMessage remoteMessage) {
		Map<String, String> data = remoteMessage.getData();
		Log.e(ExampleApplication.TAG, "Push Data:");
		for (String key : data.keySet()) {
			String value = data.get(key);
			Log.e(ExampleApplication.TAG, "  " + key + " : " + value);
		}
		Log.e(ExampleApplication.TAG, data.get("title") + ": " + data.get("body"));
	}
}
