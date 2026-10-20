package com.c2.ew.copilot.web;

import com.c2.ew.copilot.dto.TacticalQueryRequest;
import com.c2.ew.copilot.dto.TacticalResponseDto;
import com.c2.ew.copilot.service.TacticalAgentService;
import com.c2.ew.copilot.tools.ComintEmissionTools;
import com.c2.ew.copilot.tools.RadarEmissionTools;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1", "/api/v1/tactical", "/api"})
public class CopilotChatController {

    private final TacticalAgentService agentService;

    public CopilotChatController(TacticalAgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * Taktik AI Sohbet & Doğal Dil Karar Destek Terminali
     */
    @PostMapping({"/chat", "/tactical/chat", "/analyze", "/tactical/analyze"})
    public ResponseEntity<TacticalResponseDto> chat(@RequestBody TacticalQueryRequest request) {
        return ResponseEntity.ok(agentService.processTacticalQuery(request));
    }

    /**
     * Son çağrılan @Tool loglarını döner
     */
    @GetMapping({"/chat/logs", "/logs"})
    public ResponseEntity<List<String>> getToolLogs() {
        List<String> combined = new ArrayList<>();
        combined.addAll(RadarEmissionTools.drainCallLogs());
        combined.addAll(ComintEmissionTools.drainCallLogs());
        return ResponseEntity.ok(combined);
    }

    /**
     * Sağlık ve Hazırlık Durumu
     */
    @GetMapping({"/health", "/chat/health"})
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "service", "ew-copilot-service",
            "status", "UP",
            "ai_engine", "Spring AI + Google Gemini",
            "architecture", "Decoupled DTO + REST Client"
        ));
    }
}
