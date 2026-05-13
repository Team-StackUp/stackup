package com.stackup.stackup.system.presentation;

import com.stackup.stackup.system.application.SystemHealthService;
import com.stackup.stackup.system.application.dto.SystemHealthResponse;
import com.stackup.stackup.system.application.dto.SystemLiveResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemHealthService systemHealthService;

    public SystemController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @GetMapping("/live")
    public ResponseEntity<SystemLiveResponse> live() {
        return ResponseEntity.ok(systemHealthService.live());
    }

    @GetMapping("/ready")
    public ResponseEntity<SystemHealthResponse> ready() {
        return ResponseEntity.ok(systemHealthService.ready());
    }

    @GetMapping("/health")
    public ResponseEntity<SystemHealthResponse> health() {
        return ResponseEntity.ok(systemHealthService.health());
    }
}
