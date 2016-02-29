/*
 * Copyright (c) 2015, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;

import com.apptentive.android.sdk.ApptentiveInternal;
import com.apptentive.android.sdk.Log;
import com.apptentive.android.sdk.model.CustomData;
import com.apptentive.android.sdk.model.Device;
import com.apptentive.android.sdk.util.Constants;
import com.apptentive.android.sdk.util.JsonDiffer;
import org.json.JSONException;

import java.util.Locale;
import java.util.TimeZone;

/**
 * A helper class with static methods for getting, storing, retrieving, and diffing information about the current device.
 *
 * @author Sky Kelsey
 */
public class DeviceManager {

	/**
	 * If any device setting has changed, return only the changed fields in a new Device object. If a field's value was
	 * cleared, set that value to null in the Device. The first time this is called, all Device will be returned.
	 *
	 * @return A Device containing diff data which, when added to the last sent Device, yields the new Device.
	 */
	public static Device storeDeviceAndReturnDiff(Context context) {

		Device stored = getStoredDevice(context);

		Device current = generateNewDevice(context);
		CustomData customData = loadCustomDeviceData(context);
		current.setCustomData(customData);
		CustomData integrationConfig = loadIntegrationConfig(context);
		current.setIntegrationConfig(integrationConfig);

		Object diff = JsonDiffer.getDiff(stored, current);
		if (diff != null) {
			try {
				storeDevice(context, current);
				return new Device(diff.toString());
			} catch (JSONException e) {
				Log.e("Error casting to Device.", e);
			}
		}
		return null;
	}

	/**
	 * Provided so we can be sure that the device we send during conversation creation is 100% accurate. Since we do not
	 * queue this device up in the payload queue, it could otherwise be lost.
	 */
	public static Device storeDeviceAndReturnIt(Context context) {
		Device current = generateNewDevice(context);
		CustomData customData = loadCustomDeviceData(context);
		current.setCustomData(customData);
		CustomData integrationConfig = loadIntegrationConfig(context);
		current.setIntegrationConfig(integrationConfig);
		storeDevice(context, current);
		return current;
	}

	public static CustomData loadCustomDeviceData(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		String deviceDataString = prefs.getString(Constants.PREF_KEY_DEVICE_DATA, null);
		try {
			return new CustomData(deviceDataString);
		} catch (Exception e) {
			// Ignore
		}
		try {
			return new CustomData();
		} catch (JSONException e) {
			// Ignore
		}
		return null; // This should never happen.
	}

	public static void storeCustomDeviceData(Context context, CustomData deviceData) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		String deviceDataString = deviceData.toString();
		prefs.edit().putString(Constants.PREF_KEY_DEVICE_DATA, deviceDataString).apply();
	}

	public static CustomData loadIntegrationConfig(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		String integrationConfigString = prefs.getString(Constants.PREF_KEY_DEVICE_INTEGRATION_CONFIG, null);
		try {
			return new CustomData(integrationConfigString);
		} catch (Exception e) {
			// Ignore
		}
		try {
			return new CustomData();
		} catch (JSONException e) {
			// Ignore
		}
		return null; // This should never happen.
	}

	public static void storeIntegrationConfig(Context context, CustomData integrationConfig) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		String integrationConfigString = integrationConfig.toString();
		prefs.edit().putString(Constants.PREF_KEY_DEVICE_INTEGRATION_CONFIG, integrationConfigString).apply();
	}

	private static Device generateNewDevice(Context context) {
		Device device = new Device();

		// First, get all the information we can load from static resources.
		device.setOsName("Android");
		device.setOsVersion(Build.VERSION.RELEASE);
		device.setOsBuild(Build.VERSION.INCREMENTAL);
		device.setOsApiLevel(String.valueOf(Build.VERSION.SDK_INT));
		device.setManufacturer(Build.MANUFACTURER);
		device.setModel(Build.MODEL);
		device.setBoard(Build.BOARD);
		device.setProduct(Build.PRODUCT);
		device.setBrand(Build.BRAND);
		device.setCpu(Build.CPU_ABI);
		device.setDevice(Build.DEVICE);
		device.setUuid(ApptentiveInternal.getAndroidId(context));
		device.setBuildType(Build.TYPE);
		device.setBuildId(Build.ID);

		// Second, set the stuff that requires querying system services.
		TelephonyManager tm = ((TelephonyManager) (context.getSystemService(Context.TELEPHONY_SERVICE)));
		device.setCarrier(tm.getSimOperatorName());
		device.setCurrentCarrier(tm.getNetworkOperatorName());
		device.setNetworkType(Constants.networkTypeAsString(tm.getNetworkType()));

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			try {
				device.setBootloaderVersion((String) Build.class.getField("BOOTLOADER").get(null));
			} catch (Exception e) {
				//
			}
			device.setRadioVersion(Build.getRadioVersion());
		}

		device.setLocaleCountryCode(Locale.getDefault().getCountry());
		device.setLocaleLanguageCode(Locale.getDefault().getLanguage());
		device.setLocaleRaw(Locale.getDefault().toString());
		device.setUtcOffset(String.valueOf((TimeZone.getDefault().getRawOffset() / 1000)));
		return device;
	}

	public static Device getStoredDevice(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		String deviceString = prefs.getString(Constants.PREF_KEY_DEVICE, null);
		try {
			return new Device(deviceString);
		} catch (Exception e) {
			// Ignore
		}
		return null;
	}

	private static void storeDevice(Context context, Device device) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
		prefs.edit().putString(Constants.PREF_KEY_DEVICE, device.toString()).apply();
	}

	public static void onSentDeviceInfo(Context appContext) {
	}
}
