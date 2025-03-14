package org.ipuppyp.google.calendar.sync2.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@Builder
@Getter
@ToString
public class SyncRequest {

    @NonNull
    private String sourceCalendar;
    @NonNull
    private String targetCalendar;
    @NonNull
    Boolean publicOnly;
    @NonNull
    private String eventFilter;
    @NonNull
    private String eventPrefix;

}
