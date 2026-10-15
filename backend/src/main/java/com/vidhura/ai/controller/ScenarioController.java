package com.vidhura.ai.controller;

import com.vidhura.ai.dto.ApiResponse;
import com.vidhura.ai.dto.ScenarioDto;
import com.vidhura.ai.model.Policy;
import com.vidhura.ai.model.User;
import com.vidhura.ai.security.JwtAuthenticationFilter;
import com.vidhura.ai.service.PolicyService;
import com.vidhura.ai.service.ScenarioEngineService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/scenario")
public class ScenarioController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioController.class);

    private final ScenarioEngineService scenarioEngineService;
    private final PolicyService policyService;

    public ScenarioController(ScenarioEngineService scenarioEngineService, PolicyService policyService) {
        this.scenarioEngineService = scenarioEngineService;
        this.policyService = policyService;
    }

    private User getActiveUser(User user) {
        return (user != null) ? user : JwtAuthenticationFilter.GUEST_USER;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScenarioDto.ScenarioResponse>> simulateScenario(
            @Valid @RequestBody ScenarioDto.ScenarioRequest request,
            @AuthenticationPrincipal User user) {
        if (request == null || request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<ScenarioDto.ScenarioResponse>err("Question is required"));
        }

        try {
            Policy policy = null;
            if (request.getPolicyId() != null && !request.getPolicyId().trim().isEmpty()) {
                String userId = getActiveUser(user).getId();
                Optional<Policy> policyOpt = policyService.getPolicyById(request.getPolicyId(), userId);
                if (policyOpt.isPresent()) {
                    policy = policyOpt.get();
                } else {
                    Optional<Policy> fallbackOpt = policyService.findById(request.getPolicyId());
                    if (fallbackOpt.isPresent()) {
                        policy = fallbackOpt.get();
                    }
                }
            }

            ScenarioDto.ScenarioResponse response = scenarioEngineService.simulateScenario(
                    request.getQuestion().trim(), policy, request.getLanguage());
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            log.error("Scenario simulation error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<ScenarioDto.ScenarioResponse>err("Scenario simulation failed", e.getMessage()));
        }
    }

    @GetMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> getTtsAudio(
            @RequestParam(defaultValue = "en") String lang,
            @RequestParam String text) {
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String cleanText = text.replaceAll("[*#_~`>]", "")
                    .replaceAll("[✅⚠️❌🏥💡⏱️📋]", "")
                    .replaceAll("₹\\s*", "Rupees ")
                    .trim();
            if (cleanText.length() > 200) {
                cleanText = cleanText.substring(0, 200);
            }
            String url = "https://translate.google.com/translate_tts?ie=UTF-8&tl=" +
                    java.net.URLEncoder.encode(lang, java.nio.charset.StandardCharsets.UTF_8) +
                    "&client=tw-ob&q=" +
                    java.net.URLEncoder.encode(cleanText, java.nio.charset.StandardCharsets.UTF_8);

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(4))
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            java.net.http.HttpResponse<byte[]> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                        .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                        .body(resp.body());
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (Exception e) {
            log.warn("TTS generation error for lang {}: {}", lang, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

