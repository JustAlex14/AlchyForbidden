package com.lealex.alchyforbidden.compat.jade;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.familiar.RiftBeast;
import net.minecraft.resources.Identifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade integration (only loaded when Jade is installed: Jade finds this class by its @WailaPlugin annotation).
 *
 * Names the rift's creatures in their own colours (grade after the name, great ones in bold, a familiar's own name in
 * its grade's colour). Jade's own title strips every colour, so the name line is put back as the creature gives it.
 */
@WailaPlugin
public class AlchyForbiddenJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(CreatureName.INSTANCE, RiftBeast.class);
    }

    public enum CreatureName implements IEntityComponentProvider {
        INSTANCE;

        private static final Identifier UID = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "creature_name");

        @Override
        public Identifier getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            tooltip.replace(JadeIds.CORE_OBJECT_NAME, accessor.getEntity().getDisplayName().copy());
        }
    }
}
