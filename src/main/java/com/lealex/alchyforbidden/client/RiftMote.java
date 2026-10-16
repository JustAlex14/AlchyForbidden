package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.registry.ModRegistries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * AlchyForbidden's particles (registry/ModRegistries, sprites in textures/particle from tools/textures.py): the rift's
 * violet motes and the sculk's teal flecks. Animated through their sprites by age, glowing in the dark, fading and
 * shrinking at the end of their life.
 */
public class RiftMote extends SingleQuadParticle {

    /** How a kind moves: gravity (+ falls, - rises), friction (speed kept per tick), lifetime and size ranges. */
    public record Style(float gravity, float friction, int minLife, int maxLife, float minSize, float maxSize, float alpha) {}

    public static final Style MOTE = new Style(-0.01F, 0.92F, 20, 34, 0.05F, 0.09F, 1F);
    public static final Style FLECK = new Style(0.05F, 0.94F, 14, 24, 0.04F, 0.07F, 0.95F);

    private final SpriteSet sprites;
    private final Style style;
    private final float baseSize;

    protected RiftMote(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Style style) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.style = style;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = style.gravity();
        this.friction = style.friction();
        this.hasPhysics = false;
        this.lifetime = style.minLife() + random.nextInt(Math.max(1, style.maxLife() - style.minLife() + 1));
        this.baseSize = style.minSize() + random.nextFloat() * (style.maxSize() - style.minSize());
        this.quadSize = baseSize;
        this.alpha = style.alpha();
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        setSpriteFromAge(sprites);
        float life = (float) age / lifetime;
        alpha = style.alpha() * (life < 0.7F ? 1F : 1F - (life - 0.7F) / 0.3F);
    }

    @Override
    public float getQuadSize(float partial) {
        float life = (age + partial) / lifetime;
        float grow = Mth.clamp(life * 6F, 0F, 1F);
        float shrink = life < 0.6F ? 1F : 1F - (life - 0.6F) / 0.4F * 0.7F;
        return baseSize * grow * shrink;
    }

    @Override
    public int getLightCoords(float partial) {
        return LightCoordsUtil.FULL_BRIGHT;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new RiftMote(level, x, y, z, xd, yd, zd, sprites, style);
        }
    }

    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModRegistries.RIFT_MOTE.get(), sprites -> new Provider(sprites, MOTE));
        event.registerSpriteSet(ModRegistries.SCULK_FLECK.get(), sprites -> new Provider(sprites, FLECK));
    }
}
