package io.github.patbattb.yougile.plugins.priority;

import io.github.patbattb.plugins.core.expection.PluginCriticalException;
import io.github.patbattb.plugins.core.expection.PluginInterruptedException;
import io.github.patbattb.yougileapilib.domain.Task;

import java.util.*;

public class TaskHandler implements AutoCloseable {

    private final RequestSender requestSender;
    private final Parameters parameters;
    private final String noState = "-";

    TaskHandler(Parameters parameters) {
        this.parameters = parameters;
        requestSender = new RequestSender(parameters.token(), parameters.requestCountPerMinute());
    }

    List<Task> getTaskList() throws PluginCriticalException, PluginInterruptedException {
        List<Task> taskList = new ArrayList<>();
        for (String columnId: parameters.columnIds()) {
            try {
                taskList.addAll(requestSender.getTasks(columnId)
                        .stream()
                        .filter(task -> !task.isArchived() &&
                                !task.isCompleted() &&
                                !parameters.delayedState().equals(task.getStickers().get(parameters.priorityStickerId()))
                        )
                        .toList()
                );
            } catch (PluginCriticalException e) {
                requestSender.shutdown();
                throw new PluginCriticalException(e);
            } catch (PluginInterruptedException e) {
                requestSender.shutdown();
                throw new PluginInterruptedException(e);
            }
        }
        return taskList;
    }

    void updateTasks(List<Task> taskList) throws PluginCriticalException, PluginInterruptedException {
        try {
            requestSender.updateTasks(taskList);
        } catch (PluginCriticalException e) {
            requestSender.shutdown();
            throw new PluginCriticalException(e);
        } catch (PluginInterruptedException e) {
            requestSender.shutdown();
            throw new PluginInterruptedException(e);
        }
    }

    Map<Task, List<Task>> groupTasks(List<Task> taskList) throws PluginCriticalException, PluginInterruptedException {
        Map<Task, List<Task>> taskMap = new HashMap<>();
        for (Task task: taskList) {
            if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
                taskMap.put(task, pullSubtasks(task));
            }
        }
        return taskMap;
    }

    List<Task> getTasksToUpdate(Map<Task, List<Task>> taskMap) {
        List<Task> tasksToUpdate = new ArrayList<>();
        for (Map.Entry<Task, List<Task>> entry: taskMap.entrySet()) {
            Task parentTask = entry.getKey();
            List<String> subtaskStates = entry.getValue().stream()
                    .map(Task::getStickers)
                    .map(elem -> elem.getOrDefault(parameters.priorityStickerId(), noState))
                    .toList();
            Optional<String> maxSubtaskStateOption = getMaxPriorityState(subtaskStates.toArray(String[]::new));
            String maxSubtaskState = maxSubtaskStateOption.orElse(noState);
            String parentState = parentTask.getStickers().getOrDefault(parameters.priorityStickerId(), noState);
            if (!parentState.equals(maxSubtaskState)) {
                parentTask.getStickers().put(parameters.priorityStickerId(), maxSubtaskState);
                tasksToUpdate.add(parentTask);
            }
        }
        return tasksToUpdate;
    }

    private Optional<String> getMaxPriorityState(String... states) {
        if (states.length == 0) {
            return Optional.empty();
        }
        return Arrays.stream(states).filter(Objects::nonNull).max(this::stateComparator);
    }

    private int stateComparator(String stateOne, String stateTwo) {
        Optional<Priority> two = parameters.getPriorityById(stateTwo);
        if (two.isEmpty()) {
            return 1;
        }
        Optional<Priority> one = parameters.getPriorityById(stateOne);
        if (one.isEmpty()) {
            return -1;
        }
        return two.get().order() - one.get().order();
    }

    private List<Task> pullSubtasks(Task task) throws PluginInterruptedException, PluginCriticalException {
        List<Task> taskList = new ArrayList<>();
        for (String taskId: task.getSubtasks()) {
            try {
                Task subTask = requestSender.getTask(taskId);
                if (!subTask.isArchived() && !subTask.isCompleted() && !subTask.isDeleted()) {
                    taskList.add(subTask);
                }
            } catch (PluginInterruptedException e) {
                requestSender.shutdown();
                throw new PluginInterruptedException(e);
            } catch (PluginCriticalException e) {
                requestSender.shutdown();
                throw new PluginCriticalException(e);
            }
        }
        return taskList;
    }

    @Override
    public void close() {
        requestSender.close();
    }

    public void shutdown() {
        requestSender.shutdown();
    }
}
