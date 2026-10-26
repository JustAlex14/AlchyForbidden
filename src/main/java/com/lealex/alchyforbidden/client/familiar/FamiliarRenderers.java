package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.registry.ModRegistries;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Every creature of the rift's model layer and renderer (each the shared RiftBeastRenderer with its own model). */
public final class FamiliarRenderers {
    private FamiliarRenderers() {}

    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SculkCrawlerModel.LAYER, SculkCrawlerModel::createBodyLayer);
        event.registerLayerDefinition(VeilWispModel.LAYER, VeilWispModel::createBodyLayer);
        event.registerLayerDefinition(ShardHoundModel.LAYER, ShardHoundModel::createBodyLayer);
        event.registerLayerDefinition(ShackledBruteModel.LAYER, ShackledBruteModel::createBodyLayer);
        event.registerLayerDefinition(HollowStalkerModel.LAYER, HollowStalkerModel::createBodyLayer);
        event.registerLayerDefinition(AbyssalKnightModel.LAYER, AbyssalKnightModel::createBodyLayer);
        event.registerLayerDefinition(TheChoirModel.LAYER, TheChoirModel::createBodyLayer);
        event.registerLayerDefinition(WeepingChandelierModel.LAYER, WeepingChandelierModel::createBodyLayer);
        event.registerLayerDefinition(VoidGolemModel.LAYER, VoidGolemModel::createBodyLayer);
        event.registerLayerDefinition(BoneDragonModel.LAYER, BoneDragonModel::createBodyLayer);
        event.registerLayerDefinition(SeveredSeraphModel.LAYER, SeveredSeraphModel::createBodyLayer);
    }

    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.SCULK_CRAWLER.get(),
                context -> new RiftBeastRenderer<>(context, new SculkCrawlerModel(context.bakeLayer(SculkCrawlerModel.LAYER)), 0.6F, "sculk_crawler", 1.0F));
        event.registerEntityRenderer(ModRegistries.VEIL_WISP.get(),
                context -> new RiftBeastRenderer<>(context, new VeilWispModel(context.bakeLayer(VeilWispModel.LAYER)), 0.4F, "veil_wisp", 1.0F));
        event.registerEntityRenderer(ModRegistries.SHARD_HOUND.get(),
                context -> new RiftBeastRenderer<>(context, new ShardHoundModel(context.bakeLayer(ShardHoundModel.LAYER)), 0.5F, "shard_hound", 1.0F));
        event.registerEntityRenderer(ModRegistries.SHACKLED_BRUTE.get(),
                context -> new RiftBeastRenderer<>(context, new ShackledBruteModel(context.bakeLayer(ShackledBruteModel.LAYER)), 0.9F, "shackled_brute", 1.2F));
        event.registerEntityRenderer(ModRegistries.HOLLOW_STALKER.get(),
                context -> new RiftBeastRenderer<>(context, new HollowStalkerModel(context.bakeLayer(HollowStalkerModel.LAYER)), 0.5F, "hollow_stalker", 1.0F));
        event.registerEntityRenderer(ModRegistries.ABYSSAL_KNIGHT.get(),
                context -> new RiftBeastRenderer<>(context, new AbyssalKnightModel(context.bakeLayer(AbyssalKnightModel.LAYER)), 1.0F, "abyssal_knight", 1.6F));
        event.registerEntityRenderer(ModRegistries.THE_CHOIR.get(),
                context -> new RiftBeastRenderer<>(context, new TheChoirModel(context.bakeLayer(TheChoirModel.LAYER)), 0.8F, "the_choir", 1.4F));
        event.registerEntityRenderer(ModRegistries.WEEPING_CHANDELIER.get(),
                context -> new RiftBeastRenderer<>(context, new WeepingChandelierModel(context.bakeLayer(WeepingChandelierModel.LAYER)), 1.0F, "weeping_chandelier", 2.0F));
        event.registerEntityRenderer(ModRegistries.VOID_GOLEM.get(),
                context -> new RiftBeastRenderer<>(context, new VoidGolemModel(context.bakeLayer(VoidGolemModel.LAYER)), 1.6F, "void_golem", 2.4F));
        event.registerEntityRenderer(ModRegistries.BONE_DRAGON.get(),
                context -> new RiftBeastRenderer<>(context, new BoneDragonModel(context.bakeLayer(BoneDragonModel.LAYER)), 1.6F, "bone_dragon", 2.6F));
        event.registerEntityRenderer(ModRegistries.SEVERED_SERAPH.get(),
                context -> new RiftBeastRenderer<>(context, new SeveredSeraphModel(context.bakeLayer(SeveredSeraphModel.LAYER)), 1.0F, "severed_seraph", 1.5F));
    }
}
