package io.github.patbattb.yougile.plugins.priority;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Optional;

public record Parameters(String token, int requestCountPerMinute, String priorityStickerId, String delayedState,
                         List<String> columnIds, List<Priority> priorityOrder) {
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Parameters(
            @JsonProperty(value = "token", required = true) String token,
            @JsonProperty(value = "requestFrequency", required = true) int requestCountPerMinute,
            @JsonProperty(value = "priorityStickerId", required = true) String priorityStickerId,
            @JsonProperty(value = "delayedState", required = true) String delayedState,
            @JsonProperty(value = "columnIds", required = true) List<String> columnIds,
            @JsonProperty(value = "priorityOrder", required = true) List<Priority> priorityOrder) {
        this.token = token;
        this.requestCountPerMinute = requestCountPerMinute;
        this.priorityStickerId = priorityStickerId;
        this.delayedState = delayedState;
        this.columnIds = columnIds;
        this.priorityOrder = priorityOrder;
    }

    public Optional<Priority> getPriorityById(String stateId) {
        return priorityOrder.stream()
                .filter(elem -> elem.stateId().equals(stateId))
                .findAny();
    }
}
