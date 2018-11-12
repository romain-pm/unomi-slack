package org.apache.unomi.slack.actions;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.unomi.api.*;
import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.junit.Test;

public class TestSlackMessageAction {
	public static final String ITEM_TYPE = "aa";

	@Test
	public void test2() throws Exception {

		SlackMessageAction slackMessageAction = new SlackMessageAction();
		
		slackMessageAction.setUnomiProfileUrl("http://localhost:8080/cms/edit/default/en/sites/{scope}.wem-profile.html?wemProfileType=profiles&wemProfileId={profileId}");
		slackMessageAction.setSlackHookMessageServiceUrl("https://hooks.slack.com/services/");
		slackMessageAction.setSlackHookMessageServiceWorkspaceId("T04CA9GN2");
		slackMessageAction.setSlackHookMessageServiceToken("BE09GNA2X/BOIb3nGVSr5TYIZwpZrjcMW5");

		Action action = new Action();
		action.setParameter("toto", "titi");
		Map<String, Object> properties = new HashMap<String, Object>();		
		
		Profile visitor = new Profile();
		visitor.setProperty("firstName", "Romain");
		visitor.setProperty("lastName", "Gauthier");

		Event e = new Event("pageViewEvent", new Session(), visitor, "digitall", null, null, properties, new Date(),
				true);
		
		e.setProfileId("2c2c150f-ae2e-48fb-a221-45d1bee96276");
		e.setProperty("company", "Jahia");
		
		slackMessageAction.internalExecute(action, e);

	}

}
