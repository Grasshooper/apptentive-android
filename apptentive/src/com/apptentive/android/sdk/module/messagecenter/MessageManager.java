/*
 * Copyright (c) 2015, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.messagecenter;

import android.content.Context;
import android.content.SharedPreferences;
import com.apptentive.android.sdk.GlobalInfo;
import com.apptentive.android.sdk.Log;
import com.apptentive.android.sdk.comm.ApptentiveClient;
import com.apptentive.android.sdk.comm.ApptentiveHttpResponse;
import com.apptentive.android.sdk.model.AutomatedMessage;
import com.apptentive.android.sdk.model.FileMessage;
import com.apptentive.android.sdk.model.Message;
import com.apptentive.android.sdk.model.MessageFactory;
import com.apptentive.android.sdk.module.messagecenter.model.MessageCenterGreeting;
import com.apptentive.android.sdk.module.messagecenter.model.MessageCenterListItem;
import com.apptentive.android.sdk.storage.ApptentiveDatabase;
import com.apptentive.android.sdk.storage.MessageStore;
import com.apptentive.android.sdk.util.Constants;
import com.apptentive.android.sdk.util.Util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sky Kelsey
 */
public class MessageManager {

	private static AfterSendMessageListener afterSendMessageListener;
	private static OnNewMessagesListener internalNewMessagesListener;
	private static UnreadMessagesListener hostUnreadMessagesListener;

	/**
	 * Performs a request against the server to check for messages in the conversation since the latest message we already have.
	 * Make sure to run this off the UI Thread, as it blocks on IO.
	 *
	 * @return true if messages were returned, else false.
	 */
	public static boolean fetchAndStoreMessages(Context context) {
		if (GlobalInfo.conversationToken == null) {
			return false;
		}
		if (!Util.isNetworkConnectionPresent(context)) {
			return false;
		}

		// Fetch the messages.
		String lastId = getMessageStore(context).getLastReceivedMessageId();
		Log.d("Fetching messages after last id: " + lastId);
		List<Message> messagesToSave = fetchMessages(lastId);

		if (messagesToSave != null && messagesToSave.size() > 0) {
			Log.d("Messages retrieved.");
			// Also get the count of incoming unread messages.
			int incomingUnreadMessages = 0;
			// Mark messages from server where sender is the app user as read.
			for (Message message : messagesToSave) {
				if (message.isOutgoingMessage()) {
					message.setRead(true);
				} else {
					incomingUnreadMessages++;
				}
			}
			getMessageStore(context).addOrUpdateMessages(messagesToSave.toArray(new Message[messagesToSave.size()]));

			if (incomingUnreadMessages > 0) {
				if (internalNewMessagesListener != null) {
					internalNewMessagesListener.onMessagesUpdated();
				}
			}

			notifyHostUnreadMessagesListener(getUnreadMessageCount(context));
			return incomingUnreadMessages > 0;
		}
		return false;
	}

	public static List<MessageCenterListItem> getMessageCenterListItems(Context context) {
		List<MessageCenterListItem> messages = new ArrayList<>();
		messages.add(new MessageCenterGreeting()); // TODO: Generate a real greeting message from config.
		messages.addAll(getMessageStore(context).getAllMessages());
		return messages;
	}

	public static void sendMessage(Context context, Message message) {
		getMessageStore(context).addOrUpdateMessages(message);
		ApptentiveDatabase.getInstance(context).addPayload(message);
	}

	/**
	 * This doesn't need to be run during normal program execution. Testing only.
	 */
	public static void deleteAllMessages(Context context) {
		Log.e("Deleting all messages.");
		getMessageStore(context).deleteAllMessages();
	}

	private static List<Message> fetchMessages(String after_id) {
		Log.d("Fetching messages newer than: " + after_id);
		ApptentiveHttpResponse response = ApptentiveClient.getMessages(null, after_id, null);

		List<Message> ret = new ArrayList<>();
		if (!response.isSuccessful()) {
			return ret;
		}
		try {
			ret = parseMessagesString(response.getContent());
		} catch (JSONException e) {
			Log.e("Error parsing messages JSON.", e);
		} catch (Exception e) {
			Log.e("Unexpected error parsing messages JSON.", e);
		}
		return ret;
	}

	public static void updateMessage(Context context, Message message) {
		getMessageStore(context).updateMessage(message);
	}

	public static List<Message> parseMessagesString(String messageString) throws JSONException {
		List<Message> ret = new ArrayList<>();
		JSONObject root = new JSONObject(messageString);
		if (!root.isNull("items")) {
			JSONArray items = root.getJSONArray("items");
			for (int i = 0; i < items.length(); i++) {
				String json = items.getJSONObject(i).toString();
				Message message = MessageFactory.fromJson(json);
				// Since these came back from the server, mark them saved before updating them in the DB.
				message.setState(Message.State.saved);
				ret.add(message);
			}
		}
		return ret;
	}

