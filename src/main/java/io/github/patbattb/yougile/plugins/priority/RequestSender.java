package io.github.patbattb.yougile.plugins.priority;

import io.github.patbattb.plugins.core.expection.PluginCriticalException;
import io.github.patbattb.plugins.core.expection.PluginInterruptedException;
import io.github.patbattb.yougileapilib.domain.AuthKey;
import io.github.patbattb.yougileapilib.domain.PagingContainer;
import io.github.patbattb.yougileapilib.domain.QueryParams;
import io.github.patbattb.yougileapilib.domain.Task;
import io.github.patbattb.yougileapilib.domain.body.TaskUpdateBody;
import io.github.patbattb.yougileapilib.service.TaskService;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.conn.ConnectTimeoutException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class RequestSender implements AutoCloseable {

    private final int REQUEST_FREQUENCY_SECONDS = 60;
    private final int DEFAULT_TASK_LIMIT = 1000;
    private final TaskService taskService;
    private final Semaphore semaphore;
    private final Object sendLock = new Object();
    private final ExecutorService executorService;

    RequestSender(String token, int requestCount) {
        AuthKey key = new AuthKey(token);
        this.taskService = new TaskService(key);
        this.semaphore = new Semaphore(requestCount);
        executorService = Executors.newFixedThreadPool(requestCount);
    }

    List<Task> getTasks(String columnId) throws PluginCriticalException, PluginInterruptedException {
        QueryParams qParams = QueryParams.builder()
                .addParameter("columnId", columnId)
                .addParameter("limit", DEFAULT_TASK_LIMIT)
                .build();
        PagingContainer<Task> taskContainer = getTaskContainerSync(qParams);
        List<Task> taskList = new ArrayList<>(taskContainer.getContent());
        while(taskContainer.isNext()) {
            qParams = QueryParams.builder()
                    .addParameter("columnId", columnId)
                    .addParameter("offset", taskContainer.getOffset() + taskContainer.getCount())
                    .build();
            taskContainer = getTaskContainerSync(qParams);
            taskList.addAll(taskContainer.getContent());
        }
        return taskList;
    }

    void updateTasks(List<Task> tasks) throws PluginCriticalException, PluginInterruptedException {
        for (Task task: tasks) {
            TaskUpdateBody body = TaskUpdateBody.builder().stickers(task.getStickers()).build();
            updateTaskSync(task.getId(), body);
        }
    }

    public Task getTask(String taskId) throws PluginInterruptedException, PluginCriticalException {
        try {
            semaphore.acquire();
            synchronized (sendLock) {
                executorService.submit(this::waitPause);
                return taskService.getTaskById(taskId);
            }
        } catch (InterruptedException | ClientProtocolException | ConnectTimeoutException e) {
            throw new PluginInterruptedException("Getting task by id was interrupted." ,e);
        } catch (URISyntaxException | IOException e) {
            throw new PluginCriticalException("Error during getting task by id", e);
        }
    }

    private PagingContainer<Task> getTaskContainerSync(QueryParams qParams) throws PluginInterruptedException, PluginCriticalException {
        try {
            semaphore.acquire();
            synchronized (sendLock) {
                executorService.submit(this::waitPause);
                return taskService.getTaskList(qParams);
            }
        } catch (InterruptedException | ClientProtocolException | ConnectTimeoutException e) {
            throw new PluginInterruptedException("Getting tasks was interrupted." ,e);
        } catch (URISyntaxException | IOException e) {
            throw new PluginCriticalException("Error during getting tasks", e);
        }
    }

    private void updateTaskSync(String taskId, TaskUpdateBody body) throws PluginInterruptedException, PluginCriticalException {
        try {
            semaphore.acquire();
            synchronized (sendLock) {
                executorService.submit(this::waitPause);
                taskService.updateTask(taskId, body);
            }
        } catch (InterruptedException | ClientProtocolException | ConnectTimeoutException e) {
            throw new PluginInterruptedException("Getting tasks was interrupted." ,e);
        } catch (URISyntaxException | IOException e) {
            throw new PluginCriticalException("Error during getting tasks", e);
        }
    }

    private void waitPause() {
        try {
            TimeUnit.SECONDS.sleep(REQUEST_FREQUENCY_SECONDS);
        } catch (InterruptedException e) {
            //ignored
        } finally {
            semaphore.release();
        }
    }

    void shutdown() {
        executorService.shutdownNow();
    }

    @Override
    public void close() {
        executorService.close();
    }
}