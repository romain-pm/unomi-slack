/*

        ~ Licensed to the Apache Software Foundation (ASF) under one or more
        ~ contributor license agreements.  See the NOTICE file distributed with
        ~ this work for additional information regarding copyright ownership.
        ~ The ASF licenses this file to You under the Apache License, Version 2.0
        ~ (the "License"); you may not use this file except in compliance with
        ~ the License.  You may obtain a copy of the License at
        ~
        ~      http://www.apache.org/licenses/LICENSE-2.0
        ~
        ~ Unless required by applicable law or agreed to in writing, software
        ~ distributed under the License is distributed on an "AS IS" BASIS,
        ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        ~ See the License for the specific language governing permissions and
        ~ limitations under the License.
*/

package org.apache.unomi.slack.actions;

import java.io.IOException;
import java.util.Map.Entry;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.actions.ActionPostExecutor;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author rgauthier
 */
public class SlackMessageAction implements ActionExecutor {
	private static Logger logger = LoggerFactory.getLogger(SlackMessageAction.class);

	private String slackHookMessageServiceUrl;
	private String slackHookMessageServiceWorkspaceId;
	private String slackHookMessageServiceToken;

	private String slackMessageTitle;
	private String slackMessagePretext;
	private String slackMessageThumbUrl;
	private String slackMessageColor;
	private String slackMessageText;
	private String slackMessageFallback;

	private String unomiProfileUrl;
	private String unomiSystemTagsExclude;

	private ProfileService profileService;

	/**
	 * The execute method, only managing logging and calling internalExecute
	 */
	@Override
	public int execute(Action action, Event event) {

		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Execute action {} for event id {} and type {}", SlackMessageAction.class.getName(),
						event.getItemId(), event.getEventType());

