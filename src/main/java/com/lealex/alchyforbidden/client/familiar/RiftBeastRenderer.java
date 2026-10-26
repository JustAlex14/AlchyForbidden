package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.familiar.RiftBeast;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws any creature of the rift from its model and three pictures named after it (textures/entity/<id>.png, _glow,
 * _rift): its skin; what glows (an eyes pass, violet once altered); for the altered, the rift's cracks over it (an
 * emissive pass, stronger by grade, breathing); tinted toward violet and larger by grade; an echo dark. Every creature uses it with its own model, size and scale.
 */
public class RiftBeastRenderer<T extends RiftBeast> extends MobRenderer<T, RiftBeastRenderState, EntityModel<RiftBeastRenderState>> {
    private final Identifier skin;
    private final RenderType glow, cracks;
    private final float baseScale;

    public RiftBeastRenderer(EntityRendererProvider.Context context, EntityModel<RiftBeastRenderState> model, float shadow, String id, float baseScale) {
        super(context, model, shadow);
        this.baseScale = baseScale;
        this.skin = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/" + id + ".png");
        this.glow = RenderTypes.eyes(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/" + id + "_glow.png"));
        this.cracks = RenderTypes.entityTranslucentEmissive(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/" + id + "_rift.png"));
        addLayer(new RenderLayer<>(this) {
            @Override
            public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, RiftBeastRenderState state, float yRot, float xRot) {
                int eyes = state.corruption >= 1 ? 0xFFD0A0FF : -1; // the altered ones' light goes violet
                collector.order(1).submitModel(getParentModel(), state, poseStack, glow, light, OverlayTexture.NO_OVERLAY, eyes, null, state.outlineColor, null);
                if (state.corruption > 0) {
                    float breath = 0.75F + 0.25F * (float) Math.sin(state.ageInTicks * 0.15F);
                    int alpha = Math.round(255 * Math.min(1F, (0.35F + 0.22F * state.corruption) * breath));
                    collector.order(2).submitModel(getParentModel(), state, poseStack, cracks, light, OverlayTexture.NO_OVERLAY,
                            alpha << 24 | 0xFFFFFF, null, 0, null);
                }
            }
        });
    }

    /** A small shadow for the small form (the game sets the shadow in finalizeRenderState, after extractRenderState). */
    @Override
    protected float getShadowRadius(RiftBeastRenderState state) {
        return super.getShadowRadius(state) * net.minecraft.util.Mth.lerp(state.dormant, 1F, state.dormantScale);
    }

    @Override
    public RiftBeastRenderState createRenderState() {
        return new RiftBeastRenderState();
    }

    @Override
    public void extractRenderState(T entity, RiftBeastRenderState state, float partial) {
        super.extractRenderState(entity, state, partial);
        float length = entity.swingLength();
        state.swing = entity.swingTicks <= 0 ? 0 : (float) Math.sin(Math.min(1F, (entity.swingTicks - partial) / length) * Math.PI);
        state.swingTime = entity.swingTicks <= 0 ? 0 : 1F - Math.min(1F, Math.max(0F, (entity.swingTicks - partial) / length));
        state.corruption = entity.corruption();
        state.phase = entity.phase();
        state.phaseTime = entity.clientPhaseTicks + partial;
        state.intro = entity.clientIntroTicks < 0 ? -1 : entity.clientIntroTicks + partial;
        state.special = entity.specialShown();
        state.specialTime = entity.clientSpecialTicks + partial;
        state.visual = entity.visual();
        state.dormant = net.minecraft.util.Mth.lerp(partial, entity.dormantAnimO, entity.dormantAnim);
        state.dormantScale = entity.dormantScale();
        state.familiar = entity.familiar();
        state.echo = entity.echo();
        float left = Math.max(0, entity.petTicks - partial), into = RiftBeast.PET_TICKS - left;
        state.petTime = into;
        state.pet = entity.petTicks <= 0 ? 0 : Math.min(1F, Math.min(into / 6F, left / 8F));
        state.move = entity.clientMove;
        state.moveTime = entity.clientMove < 0 ? 0 : entity.clientMoveTicks + partial;
    }

    @Override
    public Identifier getTextureLocation(RiftBeastRenderState state) {
        return skin;
    }

    @Override
    protected int getModelTint(RiftBeastRenderState state) {
        if (state.echo) return 0xFF6A5A8A;
        return switch (state.corruption) {
            case 1 -> 0xFFE8DCFF;
            case 2 -> 0xFFD2BCFF;
            case 3 -> 0xFFB896FF;
            default -> -1;
        };
    }

    @Override
    protected void scale(RiftBeastRenderState state, PoseStack poseStack) {
        float s = baseScale * (1F + 0.08F * state.corruption) * net.minecraft.util.Mth.lerp(state.dormant, 1F, state.dormantScale);
        poseStack.scale(s, s, s);
    }
}
