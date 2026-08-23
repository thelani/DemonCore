package com.lani.demoncore.detection;

import com.lani.demoncore.compat.SableCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpeedTracker {
    
    private final Map<UUID, Vec3> lastPositions = new ConcurrentHashMap<>();
    private final Map<UUID, Double> speeds = new ConcurrentHashMap<>();
    
    public double updateSpeed(Entity entity) {
        UUID id = entity.getUUID();

        Entity rootVehicle = entity;
        while (rootVehicle.getVehicle() != null) {
            rootVehicle = rootVehicle.getVehicle();
        }

        double sableSpeed = SableCompat.getEntityVelocityMagnitude(entity);
        if (sableSpeed > 0.0) {

            speeds.put(id, sableSpeed);
            lastPositions.put(id, rootVehicle.position()); // Cache güncelle
            return sableSpeed;
        }

        Vec3 currentPos = rootVehicle.position();
        Vec3 lastPos = lastPositions.get(id);
        
        double speed = 0.0;
        
        if (lastPos != null) {

            double distance = currentPos.distanceTo(lastPos);
            speed = distance * 20.0; // distance per tick * 20 ticks/sec = m/s
        }
        
        lastPositions.put(id, currentPos);
        speeds.put(id, speed);
        
        return speed;
    }
    
    public double getSpeed(UUID id) {
        return speeds.getOrDefault(id, 0.0);
    }
    
    public void cleanup(UUID id) {
        lastPositions.remove(id);
        speeds.remove(id);
    }
}
