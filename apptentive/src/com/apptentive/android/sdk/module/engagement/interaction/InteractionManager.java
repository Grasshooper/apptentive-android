/*
 * Copyright (c) 2015, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.engagement.interaction;

import android.content.Context;
import android.content.SharedPreferences;

import com.apptentive.android.sdk.GlobalInfo;
import com.apptentive.android.sdk.ApptentiveLog;
import com.apptentive.android.sdk.comm.ApptentiveClient;
import com.apptentive.android.sdk.comm.ApptentiveHttpResponse;
import com.apptentive.android.sdk.module.engagement.interaction.model.Interactions;
import com.apptentive.android.sdk.module.engagement.interaction.model.Interaction;
import com.apptentive.android.sdk.module.engagement.interaction.model.InteractionsPayload;
import com.apptentive.android.sdk.module.engagement.interaction.model.Targets;
import com.apptentive.android.sdk.module.metric.MetricModule;
import com.apptentive.android.sdk.util.Constants;
import com.apptentive.android.sdk.util.Util;
import org.json.JSONException;

/**
 * @author Sky Kelsey
 */
public class InteractionManager {

	private static Interactions interactions;
	private static Targets targets;
	private static Boolean pollForInteractions;

	public static Interactions getInteractions(Context context) {
		if (interactions == null) {
			interactions = loadInteractions(context);
		}
		return interactions;
	}

	public static Targets getTargets(Context context) {
		if (targets == null) {
			targets = loadTargets(context);
		}
		return targets;
	}

	public static Interaction getApplicableInteraction(Context context, String eventLabel) {

		Targets targets = getTargets(context);

		if (targets != null) {
			String interactionId = targets.getApplicableInteraction(context, eventLabel);
			if (interactionId != null) {
				Interactions interactions = getInteractions(context);
				return interactions.getInteraction(interactionId);
			}
		}
		return null;
	}

	public static void asyncFetchAndStoreInteractions(final Context context) {

		if (!isPollForInteractions(context)) {
			ApptentiveLog.v("Interaction polling is disabled.");
			return;
		}

		boolean force = GlobalInfo.isAppDebuggable;

		if (force || hasCacheExpired(context)) {
			ApptentiveLog.i("Fetching new Interactions.");
			Thread thread = new Thread() {
				public void run() {
					fetchAndStoreInteractions(context);
				}
			};
			Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {
				@Override
				public void uncaughtException(Thread thread, Throwable throwable) {
					ApptentiveLog.w("UncaughtException in InteractionManager.", throwable);
					MetricModule.sendError(context.getApplicationContext(), throwable, null, null);
				}
			};
			thread.setUncaughtExceptionHandler(handler);
			thread.setName("Apptentive-FetchInteractions");
			thread.start();
		} else {
			ApptentiveLog.v("Using cached Interactions.");
		}
	}

	private static void fetchAndStoreInteractions(Context appContext) {
		ApptentiveHttpResponse response = ApptentiveClient.getInteractions(appContext);

		// We weren't able to connect to the internet.
		SharedPreferences prefs = appContext.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		if (response.isException()) {
			prefs.edit().putBoolean(Constants.PREF_KEY_MESSAGE_CENTER_SERVER_ERROR_LAST_ATTEMPT, false).apply();
			return;
		}
		// We got a server error.
		if (!response.isSuccessful()) {
			prefs.edit().putBoolean(Constants.PREF_KEY_MESSAGE_CENTER_SERVER_ERROR_LAST_ATTEMPT, true).apply();
			return;
		}

		String interactionsPayloadString = response.getContent();

		// Store new integration cache expiration.
		String cacheControl = response.getHeaders().get("Cache-Control");
		Integer cacheSeconds = Util.parseCacheControlHeader(cacheControl);
		if (cacheSeconds == null) {
			cacheSeconds = Constants.CONFIG_DEFAULT_INTERACTION_CACHE_EXPIRATION_DURATION_SECONDS;
		}
		updateCacheExpiration(appContext, cacheSeconds);
		storeInteractionsPayloadString(appContext, interactionsPayloadString);
	}

	/**
	 * Made public for testing. There is no other reason to use this method directly.
	 */
	public static void storeInteractionsPayloadString(Context context, String interactionsPayloadString) {
		try {
			InteractionsPayload payload = new InteractionsPayload(interactionsPayloadString);
			Interactions interactions = payload.getInteractions();
			Targets targets = payload.getTargets();
			if (interactions != null && targets != null) {
				InteractionManager.interactions = interactions;
				InteractionManager.targets = targets;
				saveInteractions(context);
				saveTargets(context);
			} else {
				ApptentiveLog.e("Unable to save payloads.");
			}
		} catch (JSONException e) {
			ApptentiveLog.w("Invalid InteractionsPayload received.");
		}
	}

	public static void clear(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		prefs.edit().remove(Constants.PREF_KEY_INTERACTIONS).commit();
		prefs.edit().remove(Constants.PREF_KEY_TARGETS).commit();
		interactions = null;
		targets = null;
	}

	private static void saveInteractions(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		prefs.edit().putString(Constants.PREF_KEY_INTERACTIONS, interactions.toString()).commit();
	}

	private static Interactions loadInteractions(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		String interactionsString = prefs.getString(Constants.PREF_KEY_INTERACTIONS, null);
		if (interactionsString != null) {
			try {
				return new Interactions(interactionsString);
			} catch (JSONException e) {
				ApptentiveLog.w("Exception creating Interactions object.", e);
			}
		}
		return null;
	}

	private static void saveTargets(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		prefs.edit().putString(Constants.PREF_KEY_TARGETS, targets.toString()).commit();
	}

	private static Targets loadTargets(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		String targetsString = prefs.getString(Constants.PREF_KEY_TARGETS, null);
		if (targetsString != null) {
			try {
				return new Targets(targetsString);
			} catch (JSONException e) {
				ApptentiveLog.w("Exception creating Targets object.", e);
			}
		}
		return null;
	}

	private static boolean hasCacheExpired(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		long expiration = prefs.getLong(Constants.PREF_KEY_INTERACTIONS_PAYLOAD_CACHE_EXPIRATION, 0);
		return expiration < System.currentTimeMillis();
	}

	public static void updateCacheExpiration(Context context, long duration) {
		long expiration = System.currentTimeMillis() + (duration * 1000);
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		prefs.edit().putLong(Constants.PREF_KEY_INTERACTIONS_PAYLOAD_CACHE_EXPIRATION, expiration).commit();
	}

	public static boolean isPollForInteractions(Context context) {
		if (pollForInteractions == null) {
			SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
			pollForInteractions = prefs.getBoolean(Constants.PREF_KEY_POLL_FOR_INTERACTIONS, true);
		}
		return pollForInteractions;
	}

	public static void setPollForInteractions(Context context, boolean pollForInteractions) {
		InteractionManager.pollForInteractions = pollForInteractions;
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		prefs.edit().putBoolean(Constants.PREF_KEY_POLL_FOR_INTERACTIONS, pollForInteractions).commit();
	}
}
