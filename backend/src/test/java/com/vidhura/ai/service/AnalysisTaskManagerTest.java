package com.vidhura.ai.service;

import com.vidhura.ai.dto.AnalysisTaskDto;
import com.vidhura.ai.model.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisTaskManagerTest {

    private AnalysisTaskManager taskManager;

    @BeforeEach
    void setUp() {
        taskManager = new AnalysisTaskManager();
    }

    @Test
    void testTaskCreationAndProgressUpdates() {
        String taskId = "test-task-123";
        AnalysisTaskDto task = taskManager.createTask(taskId, "test_policy.pdf", false, null);

        assertNotNull(task);
        assertEquals("test-task-123", task.getTaskId());
        assertEquals("PROCESSING", task.getStatus());
        assertEquals(5, task.getProgress());

        // Update progress
        taskManager.updateProgress(taskId, 50, "AI_ANALYSIS", "Running AI model analysis...");
        Optional<AnalysisTaskDto> updatedOpt = taskManager.getTask(taskId);
        assertTrue(updatedOpt.isPresent());
        assertEquals(50, updatedOpt.get().getProgress());
        assertEquals("AI_ANALYSIS", updatedOpt.get().getStep());
        assertEquals("Running AI model analysis...", updatedOpt.get().getMessage());

        // Complete task
        Policy.PolicyAnalysis result = Policy.PolicyAnalysis.builder()
                .policyName("Test Completed Policy")
                .build();
        taskManager.completeTask(taskId, result);

        Optional<AnalysisTaskDto> completedOpt = taskManager.getTask(taskId);
        assertTrue(completedOpt.isPresent());
        assertEquals("COMPLETED", completedOpt.get().getStatus());
        assertEquals(100, completedOpt.get().getProgress());
        assertNotNull(completedOpt.get().getResult());
        assertEquals("Test Completed Policy", completedOpt.get().getResult().getPolicyName());
    }

    @Test
    void testTaskFailure() {
        String taskId = "test-task-fail";
        taskManager.createTask(taskId, "corrupt.pdf", false, null);
        taskManager.failTask(taskId, "Corrupted PDF header");

        Optional<AnalysisTaskDto> failedOpt = taskManager.getTask(taskId);
        assertTrue(failedOpt.isPresent());
        assertEquals("FAILED", failedOpt.get().getStatus());
        assertEquals("Corrupted PDF header", failedOpt.get().getError());
    }
}
