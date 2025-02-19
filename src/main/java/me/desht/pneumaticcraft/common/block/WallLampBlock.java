package me.desht.pneumaticcraft.common.block;

import me.desht.pneumaticcraft.client.ColorHandlers;
import me.desht.pneumaticcraft.client.util.TintColor;
import me.desht.pneumaticcraft.common.item.ICustomTooltipName;
import me.desht.pneumaticcraft.common.registry.ModBlocks;
import me.desht.pneumaticcraft.common.registry.ModItems;
import me.desht.pneumaticcraft.common.util.VoxelShapeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.function.ToIntFunction;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;

public class WallLampBlock extends AbstractPneumaticCraftBlock implements ColorHandlers.ITintableBlock, SimpleWaterloggedBlock {
    private static final VoxelShape SHAPE_UP = VoxelShapeUtils.or(
            Block.box(3, 0, 3, 13, 1, 13),
            Block.box(4, 1, 4, 12, 2, 12),
            Block.box(6.15, 2.25, 4.75, 6.65, 3.5, 11.25),
            Block.box(5, 2.25, 5, 11, 3.25, 11),
            Block.box(4.75, 1.25, 4.75, 11.25, 2.5, 11.25),
            Block.box(9.35, 2.25, 4.75, 9.85, 3.5, 11.25),
            Block.box(4.75, 2.25, 9.35, 11.25, 3.5, 9.85),
            Block.box(4.75, 2.25, 6.15, 11.25, 3.5, 6.65)
    );
    private static final VoxelShape SHAPE_NORTH = VoxelShapeUtils.rotateX(SHAPE_UP, 270);
    private static final VoxelShape SHAPE_DOWN = VoxelShapeUtils.rotateX(SHAPE_NORTH, 270);
    private static final VoxelShape SHAPE_SOUTH = VoxelShapeUtils.rotateX(SHAPE_UP, 90);
    private static final VoxelShape SHAPE_WEST = VoxelShapeUtils.rotateY(SHAPE_NORTH, 270);
    private static final VoxelShape SHAPE_EAST = VoxelShapeUtils.rotateY(SHAPE_NORTH, 90);
    private static final VoxelShape[] SHAPES = { SHAPE_DOWN, SHAPE_UP, SHAPE_NORTH, SHAPE_SOUTH, SHAPE_WEST, SHAPE_EAST };

    private static final int[] COLORS_ON = new int[DyeColor.values().length];
    private static final int[] COLORS_OFF = new int[DyeColor.values().length];

    static {
        for (DyeColor c : DyeColor.values()) {
            TintColor tc = new TintColor(FastColor.ARGB32.color(255, c.getTextureDiffuseColor()));
            COLORS_ON[c.getId()] = tc.getARGB();
            COLORS_OFF[c.getId()] = tc.darker().getARGB();
        }
    }

    private final DyeColor color;
    private final boolean inverted;

    public WallLampBlock(Properties props, DyeColor color, boolean inverted) {
        super(props);

        this.color = color;
        this.inverted = inverted;

        registerDefaultState(defaultBlockState().setValue(LIT, inverted));
    }

    public static Properties wallLampProperties() {
        return ModBlocks.defaultProps().lightLevel(getLightValue());
    }

    @Override
    protected boolean isWaterloggable() {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);

        builder.add(LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(directionProperty()).get3DDataValue()];
    }

    @Override
    public void tick(BlockState state, ServerLevel worldIn, BlockPos pos, RandomSource rand) {
        if (state.getValue(LIT) && !shouldLight(worldIn, pos)) {
            worldIn.setBlock(pos, state.cycle(LIT), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        if (!worldIn.isClientSide) {
            boolean isLit = state.getValue(LIT);
            if (isLit != shouldLight(worldIn, pos)) {
                if (isLit) {
                    worldIn.scheduleTick(pos, this, 4);
                } else {
                    worldIn.setBlock(pos, state.cycle(LIT), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        return state
                .setValue(directionProperty(), context.getClickedFace())
                .setValue(LIT, shouldLight(context.getLevel(), context.getClickedPos()));
    }

    @Override
    public BlockState updateShape(BlockState stateIn, Direction facing, BlockState facingState, LevelAccessor worldIn, BlockPos currentPos, BlockPos facingPos) {
        return getRotation(stateIn).getOpposite() == facing && !stateIn.canSurvive(worldIn, currentPos) ?
                Blocks.AIR.defaultBlockState() :
                super.updateShape(stateIn, facing, facingState, worldIn, currentPos, facingPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
        return FaceAttachedHorizontalDirectionalBlock.canAttach(worldIn, pos, getRotation(state).getOpposite());
    }

    @Override
    public boolean isRotatable() {
        return true;
    }

    @Override
    protected boolean canRotateToTopOrBottom() {
        return true;
    }

    @Override
    public int getTintColor(BlockState state, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos, int tintIndex) {
        if (tintIndex == 1 && state != null) {
            return state.getValue(LIT) ? COLORS_ON[color.getId()] : COLORS_OFF[color.getId()];
        }
        return 0xFFFFFFFF;
    }

    private boolean shouldLight(Level world, BlockPos pos) {
        return inverted != world.hasNeighborSignal(pos);
    }

    private static ToIntFunction<BlockState> getLightValue() {
        return state -> state.getValue(LIT) ? 15 : 0;
    }

    public static class ItemWallLamp extends BlockItem implements ICustomTooltipName {
        public ItemWallLamp(WallLampBlock blockWallLamp) {
            super(blockWallLamp, ModItems.defaultProps());
        }

        @Override
        public String getCustomTooltipTranslationKey() {
            if (getBlock() instanceof WallLampBlock bwl) {
                return bwl.inverted ? "block.pneumaticcraft.wall_lamp_inverted" : "block.pneumaticcraft.wall_lamp";
            } else {
                // shouldn't happen
                return "block.pneumaticcraft.wall_lamp";
            }
        }
    }
}
