package com.lealex.alchyforbidden.block;

import com.lealex.alchyforbidden.block.entity.RelayBlockEntity;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Echo Obelisk: a lantern of rift glass with a violet crystal burning inside, always upright, on a mount that
 * changes with where it is placed. On a floor it is two blocks: a stone foot and pole (HALF lower, the block entity:
 * its link) with the lantern standing on it above (HALF upper). On a ceiling or a wall it is one block (always HALF
 * lower): a smaller lantern hanging from a plate and rod, or from a bracket whose arm reaches out of the wall. FACING is
 * the face clicked (up, down, or the way out of the wall). It carries Wrath, Sorrow and Sculk Corruption one link
 * further (essence/Relays), attuned with the Attuning Fork.
 */
public class RelayBlock extends Block implements EntityBlock {
    public static final MapCodec<RelayBlock> CODEC = simpleCodec(RelayBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** The mounts' shapes (x0, y0, z0, x1, y1, z1); the wall bracket is drawn for a wall to the south (FACING north). */
    private static final double[][] FOOT_FLOOR = {{2, 0, 2, 14, 2, 14}, {4, 2, 4, 12, 4, 12}, {6, 4, 6, 10, 16, 10}};
    private static final double[][] FOOT_CEILING = {{5, 15, 5, 11, 16, 11}, {7, 12, 7, 9, 15, 9}, {3.5, 2, 3.5, 12.5, 12, 12.5}};
    private static final double[][] FOOT_WALL = {{6, 7, 15, 10, 15, 16}, {7, 13, 7, 9, 14, 15}, {7, 11, 7, 9, 13, 9}, {3.5, 1, 3.5, 12.5, 11, 12.5}};
    private static final double[][] LANTERN_STANDING = {{6, 0, 6, 10, 2, 10}, {2.5, 2, 2.5, 13.5, 14, 13.5}, {6, 14, 6, 10, 16, 10}};
    private static final Map<Direction, VoxelShape> FOOT_SHAPES = new EnumMap<>(Direction.class);
    private static final VoxelShape STANDING = shape(LANTERN_STANDING, Direction.NORTH);

    static {
        FOOT_SHAPES.put(Direction.UP, shape(FOOT_FLOOR, Direction.NORTH));
        FOOT_SHAPES.put(Direction.DOWN, shape(FOOT_CEILING, Direction.NORTH));
        for (Direction facing : Direction.Plane.HORIZONTAL) FOOT_SHAPES.put(facing, shape(FOOT_WALL, facing));
    }

    /** The boxes, turned about the vertical axis from facing north to `facing` (as the blockstate turns the model). */
    private static VoxelShape shape(double[][] boxes, Direction facing) {
        VoxelShape shape = Shapes.empty();
        for (double[] b : boxes) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            double[] t = switch (facing) {
                case EAST -> new double[] {16 - z1, 16 - z0, x0, x1};
                case SOUTH -> new double[] {16 - x1, 16 - x0, 16 - z1, 16 - z0};
                case WEST -> new double[] {z0, z1, 16 - x1, 16 - x0};
                default -> new double[] {x0, x1, z0, z1};
            };
            shape = Shapes.or(shape, Block.box(t[0], b[1], t[2], t[1], b[4], t[3]));
        }
        return shape;
    }

    /** Only standing on a floor is it two blocks (the lantern above its pole); hanging, mount and lantern share one. */
    public static boolean tall(Direction facing) {
        return facing == Direction.UP;
    }

    public RelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? FOOT_SHAPES.get(facing) : STANDING;
    }

    /** Where the obelisk's foot (and its block entity) is, from either half. */
    public static BlockPos foot(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** The block its lantern is in, given its mount. */
    public static BlockPos top(BlockGetter level, BlockPos foot) {
        BlockState state = level.getBlockState(foot);
        return !state.hasProperty(FACING) || tall(state.getValue(FACING)) ? foot.above() : foot;
    }

    /** Where its crystal is drawn from and streams leave from (the middle of the crystal), given its foot. */
    public static Vec3 crystal(BlockGetter level, BlockPos foot) {
        BlockState state = level.getBlockState(foot);
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.UP;
        // the crystal: a little high in the standing lantern, lower in the small hanging ones
        double y = tall(facing) ? 0.06 : facing == Direction.DOWN ? -0.06 : -0.12;
        return Vec3.atCenterOf(top(level, foot)).add(0, y, 0);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        Level level = context.getLevel();
        if (tall(facing)) { // standing: the lantern needs the block above
            BlockPos top = context.getClickedPos().above();
            if (level.isOutsideBuildHeight(top) || !level.getBlockState(top).canBeReplaced(context)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (tall(state.getValue(FACING))) level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction facing = state.getValue(FACING);
        // the other half gone: this one goes too (no drop: the loot comes from the foot only)
        if (tall(facing) && direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)
                && !(neighbourState.is(this) && neighbourState.getValue(HALF) != half && neighbourState.getValue(FACING) == facing)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            // broken by its crystal end: the foot drops the obelisk (in survival) and goes without a second drop
            BlockPos footPos = foot(state, pos);
            BlockState foot = level.getBlockState(footPos);
            if (foot.is(this) && foot.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (!player.preventsBlockDrops()) dropResources(foot, level, footPos, level.getBlockEntity(footPos), player, player.getMainHandItem());
                level.setBlock(footPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, footPos, Block.getId(foot));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new RelayBlockEntity(pos, state) : null;
    }

    /** Motes drifting off the crystal. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean lantern = state.getValue(HALF) == DoubleBlockHalf.UPPER || !tall(state.getValue(FACING)); // (a hanging one is its own lantern)
        if (!lantern || random.nextInt(3) != 0 || !com.lealex.alchyx.AlchyXClientConfig.spawn(random)) return;
        Vec3 c = Vec3.atCenterOf(pos);
        level.addParticle(ModRegistries.RIFT_MOTE.get(), c.x + (random.nextDouble() - 0.5) * 0.6, c.y + (random.nextDouble() - 0.5) * 0.6,
                c.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
    }
}
