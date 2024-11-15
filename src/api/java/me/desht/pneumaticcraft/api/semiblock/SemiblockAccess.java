package me.desht.pneumaticcraft.api.semiblock;

import me.desht.pneumaticcraft.api.PneumaticRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Methods to retrieve an {@link ISemiBlock} by level and block position, server-side only.
 * <br>
 * Get an instance of this interface via {@link PneumaticRegistry.IPneumaticCraftInterface#getSemiblockAccess()}.
 */
public interface SemiblockAccess {
    /**
     * {@return the non-directional semiblock entity at the given level and position}
     *
     * @param level the level
     * @param pos   the block
     * @return the semiblock at the given pos, or null if none was found, or the blockpos in question isn't loaded
     */
    ISemiBlock getSemiblock(Level level, BlockPos pos);

    /**
     * {@return the semiblock at the given level, position and face} A null face checks for the non-directional
     * semiblock the given position; a non-null face checks for a directional semiblock
     * (see {@link IDirectionalSemiblock}).
     *
     * @param level     the level
     * @param pos       the blockpos
     * @param direction face of the blockpos, or null for the block itself
     * @return the semiblock, or null if none was found, or the blockpos in question isn't loaded
     */
    ISemiBlock getSemiblock(Level level, BlockPos pos, @Nullable Direction direction);

    /**
     * {@return a stream of all the semiblocks at the given position}
     *
     * @param world the level
     * @param pos   the blockpos
     */
    Stream<ISemiBlock> getAllSemiblocks(Level world, BlockPos pos);

    /**
     * {@return a stream of all the semiblocks at the given position}
     * If there's nothing at the given position, try the position offset by one block in the given direction.
     *
     * @param level     the world
     * @param pos       the blockpos
     * @param offsetDir a direction to offset if needed
     * @return a stream of all the semiblocks at the given position
     */
    Stream<ISemiBlock> getAllSemiblocks(Level level, BlockPos pos, Direction offsetDir);
}
