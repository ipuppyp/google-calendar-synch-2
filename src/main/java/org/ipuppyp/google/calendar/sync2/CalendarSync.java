package org.ipuppyp.google.calendar.sync2;

import static java.lang.Boolean.parseBoolean;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.stream.Collectors.toList;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Event.ExtendedProperties;

import org.ipuppyp.google.calendar.sync2.service.CalendarCrudService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.regex.Pattern;



public class CalendarSync implements HttpFunction {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarSync.class);

    private static final String PRIVATE = "private";

    private static final String ORIG_I_CAL_UID = "origICalUID";

    private static final String APPLICATION_NAME = "ipuppyp/google-calendar-sync";
    // private static final String DATA_STORE_DIR = "/root/store/";
    private static final String DATA_STORE_DIR = "/root/store/";
    private static final String CREDENTIALS = getSecretsDir() + "/google-calendar-synch-credentials.json";

    private final CalendarCrudService calendarCrudService;

    public CalendarSync() throws IOException {
        new File(DATA_STORE_DIR).mkdir();
        LOGGER.info("dir created");
        Path path = Path.of(DATA_STORE_DIR + "StoredCredential");
        Files.copy(Path.of("/secrets2/tokens/StoredCredential"), path, REPLACE_EXISTING);
        LOGGER.info("file copied");

        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-rw-r--");
        PosixFileAttributeView posixView = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        posixView.setPermissions(permissions);

        calendarCrudService = new CalendarCrudService(APPLICATION_NAME, CREDENTIALS, DATA_STORE_DIR);
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws IOException {
        BufferedWriter writer = response.getWriter();

        LOGGER.info("*********************************");
        LOGGER.info("* STARTING SYNCHRONIZATION...   *");
        LOGGER.info("*********************************");

        try {
            sync(request);
            writer.write("DONE");
        }
        catch (Throwable ex) {
            LOGGER.error("Exception during synchronization", ex);
            writer.write(ex.getMessage());
        }

        LOGGER.info("*********************************");
        LOGGER.info("* SYNCHRONIZATION FINISHED.     *");
        LOGGER.info("*********************************");

    }

    private void sync(HttpRequest request) {

        String sourceCalendar = getProperty(request, "sourceCalendar");
        String targetCalendar = getProperty(request, "targetCalendar");
        String eventPrefix = getProperty(request, "eventPrefix");
        String eventFilter = getProperty(request, "eventFilter");
        Pattern eventFilterPattern = Pattern.compile(eventFilter, CASE_INSENSITIVE);
        boolean publicOnly = parseBoolean(getProperty(request, "publicOnly"));

        LOGGER.info("*********************************");
        LOGGER.info("* PARAMS:\t\t\t*");
        LOGGER.info("* sourceCalendar = {}\t*", sourceCalendar);
        LOGGER.info("* targetCalendar = {}\t*", targetCalendar);
        LOGGER.info("* eventPrefix = {}\t*", eventPrefix);
        LOGGER.info("* eventFilter = {}\t*", eventFilter);
        LOGGER.info("* publicOnly = {}\t*", publicOnly);
        LOGGER.info("*********************************");


        Calendar source = calendarCrudService.findCalendarByName(sourceCalendar);
        Calendar target = calendarCrudService.findCalendarByName(targetCalendar);

        List<Event> eventsInSource = calendarCrudService.findEventsByCalendar(source).getItems().stream()
                .filter(event -> !(publicOnly && PRIVATE.equals(event.getVisibility())))
                .filter(event -> !eventFilterPattern.matcher(event.getSummary()).find())
                .map(event -> event.setVisibility(null))
                .map(event -> setIds(event, eventPrefix))
                .collect(toList());
        List<Event> eventsInTarget = calendarCrudService.findEventsByCalendar(target).getItems().stream()
                .filter(event -> !"cancelled".equals(event.getStatus()) && event.getSummary().contains(eventPrefix))
                .collect(toList());

        List<Event> newEvents = eventsInSource.stream().filter(event -> notContains(eventsInTarget, event)).collect(toList());
        List<Event> removedEvents = eventsInTarget.stream().filter(event -> notContains(eventsInSource, event)).collect(toList());
        List<Event> changedEvents = eventsInSource.stream().filter(event -> changed(eventsInTarget, event)).collect(toList());

        LOGGER.info("*********************************");
        LOGGER.info("* Events to add = {}\t\t*", newEvents.size());
        LOGGER.info("* Events to remove = {}\t\t*", removedEvents.size());
        LOGGER.info("* Events to update = {}\t\t*", changedEvents.size());
        LOGGER.info("*********************************");

        calendarCrudService.addEvents(target, newEvents);
        calendarCrudService.removeEvents(target, removedEvents);
        calendarCrudService.updateEvents(target, changedEvents);

    }

    private static String getSecretsDir() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return System.getProperty("user.home") + "/.store";
        } else {
            return "/secrets";
        }

    }
    private boolean notContains(Collection<Event> events, Event event) {
        return findByICalId(events, event).isEmpty();
    }

    private Optional<Event> findByICalId(Collection<Event> events, Event event) {
        return events.stream().filter(origEvent -> equalsByICalUID(origEvent, event)).findAny();
    }

    private boolean equalsByICalUID(Event event, Event origEvent) {
        return Objects.equals(origEvent.getExtendedProperties().getShared().get(ORIG_I_CAL_UID), event.getExtendedProperties().getShared().get(ORIG_I_CAL_UID));
    }

    private boolean changed(Collection<Event> events, Event event) {
        Optional<Event> origEvent = findByICalId(events, event);
        boolean changed = origEvent.isPresent() && !equals(event, origEvent.get());
        if (changed) {
            event.setSequence(origEvent.get().getSequence());
            event.setId(origEvent.get().getId());
        }
        return changed;

    }
    private boolean equals(Event event, Event origEvent) {
        return Objects.equals(origEvent.getSummary(), event.getSummary()) &&
                Objects.equals(origEvent.getStart(), event.getStart()) &&
                Objects.equals(origEvent.getDescription(), event.getDescription()) &&
                Objects.equals(origEvent.getEnd(), event.getEnd());
    }

    private Event setIds(Event event, String eventPrefix) {
        Map<String, String> shared = new HashMap<>();
        shared.put(ORIG_I_CAL_UID, event.getICalUID());
        return event
                .setSummary(eventPrefix + " " + event.getSummary())
                .setDescription("see details in original event")
                .setId(null)
                .setExtendedProperties(new ExtendedProperties().setShared(shared))
                .setICalUID(null);

    }

    private String getProperty(HttpRequest request, String name) {
        Optional<String> property = request.getFirstQueryParameter(name);
        if (property.isEmpty()) {
            LOGGER.error("Missing configuration: \"{}\", please set it with a request parameter -D\n", name);
            throw new IllegalArgumentException("Missing configuration: " + name);
        }
        return property.get();

    }
}