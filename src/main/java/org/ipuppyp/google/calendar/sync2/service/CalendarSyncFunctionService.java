package org.ipuppyp.google.calendar.sync2.service;

import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Event.ExtendedProperties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ipuppyp.google.calendar.sync2.model.SyncRequest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.*;
import java.util.regex.Pattern;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.attribute.PosixFilePermissions.fromString;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.stream.Collectors.toList;


@Slf4j
public class CalendarSyncFunctionService {

    private static final String PRIVATE = "private";
    private static final String ORIG_I_CAL_UID = "origICalUID";
    private static final String APPLICATION_NAME = "ipuppyp/google-calendar-sync";
    private final CalendarCrudService calendarCrudService;

    @SneakyThrows
    public CalendarSyncFunctionService() {
        String dataStoreDir;
        String credentials;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String storeDir = System.getProperty("user.home") + "/.store";
            dataStoreDir = storeDir + "/google-calendar-synch.tokens/tokens";
            credentials = storeDir + "/google-calendar-synch-credentials.json";
        } else {
            String storeDir = "/secrets";
            dataStoreDir = "/root/tokens";
            credentials = storeDir + "/google-calendar-synch-credentials.json";
            // original StoredCredential must be copied into a writable file
            log.info("Directory created: " + new File(dataStoreDir).mkdir());
            Path oldPath = Path.of(storeDir + "/tokens/StoredCredential");
            Path newPath = Path.of(dataStoreDir + "/StoredCredential");
            Files.copy(oldPath, newPath, REPLACE_EXISTING);
            Files.getFileAttributeView(newPath, PosixFileAttributeView.class)
                    .setPermissions(fromString("rw-rw-r--"));
        }
        calendarCrudService = new CalendarCrudService(APPLICATION_NAME, credentials, dataStoreDir);
    }

    @SneakyThrows
    public void syncWithRetry(SyncRequest request) {
        try {
            sync(request);
        } catch (Throwable ex) {
            log.warn("Exception on 1st attempt: ", ex);
            Thread.sleep(2000);
            try {
                sync(request);
            } catch (Throwable ex2) {
                log.warn("Exception on 2nd attempt: ", ex);
                Thread.sleep(5000);
                sync(request);
            }

        }
    }

    public void sync(SyncRequest request) {

        log.info("*********************************");
        log.info("* Request :\t\t\t*");
        log.info(request.toString());
        log.info("*********************************");


        Pattern eventFilterPattern = Pattern.compile(request.getEventFilter(), CASE_INSENSITIVE);

        Calendar source = calendarCrudService.findCalendarByName(request.getSourceCalendar());
        Calendar target = calendarCrudService.findCalendarByName(request.getTargetCalendar());

        List<Event> eventsInSource = calendarCrudService.findEventsByCalendar(source).stream()
                .filter(event -> !(request.getPublicOnly() && PRIVATE.equals(event.getVisibility())))
                .filter(event -> !eventFilterPattern.matcher(event.getSummary()).find())
                .map(event -> prepareToSave(event, request.getEventPrefix()))
                .collect(toList());
        List<Event> eventsInTarget = calendarCrudService.findEventsByCalendar(target).stream()
                .filter(event -> !"cancelled".equals(event.getStatus()) && event.getSummary().contains(request.getEventPrefix()))
                .collect(toList());

        List<Event> newEvents = eventsInSource.stream().filter(event -> notContains(eventsInTarget, event)).collect(toList());
        List<Event> removedEvents = eventsInTarget.stream().filter(event -> notContains(eventsInSource, event)).collect(toList());
        List<Event> changedEvents = eventsInSource.stream().filter(event -> changed(eventsInTarget, event)).collect(toList());

        log.info("*********************************");
        log.info("* Events to add = {}\t\t*", newEvents.size());
        log.info("* Events to remove = {}\t\t*", removedEvents.size());
        log.info("* Events to update = {}\t\t*", changedEvents.size());
        log.info("*********************************");

        calendarCrudService.addEvents(target, newEvents);
        calendarCrudService.removeEvents(target, removedEvents);
        calendarCrudService.updateEvents(target, changedEvents);

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
                Objects.equals(origEvent.getEnd(), event.getEnd()) &&
                Objects.equals(origEvent.getDescription(), event.getDescription()) &&
                Objects.equals(origEvent.getLocation(), event.getLocation());
    }

    private Event prepareToSave(Event event, String eventPrefix) {
        Map<String, String> shared = new HashMap<>();
        shared.put(ORIG_I_CAL_UID, event.getICalUID());
        return event
                .setSummary(eventPrefix + " " + event.getSummary())
                .setDescription("see details in original event")
                .setId(null)
                .setExtendedProperties(new ExtendedProperties().setShared(shared))
                .setICalUID(null)
                .setVisibility(null)
                .setAttendees(List.of());

    }


}