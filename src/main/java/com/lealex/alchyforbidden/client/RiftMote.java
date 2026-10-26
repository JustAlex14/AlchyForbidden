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
    public record Style(float gravity, float friction, int minLife, int maxLife, float minSize, float maxSize, float alpha, int color, boolean steady) {
        public Style(float gravity, float friction, int minLife, int maxLife, float minSize, float maxSize, float alpha) {
            this(gravity, friction, minLife, maxLife, minSize, maxSize, alpha, 0xFFFFFF, false);
        }
    }

    public static final Style MOTE = new Style(-0.01F, 0.92F, 20, 34, 0.05F, 0.09F, 1F);
    public static final Style FLECK = new Style(0.05F, 0.94F, 14, 24, 0.04F, 0.07F, 0.95F);
    // the essences on their way: embers rise, tears hang, spores sink
    public static final Style WRATH = new Style(-0.02F, 0.9F, 14, 26, 0.06F, 0.1F, 1F);
    public static final Style SORROW = new Style(-0.003F, 0.95F, 22, 36, 0.07F, 0.11F, 0.85F);
    public static final Style CORRUPTION = new Style(0.008F, 0.93F, 18, 30, 0.06F, 0.1F, 1F);
    // the carved wings: steady (no fade, no growth), no drag, three ticks, tinted dust
    public static final Style ECHO_EDGE = new Style(0F, 1F, 3, 3, 0.07F, 0.095F, 0.95F, 0x9FF2F8, true);
    public static final Style ECHO_FEATHER = new Style(0F, 1F, 3, 3, 0.06F, 0.085F, 0.85F, 0x3FC6D0, true);
    public static final Style CHAIN_EDGE = new Style(0F, 1F, 3, 3, 0.07F, 0.095F, 0.95F, 0xC8C4D4, true);
    public static final Style CHAIN_FEATHER = new Style(0F, 1F, 3, 3, 0.06F, 0.085F, 0.9F, 0x8C8696, true);

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
        if (style.steady()) {
            this.alpha = 0F; // hidden its first tick (it is spawned a tick ahead of the player it rides: client/InscriptionVisuals)
            setSprite(sprites.get(random)); // one of the dust's sprites, kept
            this.rCol = (style.color() >> 16 & 0xFF) / 255F * (0.85F + random.nextFloat() * 0.15F);
            this.gCol = (style.color() >> 8 & 0xFF) / 255F * (0.85F + random.nextFloat() * 0.15F);
            this.bCol = (style.color() & 0xFF) / 255F * (0.85F + random.nextFloat() * 0.15F);
        } else {
            setSpriteFromAge(sprites);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        if (style.steady()) {
            alpha = style.alpha();
            return;
        }
        setSpriteFromAge(sprites);
        float life = (float) age / lifetime;
        alpha = style.alpha() * (life < 0.7F ? 1F : 1F - (life - 0.7F) / 0.3F);
    }

    @Override
    public float getQuadSize(float partial) {
        if (style.steady()) return baseSize;
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
        event.registerSpriteSet(ModRegistries.WRATH_MOTE.get(), sprites -> new Provider(sprites, WRATH));
        event.registerSpriteSet(ModRegistries.SORROW_MOTE.get(), sprites -> new Provider(sprites, SORROW));
        event.registerSpriteSet(ModRegistries.CORRUPTION_MOTE.get(), sprites -> new Provider(sprites, CORRUPTION));
    }
}
