package com.vidhura.ai.service;

import com.vidhura.ai.dto.AnalysisTaskDto;
import com.vidhura.ai.model.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AnalysisTaskManager {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskManager.class);

    private final Map<String, AnalysisTaskDto> taskMap = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    public AnalysisTaskDto createTask(String taskId, String fileName, boolean cached, Policy.PolicyAnalysis cachedResult) {
        Instant now = Instant.now();
        AnalysisTaskDto task = AnalysisTaskDto.builder()
                .taskId(taskId)
                .fileName(fileName)
                .status(cached ? "COMPLETED" : "PROCESSING")
                .progress(cached ? 100 : 5)
                .step(cached ? "COMPLETE" : "UPLOADED")
                .message(cached ? "Loaded instantly from cache ✓" : "Document uploaded, beginning processing...")
                .cached(cached)
                .result(cachedResult)
                .createdAt(now)
                .updatedAt(now)
                .build();

        taskMap.put(taskId, task);
        return task;
    }

    public Optional<AnalysisTaskDto> getTask(String taskId) {
        if (taskId == null) return Optional.empty();
        return Optional.ofNullable(taskMap.get(taskId));
    }

    public void updateProgress(String taskId, int progress, String step, String message) {
        AnalysisTaskDto task = taskMap.get(taskId);
        if (task != null) {
            task.setProgress(progress);
            task.setStep(step);
            task.setMessage(message);
            task.setUpdatedAt(Instant.now());
            broadcast(taskId, "PROGRESS", task);
        }
    }

    public void completeTask(String taskId, Policy.PolicyAnalysis result) {
        AnalysisTaskDto task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setStep("COMPLETE");
            task.setMessage("Analysis successfully completed ✓");
            task.setResult(result);
            task.setUpdatedAt(Instant.now());
            broadcast(taskId, "COMPLETE", task);
            closeEmitters(taskId);
        }
    }

    public void failTask(String taskId, String error) {
        AnalysisTaskDto task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus("FAILED");
            task.setStep("FAILED");
            task.setError(error);
            task.setMessage("Analysis failed: " + error);
            task.setUpdatedAt(Instant.now());
            broadcast(taskId, "ERROR", task);
            closeEmitters(taskId);
        }
    }

    public SseEmitter subscribe(String taskId) {
        // 5-minute timeout for SSE emitter
        SseEmitter emitter = new SseEmitter(300_000L);
        emitterMap.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(taskId, emitter);
        });
        emitter.onError(e -> removeEmitter(taskId, emitter));

        // Immediately send current state if task exists
        AnalysisTaskDto task = taskMap.get(taskId);
        if (task != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("INIT")
                        .data(task));
            } catch (IOException e) {
                removeEmitter(taskId, emitter);
            }
        }

        return emitter;
    }

    private void broadcast(String taskId, String eventName, AnalysisTaskDto task) {
        List<SseEmitter> emitters = emitterMap.get(taskId);
        if (emitters == null || emitters.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(task));
            } catch (Exception e) {
                removeEmitter(taskId, emitter);
            }
        }
    }

    private void removeEmitter(String taskId, SseEmitter emitter) {
        List<SseEmitter> emitters = emitterMap.get(taskId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emitterMap.remove(taskId);
            }
        }
    }

    private void closeEmitters(String taskId) {
        List<SseEmitter> emitters = emitterMap.remove(taskId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
