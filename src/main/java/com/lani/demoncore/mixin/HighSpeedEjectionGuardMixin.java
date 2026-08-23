package com.lani.demoncore.mixin;

import com.lani.demoncore.compat.SableCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = PersistentEntitySectionManager.class, priority = 500)
public abstract class HighSpeedEjectionGuardMixin {

    @Shadow
    @Final
    public EntitySectionStorage<EntityAccess> sectionStorage;

    @Inject(method = "processChunkUnload", at = @At("HEAD"), cancellable = true)
    private void demoncore$guardHighSpeedPassengers(final long chunkLong, final CallbackInfoReturnable<Boolean> cir) {
        if (!SableCompat.isSableLoaded()) return;
        
        final List<EntitySection<EntityAccess>> sections = this.sectionStorage
                .getExistingSectionsInChunk(chunkLong)
                .toList();

        for (final EntitySection<EntityAccess> section : sections) {
            final List<EntityAccess> entities = section.getEntities().toList();

            for (final EntityAccess entityAccess : entities) {
                final Entity entity = ((Entity) entityAccess);

                if (!SableCompat.isEntityInSubLevel(entity)) continue;

                if (!entity.isVehicle()) continue;
                
                boolean hasPlayerPassenger = false;
                for (Entity passenger : entity.getPassengers()) {
                    if (passenger instanceof Player) {
                        hasPlayerPassenger = true;
                        break;
                    }
                }
                
                if (hasPlayerPassenger) {

                    cir.setReturnValue(false); // false = chunk NOT unloaded
                    return;
                }
            }
        }
    }
}
