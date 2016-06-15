/*
 * Copyright (c) 2016, Apptentive, Inc. All Rights Reserved.
 * Please refer to the LICENSE file for the terms and conditions
 * under which redistribution and use of this file is permitted.
 */

package com.apptentive.android.sdk.module.engagement.interaction.model.survey;

public interface Question {
	int QUESTION_TYPE_SINGLELINE  = 1;
	int QUESTION_TYPE_MULTICHOICE = 2;
	int QUESTION_TYPE_MULTISELECT = 3;
	int QUESTION_TYPE_RANGE = 4;

	int getType();

	String getId();
	String getValue();
	boolean isRequired();
	String getRequiredText();
	void setRequiredText(String requiredText);
	String getInstructions();

	int getMinSelections();
	int getMaxSelections();

	enum Type {
		multichoice,
		singleline,
		multiselect,
		range
	}
}
