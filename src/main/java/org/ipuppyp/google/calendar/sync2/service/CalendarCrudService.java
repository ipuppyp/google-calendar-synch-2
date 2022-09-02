package org.ipuppyp.google.calendar.sync2.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;

public class CalendarCrudService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CalendarCrudService.class);
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

	private com.google.api.services.calendar.Calendar client;

	private static final List<String> SCOPES =
			Collections.singletonList(CalendarScopes.CALENDAR_READONLY);

	public CalendarCrudService(String applicationName, String credentials, String dataStoreDir) {
		LOGGER.info("Data store dir: {}", dataStoreDir);
		init(applicationName, credentials, dataStoreDir);
	}

	private void init(String applicationName, String credentials, String dataStoreDir) {
		HttpTransport httpTransport;
		try {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();


			Credential credential = authorize(credentials, httpTransport, dataStoreDir);

			client = new com.google.api.services.calendar.Calendar.Builder(httpTransport, JSON_FACTORY, credential)
					.setApplicationName(applicationName).build();
		} catch (GeneralSecurityException | IOException e) {
			throw new ApiCallException(e);
		}

	}

	private Credential authorize(String credentials, HttpTransport httpTransport, String dataStoreDir) throws IOException {

		File dataDirectory = new File(dataStoreDir);
		LOGGER.info("Data dataDirectory: {}", dataDirectory);

		DataStoreFactory dataStoreFactory = new FileDataStoreFactory(dataDirectory);

		GoogleClientSecrets clientSecrets;
		try {

			clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
					new InputStreamReader(new FileInputStream(credentials)));
			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY,
					clientSecrets, SCOPES).setDataStoreFactory(dataStoreFactory)
							.setRefreshListeners(Set.of())
							.build();
			Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
					.authorize("user");
			return credential;

		} catch (IOException e) {
			throw new ApiCallException(e);
		}
	}

	public void addEvent(Calendar calendar, Event event) {
		try {

			LOGGER.debug("Add event {}, {} to calendar {}...", event.getSummary(), event.getStart().getDateTime(), calendar.getSummary());
			client.events().insert(calendar.getId(), event).execute();
			LOGGER.debug("Event added.");

		} catch (IOException e) {
			throw new ApiCallException(e);
		}
	}


	public void addEvents(Calendar calendar, Collection<Event> events) {
		events.forEach(event -> addEvent(calendar, event));
	}

	public void updateEvent(Calendar calendar, Event event) {
		try {
			LOGGER.debug("Update event {}, {} in calendar {}...", event.getSummary(), event.getStart().getDateTime(), calendar.getSummary());
			client.events().update(calendar.getId(), event.getId(), event).execute();
			LOGGER.debug("Event updated.");

		} catch (IOException e) {
			throw new ApiCallException(e);
		}
	}

	public void updateEvents(Calendar calendar, Collection<Event> events) {
		events.forEach(event -> updateEvent(calendar, event));
	}


	public void removeEvent(Calendar calendar, Event event) {
		try {
			LOGGER.debug("Remove event: {}, {} from calendar {}...", event.getSummary(), event.getStart().getDateTime(), calendar.getSummary());
			client.events().delete(calendar.getId(), event.getId()).execute();
			LOGGER.debug("Event removed.");
		} catch (IOException e) {
			throw new ApiCallException(e);
		}
	}

	public void removeEvents(Calendar calendar, Collection<Event> events) {
		events.forEach(event -> removeEvent(calendar, event));

	}

	public Events findEventsByCalendar(Calendar calendar) {
		try {

			LOGGER.debug("Loading events for calendar {}...", calendar.getSummary());
			Events events = client.events().list(calendar.getId())
					.setMaxResults(100)
					.setShowHiddenInvitations(true)
					//.setSingleEvents(true)
					.setTimeMin(new DateTime(new Date())).execute();
			LOGGER.debug("{} Events found.", events.getItems().size());
			return events;
		} catch (IOException e) {
			throw new ApiCallException(e);
		}
	}

	public Calendar findCalendarByName(String name) {
		try {

			LOGGER.debug("Finding calendar by name {}...", name);
			CalendarList calendarList = client.calendarList().list().execute();
			CalendarListEntry result = calendarList.getItems().stream().filter(cal -> cal.getSummary().equals(name))
					.findFirst().get();
			Calendar calendar = new Calendar().setId(result.getId()).setSummary(result.getSummary());

			LOGGER.debug("Calendar found: {}", calendar);
			return calendar;
		} catch (IOException e) {
			throw new ApiCallException(e);
		}
	}



}