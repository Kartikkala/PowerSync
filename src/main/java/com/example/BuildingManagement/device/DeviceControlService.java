package com.example.BuildingManagement.device;

import com.example.BuildingManagement.common.enums.DeviceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class DeviceControlService {

    private final IotDeviceRepo iotDeviceRepo;

    /**
     * Get a device by its ID.
     */
    public IotDevice getDeviceById(Long deviceId) {
        return iotDeviceRepo.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));
    }

    /**
     * Get a device by the room it is installed in.
     */
    public IotDevice getDeviceByRoomId(Long roomId) {
        return iotDeviceRepo.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("No device found for room: " + roomId));
    }

    /**
     * Send an HTTP GET request to the ESP32 server to turn ON the electricity.
     */
    public boolean turnOn(Long deviceId) {
        IotDevice device = iotDeviceRepo.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        if (device.getIpAddress() == null || device.getIpAddress().isEmpty()) {
            throw new RuntimeException("Device IP address is not configured");
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "http://" + device.getIpAddress() + ":8081/on";
            
            // Assume the ESP32 returns 200 OK on success
            restTemplate.getForEntity(url, String.class);
            
            device.setStatus(DeviceStatus.ON);
            iotDeviceRepo.save(device);
            System.out.println("✅ Device " + device.getDeviceSerial() + " turned ON successfully.");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to turn ON device " + device.getDeviceSerial() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Send an HTTP GET request to the ESP32 server to turn OFF the electricity.
     */
    public boolean turnOff(Long deviceId) {
        IotDevice device = iotDeviceRepo.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        if (device.getIpAddress() == null || device.getIpAddress().isEmpty()) {
            throw new RuntimeException("Device IP address is not configured");
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "http://" + device.getIpAddress() + ":8081/off";
            
            // Assume the ESP32 returns 200 OK on success
            restTemplate.getForEntity(url, String.class);
            
            device.setStatus(DeviceStatus.OFF);
            iotDeviceRepo.save(device);
            System.out.println("✅ Device " + device.getDeviceSerial() + " turned OFF successfully.");
            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to turn OFF device " + device.getDeviceSerial() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates the IP address of a specific IoT device.
     */
    public IotDevice updateIpAddress(Long deviceId, String ipAddress) {
        IotDevice device = iotDeviceRepo.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("IP address cannot be empty");
        }

        device.setIpAddress(ipAddress.trim());
        iotDeviceRepo.save(device);
        System.out.println("✅ Device " + device.getDeviceSerial() + " IP updated to " + ipAddress);
        return device;
    }

    /**
     * Updates the unit rate globally for all registered IoT devices.
     */
    public void updateUniversalUnitRate(java.math.BigDecimal newRate) {
        java.util.List<IotDevice> allDevices = iotDeviceRepo.findAll();
        for (IotDevice device : allDevices) {
            device.setUnitRatePerKwh(newRate);
        }
        iotDeviceRepo.saveAll(allDevices);
        System.out.println("✅ Universal unit rate updated to " + newRate + " for " + allDevices.size() + " devices.");
    }
}
