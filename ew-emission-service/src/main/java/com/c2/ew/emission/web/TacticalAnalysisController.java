package com.c2.ew.emission.web;

import com.c2.ew.emission.infrastructure.seeder.MassDataSeederService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/tactical/analysis", "/api/tactical/analysis"})
public class TacticalAnalysisController {

    private final MassDataSeederService massDataSeederService;

    public TacticalAnalysisController(MassDataSeederService massDataSeederService) {
        this.massDataSeederService = massDataSeederService;
    }

    /**
     * Büyük Ölçekli Taktik Veri Üretici (Stress Test & Büyük Veri Seeder)
     */
    @PostMapping("/seed-massive")
    public ResponseEntity<Map<String, Object>> seedMassive(
        @RequestParam(defaultValue = "200000") int radarCount,
        @RequestParam(defaultValue = "200000") int comintCount
    ) {
        Map<String, Object> result = massDataSeederService.seedMassiveData(radarCount, comintCount);
        return ResponseEntity.ok(result);
    }
}