				logger.debug("Action parameters {} ", action.getParameterValues().toString());
			}

			int eventServiceStatus = internalExecute(action, event);

			if (logger.isDebugEnabled()) {
				logger.debug("Action {} is done for event id {} and type {}", SlackMessageAction.class.getName(),
						event.getItemId(), event.getEventType());
			}

			return eventServiceStatus;
		} catch (Exception e) {
			logger.error("Error when executing action", e);
			logger.error("action {}", action);
			logger.error("event {}", event);

			return EventService.NO_CHANGE;
		}
	}

	/**
	 * Method articulating the business logic
	 * 
	 * @param action
	 * @param event
	 * @return
	 * @throws Exception
	 */
	protected int internalExecute(Action action, Event event) throws Exception {

		String slackUrl = slackHookMessageServiceUrl + "/" + slackHookMessageServiceWorkspaceId + "/"
				+ slackHookMessageServiceToken;

		logger.debug("slackUrl: {} ", slackUrl);

		String jsonForSlack = buildJsonForSlackUsingAttachments(action, event);

		executePostRequestToSlack(slackUrl, jsonForSlack);

		return EventService.NO_CHANGE;
	}

	/**
	 * Loosy method to remove that log some stuff
	 * 
	 * @param action
	 * @param event
	 */
	private void supperLogger(Action action, Event event) {

		logger.warn("Event type: {} - item type: {} - version: {}", event.getEventType(), event.getItemType(),
				event.getVersion());

		for (Parameter actionParameter : action.getActionType().getParameters()) {
			logger.warn("Action - actionParameter id: {} - type {}", actionParameter.getId(),
					actionParameter.getType());
		}

		logger.warn("Action type name: {} ", action.getActionType().getMetadata().getName());

		for (Entry eventProperty : event.getProperties().entrySet()) {
			logger.warn("Event property {} - {}", eventProperty.getKey(), eventProperty.getValue());
		}
		
		if (event.getSource() != null) {
			Event source = (Event) event.getSource();
			logger.warn("Source - Item id:{} ", source.getItemId());
			logger.warn("Source - Item type:{} ", source.getItemType());
			logger.warn("Source - Version:{} ", source.getVersion());
			logger.warn("Source - Class:{} ", source.getClass());
			
			for (Entry sourceEventProperty : source.getProperties().entrySet()) {
				logger.warn("Source Event property {} - {}", sourceEventProperty.getKey(), sourceEventProperty.getValue());
			}
			
			if (event.getSource() instanceof Goal) {
				Goal goal = (Goal) event.getSource();
				logger.warn("Source - goal id {} - name {}", goal.getMetadata().getId(), goal.getMetadata().getName());
			}
		}

		if (event.getTarget() != null) {
			logger.warn("Target - Item id:{} ", event.getTarget().getItemId());
			logger.warn("Target - Item type:{} ", event.getTarget().getItemType());
			logger.warn("Target - Version:{} ", event.getTarget().getVersion());
			logger.warn("Target - Class:{} ", event.getTarget().getClass());

			if (event.getTarget() instanceof Goal) {
				Goal goal = (Goal) event.getTarget();
				
				logger.warn("Target - goal id {} - name {} - description {}", goal.getMetadata().getId(), goal.getMetadata().getName(), goal.getMetadata().getDescription());
				logger.warn("Target - goal item type " , goal.getItemType());
			}
		}

		for (ActionPostExecutor act : event.getActionPostExecutors()) {
			logger.warn("act class::{} ", act.getClass());
		}

	}

	/**
	 * 
	 * @param pt
	 * @param tagsToExclude
	 * @return
	 */
	private boolean excludeProperty(PropertyType pt, String[] tagsToExclude) {

		// Exclude properties that don't have any tag
		if (pt == null || pt.getMetadata() == null || pt.getMetadata().getName() == null) {
			return true;
		}

		// Exclude properties that don't have any system tags
		if (pt.getMetadata().getSystemTags() == null || pt.getMetadata().getSystemTags().size() == 0) {
			return true;
		}

		// Exclude properties depending in the tag
		for (String tagToExclude : tagsToExclude) {
			if (pt.getMetadata().getSystemTags().contains(tagToExclude)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Building the json that will be sent to slack
	 * 
	 * @param event
	 * @return
	 * @throws JSONException
	 */
	private String buildJsonForSlackUsingAttachments(Action action, Event event) throws JSONException {

		supperLogger(action, event);

		JSONObject slackAttachment1 = getJsonFromCfgValues(event);

		JSONArray fields = new JSONArray();

		String[] tagsToExclude = unomiSystemTagsExclude.split(",");

		fields = getJsonFromProfileProperties(event, fields, tagsToExclude);

		fields.put(getJsonFromSessionProperty("Country", "sessionCountryName", event));
		fields.put(getJsonFromSessionProperty("City", "sessionCity", event));
		fields.put(getJsonFromSessionProperty("Device category", "deviceCategory", event));

		slackAttachment1.put("fields", fields);

		JSONArray attachments = new JSONArray();
		attachments.put(slackAttachment1);

		JSONObject jsonForSlack = new JSONObject();
		jsonForSlack.put("attachments", attachments);

		String slackString = jsonForSlack.toString();

		logger.warn("text sent to slack: " + slackString + "\n\n");

		return slackString;
	}

	/**
	 * 
	 * @param event
	 * @param attachment1
	 * @return
	 * @throws JSONException
	 */
	private JSONObject getJsonFromCfgValues(Event event) throws JSONException {

		String techInfo = "";
		String processedSlackMessageTitle = slackMessageTitle;
		if (event.getTarget() instanceof Goal) {
			Goal goal = (Goal) event.getTarget();
			processedSlackMessageTitle = slackMessageTitle.replace("{goalName}", goal.getMetadata().getName());
			techInfo = event.getScope() + " - Goal id: " + goal.getMetadata().getId();
		} else {
			processedSlackMessageTitle = "We actually don't know that much on what trigger this action";
		}

		JSONObject attachment1 = new JSONObject();
		attachment1.put("title", processedSlackMessageTitle);

		String processedSlacMessagePretext = slackMessagePretext.replace("{scope}", event.getScope());
		attachment1.put("pretext", processedSlacMessagePretext);
		attachment1.put("thumb_url", slackMessageThumbUrl);
		attachment1.put("color", slackMessageColor);
		attachment1.put("text", slackMessageText);
		attachment1.put("fallback", slackMessageFallback);
		attachment1.put("ts", System.currentTimeMillis() / 1000);
		attachment1.put("footer", "Apache Unomi integration - " + techInfo);
		attachment1.put("footer_icon",
				"https://www.jahia.com/files/live/sites/jahiacom/files/platform/Marketing%20Factory/Images/unomi-logo.png");

		// Build the slack button (called slackAction)
		JSONArray slackActions = new JSONArray();
		JSONObject slackAction1 = new JSONObject();

		String profileId = event.getProfileId();
		unomiProfileUrl = unomiProfileUrl.replace("{scope}", event.getScope());
		unomiProfileUrl = unomiProfileUrl.replace("{profileId}", profileId);
		slackAction1.put("url", unomiProfileUrl);

		// attachment1.put("title_link", mfProfileUrl);

		slackAction1.put("type", "button");
		slackAction1.put("text", "Look at the profile in MFactory");
		slackActions.put(slackAction1);
		attachment1.put("actions", slackActions);

		return attachment1;
	}

	/**
	 * 
	 * @param event
	 * @param fields
	 * @param tagsToExclude
	 * @return
	 * @throws JSONException
	 */
	private JSONArray getJsonFromProfileProperties(Event event, JSONArray fields, String[] tagsToExclude)
			throws JSONException {

		for (Entry<String, Object> propertyInVisitorProfile : event.getProfile().getProperties().entrySet()) {

			String key = propertyInVisitorProfile.getKey().toString();
			logger.debug("\nkey : {}", key);

			PropertyType pt = profileService.getPropertyType(key);

			if (excludeProperty(pt, tagsToExclude))
				continue;

			String propertyName = pt.getMetadata().getName();
			JSONObject field = getJsonObjfromProperty(propertyName, propertyInVisitorProfile.getValue().toString());
			fields.put(field);

			logger.debug("Profile Property : {}", field);
		}

		return fields;
	}

	/**
	 * 
	 * @param title
	 * @param value
	 * @return
	 * @throws JSONException
	 */
	private JSONObject getJsonObjfromProperty(String title, Object value) throws JSONException {

		String strValue = (String) value;
		JSONObject slackField = new JSONObject();

		// Beyond 65 characters, the values won't display nicely
		if (strValue.length() > 65) {
			strValue = strValue.substring(0, 63) + "...";
		}

		slackField.put("value", strValue);
		slackField.put("title", title);
		slackField.put("short", true);

		return slackField;
	}

	/**
	 * Specific for session property
	 * 
	 * @param friendlyName
	 * @param propertyKey
	 * @param event
	 * @return
	 * @throws JSONException
	 */
	private JSONObject getJsonFromSessionProperty(String friendlyName, String propertyKey, Event event)
			throws JSONException {

		if (event.getSession().getProperty(propertyKey) != null
				&& event.getSession().getProperty(propertyKey).toString().length() > 0) {

			return getJsonObjfromProperty(friendlyName, event.getSession().getProperty(propertyKey));

		}

		return null;
	}

	/**
	 * Execute the http request
	 * 
	 * @param textUrl
	 * @param jsonForSlack
	 * @return
	 * @throws IOException
	 */
	private int executePostRequestToSlack(String textUrl, String jsonForSlack) {

		CloseableHttpClient client = null ;
		
		try {
			
			 client = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(textUrl);
			
			StringEntity entity = new StringEntity(jsonForSlack);
			httpPost.setEntity(entity);
			
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");
			
			CloseableHttpResponse response = null;
			
			response = client.execute(httpPost);
			
			if (response.getStatusLine().getStatusCode() != 200) {
				logger.error("Error when executing request to slack - http code {} - response {}", response.getStatusLine().getStatusCode(), response.toString());
			}
			
		} catch (IOException e) {
			logger.error("Error when executing request to slack: {}", e);
			return EventService.NO_CHANGE;
		} finally {
			try {
				client.close();
			} catch (IOException e) {
				logger.error("Cannot close: {}", e);
			}
		}

		return EventService.NO_CHANGE;
	}
	
	public String getSlackHookMessageServiceUrl() {
		return slackHookMessageServiceUrl;
	}

	public void setSlackHookMessageServiceUrl(String slackHookMessageServiceUrl) {
		this.slackHookMessageServiceUrl = slackHookMessageServiceUrl;
	}

	public String getSlackHookMessageServiceWorkspaceId() {
		return slackHookMessageServiceWorkspaceId;
	}

	public void setSlackHookMessageServiceWorkspaceId(String slackHookMessageServiceWorkspaceId) {
		this.slackHookMessageServiceWorkspaceId = slackHookMessageServiceWorkspaceId;
	}

	public String getSlackHookMessageServiceToken() {
		return slackHookMessageServiceToken;
	}

	public void setSlackHookMessageServiceToken(String slackHookMessageServiceToken) {
		this.slackHookMessageServiceToken = slackHookMessageServiceToken;
	}

	public ProfileService getProfileService() {
		return profileService;
	}

	public void setProfileService(ProfileService profileService) {
		this.profileService = profileService;
	}

	public String getUnomiProfileUrl() {
		return unomiProfileUrl;
	}

	public void setUnomiProfileUrl(String unomiProfileUrl) {
		this.unomiProfileUrl = unomiProfileUrl;
	}

	public String getUnomiSystemTagsExclude() {
		return unomiSystemTagsExclude;
	}

	public void setUnomiSystemTagsExclude(String unomiSystemTagsExclude) {
		this.unomiSystemTagsExclude = unomiSystemTagsExclude;
	}

	public String getSlackMessagePretext() {
		return slackMessagePretext;
	}

	public void setSlackMessagePretext(String slackMessagePretext) {
		this.slackMessagePretext = slackMessagePretext;
	}

	public String getSlackMessageThumbUrl() {
		return slackMessageThumbUrl;
	}

	public void setSlackMessageThumbUrl(String slackMessageThumbUrl) {
		this.slackMessageThumbUrl = slackMessageThumbUrl;
	}

	public String getSlackMessageColor() {
		return slackMessageColor;
	}

	public void setSlackMessageColor(String slackMessageColor) {
		this.slackMessageColor = slackMessageColor;
	}

	public String getSlackMessageText() {
		return slackMessageText;
	}

	public void setSlackMessageText(String slackMessageText) {
		this.slackMessageText = slackMessageText;
	}

	public String getSlackMessageFallback() {
		return slackMessageFallback;
	}

	public void setSlackMessageFallback(String slackMessageFallback) {
		this.slackMessageFallback = slackMessageFallback;
	}

	public String getSlackMessageTitle() {
		return slackMessageTitle;
	}

	public void setSlackMessageTitle(String slackMessageTitle) {
		this.slackMessageTitle = slackMessageTitle;
	}

}
