package io.github.patbattb.yougile.plugins.priority;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.patbattb.plugins.core.Plugin;
import io.github.patbattb.plugins.core.expection.PluginCriticalException;
import io.github.patbattb.plugins.core.expection.PluginInterruptedException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

public class PriorityPlugin extends Plugin {

    private final String configFileName = "priority.config.json";

    @Override
    public String getTitle() {
        return "Priority";
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public int timeout() {
        return 60;
    }

    @Override
    public void run() throws PluginInterruptedException, PluginCriticalException {
        Parameters parameters = initProperties();
        TaskHandler handler = null;
        try (TaskHandler taskHandler = new TaskHandler(parameters)) {
            handler = taskHandler;
            List<PriorityTask> taskList = taskHandler.getTaskList();
            taskList = taskHandler.getTasksToUpdate(taskList);
            if (taskList != null && !taskList.isEmpty()) {
                taskHandler.updateTasks(taskList);
            }
            taskHandler.shutdown();
            handler = null;
        } finally {
            if (handler != null) {
                handler.shutdown();
            }
        }
    }

    private Parameters initProperties() {
        Path configFile = getConfigFilePath();
        JsonMapper mapper = new JsonMapper();
        try {
            return mapper.readValue(configFile.toFile(), Parameters.class);
        } catch (IOException e) {
            throw new RuntimeException("Couldn't read config file.", e);
        }
    }

    private Path getConfigFilePath() {
        String folderPath = PriorityPlugin.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();
        String decodedFolderPath = URLDecoder.decode(folderPath, StandardCharsets.UTF_8);
        return Path.of(decodedFolderPath).getParent().resolve(configFileName);
    }
}