	public static void onResumeSending() {
		if (afterSendMessageListener != null) {
			afterSendMessageListener.onResumeSending();
		}
	}

	public static void onPauseSending() {
		if (afterSendMessageListener != null) {
			afterSendMessageListener.onPauseSending();
		}
	}

	public static void onSentMessage(Context context, Message message, ApptentiveHttpResponse response) {
		if (response == null || !response.isSuccessful()) {
			if (message instanceof FileMessage) {
				((FileMessage) message).deleteStoredFile(context);
			}
			onPauseSending();
			return;
		}
		if (response.isSuccessful()) {
			// Don't store hidden messages once sent. Delete them.
			if (message.isHidden()) {
				if (message instanceof FileMessage) {
					((FileMessage) message).deleteStoredFile(context);
				}
				getMessageStore(context).deleteMessage(message.getNonce());
				return;
			}
			try {
				JSONObject responseJson = new JSONObject(response.getContent());
				if (message.getState() == Message.State.sending) {
					message.setState(Message.State.sent);
				}
				message.setId(responseJson.getString(Message.KEY_ID));
				message.setCreatedAt(responseJson.getDouble(Message.KEY_CREATED_AT));
			} catch (JSONException e) {
				Log.e("Error parsing sent message response.", e);
			}
			getMessageStore(context).updateMessage(message);

			if (afterSendMessageListener != null) {
				afterSendMessageListener.onMessageSent(response, message);
			}
		}
/*
		if(response.isBadPayload()) {
			// TODO: Tell the user that this message failed to send. It will be deleted by the record send worker.
		}
*/
	}

	/**
	 * This method will show either a Welcome or a No Love AutomatedMessage. If a No Love message has been shown, no other
	 * AutomatedMessage shall be shown, and no AutomatedMessage shall be shown twice.
	 *
	 * @param context The context from which this method is called.
	 * @param forced  If true, show a Welcome AutomatedMessage, else show a NoLove AutomatedMessage.
	 */
	public static void createMessageCenterAutoMessage(Context context, boolean forced) {
		SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);

		boolean shownAutoMessage = prefs.getBoolean(Constants.PREF_KEY_AUTO_MESSAGE_SHOWN_AUTO_MESSAGE, false);

		// Migrate old values if needed.
		boolean shownManual = prefs.getBoolean(Constants.PREF_KEY_AUTO_MESSAGE_SHOWN_MANUAL, false);
		boolean shownNoLove = prefs.getBoolean(Constants.PREF_KEY_AUTO_MESSAGE_SHOWN_NO_LOVE, false);
		if (!shownAutoMessage) {
			if (shownManual || shownNoLove) {
				shownAutoMessage = true;
				prefs.edit().putBoolean(Constants.PREF_KEY_AUTO_MESSAGE_SHOWN_AUTO_MESSAGE, true).commit();
			}
		}

		AutomatedMessage message;

		if (!shownAutoMessage) {
			if (forced) {
				message = AutomatedMessage.createWelcomeMessage(context);
			} else {
				message = AutomatedMessage.createNoLoveMessage(context);
			}
			if (message != null) {
				prefs.edit().putBoolean(Constants.PREF_KEY_AUTO_MESSAGE_SHOWN_AUTO_MESSAGE, true).commit();
				getMessageStore(context).addOrUpdateMessages(message);
				ApptentiveDatabase.getInstance(context).addPayload(message);
			}
		}
	}

	private static MessageStore getMessageStore(Context context) {
		return ApptentiveDatabase.getInstance(context);
	}

	public static int getUnreadMessageCount(Context context) {
		return getMessageStore(context).getUnreadMessageCount();
	}


   // Listeners

	public interface AfterSendMessageListener {
		void onMessageSent(ApptentiveHttpResponse response, Message message);
		void onPauseSending();
		void onResumeSending();
	}

	public static void setAfterSendMessageListener(AfterSendMessageListener listener) {
		afterSendMessageListener = listener;
	}

	public interface OnNewMessagesListener {
		public void onMessagesUpdated();
	}

	public static void setInternalOnMessagesUpdatedListener(OnNewMessagesListener listener) {
		internalNewMessagesListener = listener;
	}

	public static void setHostUnreadMessagesListener(UnreadMessagesListener listener) {
		hostUnreadMessagesListener = listener;
	}

	public static void notifyHostUnreadMessagesListener(int unreadMessages) {
		if (hostUnreadMessagesListener != null) {
			hostUnreadMessagesListener.onUnreadMessageCountChanged(unreadMessages);
		}
	}
}
