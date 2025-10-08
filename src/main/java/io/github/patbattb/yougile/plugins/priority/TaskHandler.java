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

    List<PriorityTask> getTaskList() throws PluginCriticalException, PluginInterruptedException {
        List<PriorityTask> taskList = new ArrayList<>();
        for (String columnId: parameters.columnIds()) {
            try {
                taskList.addAll(getTasks(columnId));
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

    void updateTasks(List<PriorityTask> taskList) throws PluginCriticalException, PluginInterruptedException {
        try {
            for (PriorityTask task: taskList) {
                requestSender.updateTaskPriority(task.getId(), task.getStickers());
            }
        } catch (PluginCriticalException e) {
            requestSender.shutdown();
            throw new PluginCriticalException(e);
        } catch (PluginInterruptedException e) {
            requestSender.shutdown();
            throw new PluginInterruptedException(e);
        }
    }

    List<PriorityTask> getTasksToUpdate(List<PriorityTask> taskList) {
        return updatePriority(taskList, new ArrayList<>());
    }

    private List<PriorityTask> updatePriority(List<PriorityTask> tasks, List<PriorityTask> tasksToUpdate) {
        for (PriorityTask task: tasks) {
            List<PriorityTask> subtasks = task.getSubtasks();
            if (subtasks == null || subtasks.isEmpty()) {
                continue;
            }
            updatePriority(subtasks, tasksToUpdate);
            List<String> subtaskStates = task.getSubtasks().stream()
                    .map(PriorityTask::getStickers)
                    .map(elem -> elem.getOrDefault(parameters.priorityStickerId(), noState))
                    .toList();
            Optional<String> maxSubtaskStateOption = getMaxPriorityState(subtaskStates.toArray(String[]::new));
            String maxSubtaskState = maxSubtaskStateOption.orElse(noState);
            String parentState = task.getStickers().getOrDefault(parameters.priorityStickerId(), noState);
            if (!parentState.equals(maxSubtaskState)) {
                task.getStickers().put(parameters.priorityStickerId(), maxSubtaskState);
                tasksToUpdate.add(task);
            }
        }
        return tasksToUpdate;
    }

    private List<PriorityTask> getTasks(String columnId) throws PluginCriticalException, PluginInterruptedException {
        List<PriorityTask> priorityTasks = new ArrayList<>();
        List<Task> tasks = getTasksByColumnId(columnId);
        for (Task task: tasks) {
            PriorityTask priorityTask = new PriorityTask(task);
            if (isActive(priorityTask)) {
                priorityTask.getSubtasks().addAll(pullSubtasks(task.getSubtasks()));
                priorityTasks.add(priorityTask);
            }
        }
        return priorityTasks;
    }

    private List<Task> getTasksByColumnId(String columnId) throws PluginCriticalException, PluginInterruptedException {
        try {
            return requestSender.getTasks(columnId);
        } catch (PluginCriticalException e) {
            requestSender.shutdown();
            throw new PluginCriticalException("Critical error during getting tasks.", e);
        } catch (PluginInterruptedException e) {
            requestSender.shutdown();
            throw new PluginInterruptedException("Error during getting tasks.", e);
        }
    }

    private Task getTaskById(String taskId) throws PluginInterruptedException, PluginCriticalException {
        try {
            return requestSender.getTask(taskId);
        } catch (PluginInterruptedException e) {
            requestSender.shutdown();
            throw new PluginInterruptedException("Error in getting task by id.", e);
        } catch (PluginCriticalException e) {
            requestSender.shutdown();
            throw new PluginCriticalException("Critical error in getting task by id.", e);
        }
    }

    private List<PriorityTask> pullSubtasks(List<String> subtaskIds) throws PluginCriticalException, PluginInterruptedException {
        List<PriorityTask> priorityTaskList = new ArrayList<>();
        if (subtaskIds == null || subtaskIds.isEmpty()) {
            return priorityTaskList;
        }
        for (String subtaskId: subtaskIds) {
            Task subtask = getTaskById(subtaskId);
            PriorityTask prioritySubTask = new PriorityTask(subtask);
            if (isActive(prioritySubTask)) {
                prioritySubTask.getSubtasks().addAll(pullSubtasks(subtask.getSubtasks()));
                priorityTaskList.add(prioritySubTask);
            }
        }
        return priorityTaskList;
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

    private boolean isActive(PriorityTask task) {
        return !task.isDeleted() && !task.isArchived() && !task.isCompleted() &&
                !parameters.delayedState().equals(task.getStickers().get(parameters.priorityStickerId()));
    }

    @Override
    public void close() {
        requestSender.close();
    }

    public void shutdown() {
        requestSender.shutdown();
    }
}
