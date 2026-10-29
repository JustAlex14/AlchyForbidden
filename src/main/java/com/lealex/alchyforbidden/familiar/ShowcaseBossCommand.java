package com.lealex.alchyforbidden.familiar;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /alchyforbidden showcase boss <creature> <pos> [hold]}: a rift creature makes its entrance for the player who
 * runs it (the camera, its name), then holds still `hold` ticks (default 30) and goes back into the rift. It never
 * fights and is never saved. {@code showcase arena} takes the player into a void arena of their own (the trailer's
 * entrances are staged there), {@code showcase return} brings them back. For trailers (AlchyX trailer commands).
 */
public final class ShowcaseBossCommand {
    private ShowcaseBossCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("alchyforbidden")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("showcase").then(Commands.literal("arena").executes(c -> {
                            ServerPlayer player = c.getSource().getPlayer();
                            return player != null && VoidTrials.showcaseArena(player) ? 1 : 0;
                        }))
                        .then(Commands.literal("return").executes(c -> {
                            ServerPlayer player = c.getSource().getPlayer();
                            if (player != null) VoidTrials.showcaseReturn(player);
                            return 1;
                        }))
                        .then(Commands.literal("boss")
                        .then(Commands.argument("creature", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(RiftCreature.values()).map(RiftCreature::id), b))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(c -> boss(c, 30))
                                        .then(Commands.argument("hold", IntegerArgumentType.integer(0, 600))
                                                .executes(c -> boss(c, IntegerArgumentType.getInteger(c, "hold")))))))));
    }

    private static int boss(CommandContext<CommandSourceStack> context, int hold) {
        RiftCreature creature = RiftCreature.byId(StringArgumentType.getString(context, "creature"));
        if (creature == null) {
            context.getSource().sendFailure(Component.literal("No rift creature called " + StringArgumentType.getString(context, "creature")));
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        ServerPlayer viewer = context.getSource().getPlayer();
        Vec3 at = Vec3Argument.getVec3(context, "pos");
        RiftBeast beast = creature.type().create(level, EntitySpawnReason.COMMAND);
        if (beast == null) return 0;
        beast.snapTo(at.x, at.y, at.z, 0F, 0F);
        beast.setPersistenceRequired();
        level.addFreshEntity(beast);
        if (viewer != null) beast.showcase(viewer, hold);
        return 1;
    }
}
