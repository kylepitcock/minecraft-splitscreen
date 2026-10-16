package org.kylepitcock.splitscreen.mixin.client;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TrackingEmitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Queue;

/** Lets each local player keep their own particles while sharing the one (expensive) particle engine. */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor("level")
    ClientLevel splitscreen$getLevel();

    @Accessor("level")
    void splitscreen$setLevel(ClientLevel level);

    @Accessor("particles")
    Map<ParticleRenderType, ParticleGroup<?>> splitscreen$getParticles();

    @Mutable
    @Accessor("particles")
    void splitscreen$setParticles(Map<ParticleRenderType, ParticleGroup<?>> particles);

    @Accessor("trackingEmitters")
    Queue<TrackingEmitter> splitscreen$getTrackingEmitters();

    @Mutable
    @Accessor("trackingEmitters")
    void splitscreen$setTrackingEmitters(Queue<TrackingEmitter> emitters);

    @Accessor("particlesToAdd")
    Queue<Particle> splitscreen$getParticlesToAdd();

    @Mutable
    @Accessor("particlesToAdd")
    void splitscreen$setParticlesToAdd(Queue<Particle> queue);

    @Accessor("trackedParticleCounts")
    Object2IntOpenHashMap<ParticleLimit> splitscreen$getTrackedParticleCounts();

    @Mutable
    @Accessor("trackedParticleCounts")
    void splitscreen$setTrackedParticleCounts(Object2IntOpenHashMap<ParticleLimit> counts);
}
