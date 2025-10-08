package io.github.patbattb.yougile.plugins.priority;

import io.github.patbattb.yougileapilib.domain.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PriorityTask {
    private final String id;
    private final String title;
    private final boolean completed;
    private final boolean archived;
    private final List<PriorityTask> subtasks;
    private final Map<String, String> stickers;

    public PriorityTask(Task task) {
        this.id = task.getId();
        this.title = task.getTitle();
        this.completed = task.isCompleted();
        this.archived = task.isArchived();
        this.stickers = task.getStickers();
        this.subtasks = new ArrayList<>();
    }

    String getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    List<PriorityTask> getSubtasks() {
        return subtasks;
    }

    Map<String, String> getStickers() {
        return stickers;
    }

    boolean isCompleted() {
        return completed;
    }

    boolean isArchived() {
        return archived;
    }
}
