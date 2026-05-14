package com.example.BuildingManagement.device;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceControlService deviceControlService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ResponseEntity<?> getDeviceById(@PathVariable("id") Long id) {
        try {
            IotDevice device = deviceControlService.getDeviceById(id);
            return ResponseEntity.ok(buildDeviceResponse(device));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/room/{roomId}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ResponseEntity<?> getDeviceByRoom(@PathVariable("roomId") Long roomId) {
        try {
            IotDevice device = deviceControlService.getDeviceByRoomId(roomId);
            return ResponseEntity.ok(buildDeviceResponse(device));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> buildDeviceResponse(IotDevice device) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", device.getId());
        response.put("deviceSerial", device.getDeviceSerial());
        response.put("status", device.getStatus());
        response.put("ipAddress", device.getIpAddress());
        response.put("unitRatePerKwh", device.getUnitRatePerKwh());
        response.put("roomId", device.getRoom() != null ? device.getRoom().getId() : null);
        response.put("roomNumber", device.getRoom() != null ? device.getRoom().getRoomNumber() : null);
        return response;
    }

    @PostMapping("/{id}/on")
    @PreAuthorize("hasRole('LANDLORD')")
    public ResponseEntity<Map<String, String>> turnDeviceOn(@PathVariable("id") Long id) {
        boolean success = deviceControlService.turnOn(id);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Device turned ON"));
        } else {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Failed to reach ESP32 or update status"));
        }
    }

    @PostMapping("/{id}/off")
    @PreAuthorize("hasRole('LANDLORD')")
    public ResponseEntity<Map<String, String>> turnDeviceOff(@PathVariable("id") Long id) {
        boolean success = deviceControlService.turnOff(id);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "success", "message", "Device turned OFF"));
        } else {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Failed to reach ESP32 or update status"));
        }
    }

    @PutMapping("/{id}/ip")
    @PreAuthorize("hasRole('LANDLORD')")
    public ResponseEntity<Map<String, String>> updateDeviceIp(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> payload) {
        String ipAddress = payload.get("ipAddress");
        if (ipAddress == null || ipAddress.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ipAddress is required"));
        }

        try {
            deviceControlService.updateIpAddress(id, ipAddress);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Device IP updated to " + ipAddress));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/universal-unit-rate")
    @PreAuthorize("hasRole('LANDLORD')")
    public ResponseEntity<Map<String, String>> updateUniversalUnitRate(@RequestBody Map<String, java.math.BigDecimal> payload) {
        java.math.BigDecimal unitRate = payload.get("unitRate");
        if (unitRate == null || unitRate.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Valid unitRate is required"));
        }

        try {
            deviceControlService.updateUniversalUnitRate(unitRate);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Universal unit rate updated to " + unitRate));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Failed to update universal unit rate: " + e.getMessage()));
        }
    }
}
