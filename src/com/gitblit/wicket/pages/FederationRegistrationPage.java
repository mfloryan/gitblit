/*
 * Copyright 2011 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.wicket.pages;

import java.util.Collections;
import java.util.List;

import org.apache.wicket.PageParameters;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.DataView;
import org.apache.wicket.markup.repeater.data.ListDataProvider;

import com.gitblit.GitBlit;
import com.gitblit.Keys;
import com.gitblit.models.FederationModel;
import com.gitblit.models.FederationModel.RepositoryStatus;
import com.gitblit.wicket.GitBlitWebSession;
import com.gitblit.wicket.WicketUtils;

public class FederationRegistrationPage extends BasePage {

	public FederationRegistrationPage(PageParameters params) {
		super(params);

		setupPage("", getString("gb.registrations"));

		final boolean showAdmin;
		if (GitBlit.getBoolean(Keys.web.authenticateAdminPages, true)) {
			boolean allowAdmin = GitBlit.getBoolean(Keys.web.allowAdministration, false);
			showAdmin = allowAdmin && GitBlitWebSession.get().canAdmin();
		} else {
			showAdmin = false;
		}
		setStatelessHint(true);

		String url = WicketUtils.getUrlParameter(params);
		String name = WicketUtils.getNameParameter(params);

		FederationModel registration = GitBlit.self().getFederationRegistration(url, name);
		if (registration == null) {
			error("Could not find federation registration!", true);
		}

		add(new Label("url", registration.url));
		add(WicketUtils.getRegistrationImage("typeIcon", registration, this));
		add(new Label("typeName", registration.isResultData() ? getString("gb.federationResults")
				: getString("gb.federationRegistration")));
		add(new Label("frequency", registration.frequency));
		add(new Label("folder", registration.folder));
		add(new Label("token", showAdmin ? registration.token : "--"));
		add(WicketUtils.createTimestampLabel("lastPull", registration.lastPull, getTimeZone()));
		add(WicketUtils.createTimestampLabel("nextPull", registration.nextPull, getTimeZone()));

		StringBuilder inclusions = new StringBuilder();
		for (String inc : registration.inclusions) {
			inclusions.append(inc).append("<br/>");
		}
		StringBuilder exclusions = new StringBuilder();
		for (String ex : registration.exclusions) {
			exclusions.append(ex).append("<br/>");
		}

		add(new Label("inclusions", inclusions.toString()).setEscapeModelStrings(false));

		add(new Label("exclusions", exclusions.toString()).setEscapeModelStrings(false));

		List<RepositoryStatus> list = registration.getStatusList();
		Collections.sort(list);
		DataView<RepositoryStatus> dataView = new DataView<RepositoryStatus>("row",
				new ListDataProvider<RepositoryStatus>(list)) {
			private static final long serialVersionUID = 1L;
			private int counter;

			@Override
			protected void onBeforeRender() {
				super.onBeforeRender();
				counter = 0;
			}

			public void populateItem(final Item<RepositoryStatus> item) {
				final RepositoryStatus entry = item.getModelObject();
				item.add(WicketUtils.getPullStatusImage("statusIcon", entry.status));
				item.add(new Label("name", entry.name));
				item.add(new Label("status", entry.status.name()));
				WicketUtils.setAlternatingBackground(item, counter);
				counter++;
			}
		};
		add(dataView);
	}
}
