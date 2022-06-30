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

package me.desht.pneumaticcraft.common.hacking.entity;

import me.desht.pneumaticcraft.api.lib.Names;
import me.desht.pneumaticcraft.api.pneumatic_armor.hacking.AbstractPersistentEntityHack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static me.desht.pneumaticcraft.api.PneumaticRegistry.RL;

public class HackableGhast extends AbstractPersistentEntityHack<Ghast> {
    private static final ResourceLocation ID = RL("ghast");

    public HackableGhast() {
        super(StockHackTypes.DISARM);
    }

    @Override
    public ResourceLocation getHackableId() {
        return ID;
    }

    @Override
    public Class<Ghast> getHackableClass() {
        return Ghast.class;
    }

    @Mod.EventBusSubscriber(modid = Names.MOD_ID)
    public static class Listener {
        @SubscribeEvent
        public static void onFireball(EntityJoinWorldEvent event) {
            if (event.getEntity() instanceof Fireball f && f.getOwner() instanceof Ghast ghast
                    && hasPersistentHack(ghast, HackableGhast.class)) {
                event.setCanceled(true);
            }
        }
    }
}
