/*
 * This file is part of pnc-repressurized.
 *
 *     pnc-repressurized is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     pnc-repressurized is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with pnc-repressurized.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.desht.pneumaticcraft.common.hacking.block;

import me.desht.pneumaticcraft.api.pneumatic_armor.hacking.IHackableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static me.desht.pneumaticcraft.api.PneumaticRegistry.RL;
import static me.desht.pneumaticcraft.common.util.PneumaticCraftUtils.xlate;

public class HackableMobSpawner implements IHackableBlock {
    private static final ResourceLocation ID = RL("mob_spawner");

    @Override
    public ResourceLocation getHackableId() {
        return ID;
    }

    @Override
    public boolean canHack(BlockGetter level, BlockPos pos, BlockState state, Player player) {
        return !isHacked(level, pos);
    }

    public static boolean isHacked(BlockGetter world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner && spawner.getSpawner().requiredPlayerRange == 0;
    }

    @Override
    public void addInfo(BlockGetter world, BlockPos pos, List<Component> curInfo, Player player) {
        curInfo.add(xlate("pneumaticcraft.armor.hacking.result.neutralize"));
    }

    @Override
    public void addPostHackInfo(BlockGetter world, BlockPos pos, List<Component> curInfo, Player player) {
        curInfo.add(xlate("pneumaticcraft.armor.hacking.finished.neutralized"));
    }

    @Override
    public int getHackTime(BlockGetter world, BlockPos pos, Player player) {
        return 200;
    }

    @Override
    public void onHackComplete(Level world, BlockPos pos, Player player) {
        if (!world.isClientSide) {
            BlockEntity te = world.getBlockEntity(pos);
            if (te != null) {
                CompoundTag tag = te.saveWithFullMetadata();
                tag.putShort("RequiredPlayerRange", (short) 0);
                te.load(tag);
                BlockState state = world.getBlockState(pos);
                world.sendBlockUpdated(pos, state, state, 3);
            }
        }

    }

    @Override
    public boolean afterHackTick(BlockGetter world, BlockPos pos) {
        BaseSpawner spawner = ((SpawnerBlockEntity) world.getBlockEntity(pos)).getSpawner();
        spawner.oSpin = spawner.spin;
        spawner.spawnDelay = 10;
        return false;
    }
}
