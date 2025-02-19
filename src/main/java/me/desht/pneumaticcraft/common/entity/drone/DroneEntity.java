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

package me.desht.pneumaticcraft.common.entity.drone;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.block.IPneumaticWrenchable;
import me.desht.pneumaticcraft.api.drone.*;
import me.desht.pneumaticcraft.api.item.IProgrammable;
import me.desht.pneumaticcraft.api.pneumatic_armor.hacking.IHackableEntity;
import me.desht.pneumaticcraft.api.pressure.PressureHelper;
import me.desht.pneumaticcraft.api.semiblock.SemiblockEvent;
import me.desht.pneumaticcraft.api.tileentity.IManoMeasurable;
import me.desht.pneumaticcraft.api.upgrade.PNCUpgrade;
import me.desht.pneumaticcraft.client.util.ProgressingLine;
import me.desht.pneumaticcraft.common.block.entity.PneumaticEnergyStorage;
import me.desht.pneumaticcraft.common.block.entity.drone.DroneRedstoneEmitterBlockEntity;
import me.desht.pneumaticcraft.common.capabilities.BasicAirHandler;
import me.desht.pneumaticcraft.common.config.ConfigHelper;
import me.desht.pneumaticcraft.common.debug.DroneDebugger;
import me.desht.pneumaticcraft.common.drone.*;
import me.desht.pneumaticcraft.common.drone.ai.DroneAIManager;
import me.desht.pneumaticcraft.common.drone.ai.DroneAIManager.WrappedGoal;
import me.desht.pneumaticcraft.common.drone.ai.DroneGoToChargingStation;
import me.desht.pneumaticcraft.common.drone.ai.DroneGoToOwner;
import me.desht.pneumaticcraft.common.drone.progwidgets.ProgWidgetGoToLocation;
import me.desht.pneumaticcraft.common.drone.progwidgets.ProgWidgetLogistics;
import me.desht.pneumaticcraft.common.drone.progwidgets.SavedDroneProgram;
import me.desht.pneumaticcraft.common.entity.semiblock.AbstractLogisticsFrameEntity;
import me.desht.pneumaticcraft.common.item.DroneItem;
import me.desht.pneumaticcraft.common.item.GPSToolItem;
import me.desht.pneumaticcraft.common.item.ItemRegistry;
import me.desht.pneumaticcraft.common.item.minigun.AbstractGunAmmoItem;
import me.desht.pneumaticcraft.common.minigun.Minigun;
import me.desht.pneumaticcraft.common.network.DronePacket;
import me.desht.pneumaticcraft.common.network.NetworkHandler;
import me.desht.pneumaticcraft.common.network.PacketPlayMovingSound.MovingSoundFocus;
import me.desht.pneumaticcraft.common.network.PacketShowWireframe;
import me.desht.pneumaticcraft.common.network.PacketSyncDroneProgWidgets;
import me.desht.pneumaticcraft.common.pneumatic_armor.CommonArmorHandler;
import me.desht.pneumaticcraft.common.pneumatic_armor.CommonUpgradeHandlers;
import me.desht.pneumaticcraft.common.registry.*;
import me.desht.pneumaticcraft.common.thirdparty.RadiationSourceCheck;
import me.desht.pneumaticcraft.common.upgrades.*;
import me.desht.pneumaticcraft.common.util.DirectionUtil;
import me.desht.pneumaticcraft.common.util.DroneProgramBuilder;
import me.desht.pneumaticcraft.common.util.IOHelper;
import me.desht.pneumaticcraft.common.util.PneumaticCraftUtils;
import me.desht.pneumaticcraft.common.util.chunkloading.DynamicChunkLoader;
import me.desht.pneumaticcraft.common.util.chunkloading.PlayerLogoutTracker;
import me.desht.pneumaticcraft.common.util.fakeplayer.DroneFakePlayer;
import me.desht.pneumaticcraft.common.util.fakeplayer.DroneItemHandler;
import me.desht.pneumaticcraft.lib.Log;
import me.desht.pneumaticcraft.lib.PneumaticValues;
import me.desht.pneumaticcraft.mixin.accessors.EntityAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

import static me.desht.pneumaticcraft.api.PneumaticRegistry.RL;
import static me.desht.pneumaticcraft.common.util.PneumaticCraftUtils.xlate;

public class DroneEntity extends AbstractDroneEntity implements
        IManoMeasurable, IPneumaticWrenchable, IEntityWithComplexSpawn,
        IHackableEntity<DroneEntity>, IDroneBase, FlyingAnimal, IUpgradeHolder {

    private static final Codec<Map<BlockPos,BlockState>> DISPLACED_LIQUIDS_CODEC
            = Codec.unboundedMap(BlockPos.CODEC, BlockState.CODEC);

    private static final float LASER_EXTEND_SPEED = 0.05F;

    private static final EntityDataAccessor<Boolean> ACCELERATING = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> PRESSURE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> PROGRAM_KEY = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<BlockPos> DUG_POS = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> GOING_TO_OWNER = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DRONE_COLOR = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MINIGUN_ACTIVE = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_MINIGUN = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> LABEL = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ACTIVE_WIDGET = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> TARGET_POS = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<ItemStack> HELD_ITEM = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);

    private static final MutableComponent DEF_DRONE_NAME = Component.literal("Drone");

    private static final HashMap<Component, Integer> LASER_COLOR_MAP = new HashMap<>();
    static {
        LASER_COLOR_MAP.put(Component.literal("aureylian"), 0xff69b4);
        LASER_COLOR_MAP.put(Component.literal("loneztar"), 0x00a0a0);
        LASER_COLOR_MAP.put(Component.literal("jadedcat"), 0xa020f0);
        LASER_COLOR_MAP.put(Component.literal("desht"), 0xff6000);
    }

    private final EntityDroneItemHandler droneItemHandler = new EntityDroneItemHandler(this);

    private final FluidTank fluidTank = new FluidTank(Integer.MAX_VALUE);

    private final PneumaticEnergyStorage energy = new PneumaticEnergyStorage(100000);

    private final ItemStackHandler upgradeInventory = new ItemStackHandler(9);
    private final UpgradeCache upgradeCache = new UpgradeCache(this);

    private BasicAirHandler airHandler;

    private final Map<Direction,Integer> emittingRedstoneValues = new EnumMap<>(Direction.class);
    private float propSpeed;

    private ProgressingLine targetLine;
    private ProgressingLine oldTargetLine;

    public List<IProgWidget> progWidgets = new ArrayList<>();

    private DroneFakePlayer fakePlayer;
    public Component ownerName = DEF_DRONE_NAME;
    private UUID ownerUUID;

    private final DroneGoToChargingStation chargeAI;
    private DroneGoToOwner gotoOwnerAI;
    private final DroneAIManager aiManager = new DroneAIManager(this);

    private double droneSpeed;  // not to be confused with LivingEntity#speed
    private int healingInterval;
    private int suffocationCounter = 40; // Drones are immune to suffocation for this time.
    private boolean isSuffocating;
    private boolean disabledByHacking;
    private boolean standby; // If true, the drone's propellers stop, the drone will fall down, and won't use pressure.
    private boolean allowStandbyPickup;
    private Minigun minigun;
    private int attackCount; // tracks number of times drone has starting attacking something
    private BlockPos deployPos; // where the drone was deployed, accessible to programs as '$deploy_pos'

    private final DroneDebugger debugger = new DroneDebugger(this);

    private int securityUpgradeCount; // for liquid immunity: 1 = breathe in water, 2 = temporary air bubble, 3+ = permanent water removal
    private final Map<BlockPos, BlockState> displacedLiquids = new HashMap<>();  // liquid blocks displaced by security upgrade

    // Although this is only used by DroneAILogistics, it is here rather than there,
    // so it can persist, for performance reasons; DroneAILogistics is a short-lived object
    private LogisticsManager logisticsManager;
    private ItemEnchantments stackEnchants = ItemEnchantments.EMPTY;
    private boolean carriedEntityAIdisabled;  // true if the drone's carried entity AI was already disabled
    private UUID wrenchedBy = null;

    private ChunkPos prevChunkPos = null;
    private DynamicChunkLoader chunkLoader;

    protected Consumer<SemiblockEvent> semiblockEventConsumer = null;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level world) {
        super(type, world);
        moveControl = new DroneMovementController(this);
        goalSelector.addGoal(1, chargeAI = new DroneGoToChargingStation(this));
    }

    DroneEntity(EntityType<? extends DroneEntity> type, Level world, Player player) {
        this(type, world);
        if (player != null) {
            ownerUUID = player.getGameProfile().getId();
            ownerName = player.getName();
        } else {
            ownerUUID = getUUID(); // Anonymous drone used for Amadron or spawned with a Dispenser
            ownerName = DEF_DRONE_NAME;
        }
    }

    public DroneEntity(Level world, Player player) {
        this(ModEntityTypes.DRONE.get(), world, player);
    }

    protected void registerSemiblockEventListener() {
        if (semiblockEventConsumer == null) {
            // only drones with a logistics widget actually care about semiblock events
            if (progWidgets.stream().anyMatch(w -> w instanceof ProgWidgetLogistics)) {
                semiblockEventConsumer = this::onSemiblockEvent;
                NeoForge.EVENT_BUS.addListener(semiblockEventConsumer);
            }
        } else {
            throw new IllegalStateException("already registered a semiblock event listener!");
        }
    }

    protected void unregisterSemiblockEventListener() {
        if (semiblockEventConsumer != null) {
            NeoForge.EVENT_BUS.unregister(semiblockEventConsumer);
        }
    }

    public void onSemiblockEvent(SemiblockEvent event) {
        if (!event.getWorld().isClientSide && event.getWorld() == getCommandSenderWorld()
                && event.getSemiblock() instanceof AbstractLogisticsFrameEntity) {
            // semiblock has been added or removed; clear the cached logistics manager
            // next DroneAILogistics operation will search the area again
            logisticsManager = null;
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();

        if (!level().isClientSide) {
            unregisterSemiblockEventListener();
        }
    }

    @Override
    protected PathNavigation createNavigation(Level worldIn) {
        EntityPathNavigateDrone nav = new EntityPathNavigateDrone(this, worldIn);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    /**
     * Deserialize stored itemstack data when a drone item is deployed.
     * @param droneStack the drone itemstack
     */
    public void readFromItemStack(ItemStack droneStack) {
        Validate.isTrue(droneStack.getItem() instanceof DroneItem);
        DroneItem droneItem = (DroneItem) droneStack.getItem();

        droneStack.getOrDefault(ModDataComponents.ITEM_UPGRADES, SavedUpgrades.EMPTY).fillItemHandler(upgradeInventory);

        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(droneStack));
        // filter out enchantments which shouldn't really be there -
        // https://github.com/TeamPneumatic/pnc-repressurized/issues/1073
        // https://github.com/EnigmaticaModpacks/Enigmatica6/issues/5167
        enchantments.keySet().removeIf(ench -> !droneStack.isPrimaryItemFor(ench));
        stackEnchants = enchantments.toImmutable();

        if (droneItem.canProgram(droneStack)) {
            progWidgets = SavedDroneProgram.loadProgWidgets(droneStack);
            ProgWidgetUtils.updatePuzzleConnections(progWidgets);
        }

        setDroneColor(droneItem.getDroneColor(droneStack).getId());

        fluidTank.setCapacity(PneumaticValues.DRONE_TANK_SIZE * (1 + getUpgrades(ModUpgrades.INVENTORY.get())));
        FluidStack storedFluid = droneStack.getOrDefault(ModDataComponents.STORED_FLUID, SimpleFluidContent.EMPTY).copy();
        fluidTank.setFluid(storedFluid);

        droneItemHandler.setUseableSlots(1 + getUpgrades(ModUpgrades.INVENTORY.get()));
        int air = droneStack.getCapability(PNCCapabilities.AIR_HANDLER_ITEM).getAir();
        getAirHandler().addAir(air);

        if (droneStack.has(DataComponents.CUSTOM_NAME)) {
            setCustomName(droneStack.get(DataComponents.CUSTOM_NAME));
        }
    }

    /**
     * Serialize the necessary data to a drone item when the drone dies.
     * @param droneStack the drone itemstack
     */
    private void writeToItemStack(ItemStack droneStack) {
        if (droneStack.getItem() instanceof DroneItem droneItem) {
            if (droneItem.canProgram(droneStack)) {
                SavedDroneProgram.writeToItem(droneStack, progWidgets);
            }

            droneStack.set(ModDataComponents.DRONE_COLOR, getDroneColor());

            if (!fluidTank.isEmpty()) {
                droneStack.set(ModDataComponents.STORED_FLUID, SimpleFluidContent.copyOf(fluidTank.getFluid()));
            }

            UpgradableItemUtils.setUpgrades(droneStack, upgradeInventory);
            EnchantmentHelper.setEnchantments(droneStack, stackEnchants);

            droneStack.getCapability(PNCCapabilities.AIR_HANDLER_ITEM).addAir(getAirHandler().getAir());

            if (hasCustomName()) {
                droneStack.set(DataComponents.CUSTOM_NAME, getCustomName());
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);

        builder.define(PRESSURE, 0.0f);
        builder.define(ACCELERATING, false);
        builder.define(PROGRAM_KEY, "");
        builder.define(DUG_POS, BlockPos.ZERO);
        builder.define(GOING_TO_OWNER, false);
        builder.define(DRONE_COLOR, DyeColor.BLACK.getId());
        builder.define(MINIGUN_ACTIVE, false);
        builder.define(HAS_MINIGUN, false);
        builder.define(AMMO, 0xFFFFFF00);
        builder.define(LABEL, "");
        builder.define(ACTIVE_WIDGET, 0);
        builder.define(TARGET_POS, BlockPos.ZERO);
        builder.define(HELD_ITEM, ItemStack.EMPTY);
        builder.define(TARGET_ID, 0);
    }

    public static AttributeSupplier.Builder prepareAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.FOLLOW_RANGE, 75.0D);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(Objects.requireNonNullElse(ownerUUID, getUUID()));
        ComponentSerialization.STREAM_CODEC.encode(buffer, ownerName);
        buffer.writeVarInt(getUpgrades(ModUpgrades.SECURITY.get()));
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        ownerUUID = buffer.readUUID();
        ownerName = ComponentSerialization.STREAM_CODEC.decode(buffer);
        securityUpgradeCount = buffer.readVarInt();
    }

    /**
     * Determines if an entity can be despawned, used on idle far away entities
     */
    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    protected float getSoundVolume() {
        return 0.2F;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource p_184601_1_) {
        return ModSounds.DRONE_HURT.get();
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DRONE_DEATH.get();
    }

    @Override
    public void tick() {
        if (tickCount == 1) {
            onFirstTick();
        }

        Level level = level();

        boolean enabled = !disabledByHacking && getAirHandler().getPressure() > 0.01F;
        if (!level.isClientSide) {
            entityData.set(PRESSURE, ((int) (getAirHandler().getPressure() * 10.0f)) / 10.0f);  // rounded for client

            setAccelerating(!standby && enabled);
            if (isAccelerating()) {
                fallDistance = 0;
            }

            if (healingInterval != 0 && getHealth() < getMaxHealth() && tickCount % healingInterval == 0) {
                heal(1);
                airHandler.addAir(-healingInterval);
            }

            if (!isSuffocating) {
                suffocationCounter = 40;
            }
            isSuffocating = false;

            if (chunkLoader != null && PlayerLogoutTracker.INSTANCE.isPlayerLoggedOutTooLong(level.getServer(), getOwnerUUID())) {
                chunkLoader.unloadAll((ServerLevel) level);
            }

            Path path = getNavigation().getPath();
            if (path != null) {
                Node target = path.getEndNode();
                if (target != null) {
                    setTargetedBlock(new BlockPos(target.x, target.y, target.z));
                } else {
                    setTargetedBlock(null);
                }
            } else {
                setTargetedBlock(null);
            }

            if (level.getGameTime() % 20 == 0) {
                debugger.updateDebuggingPlayers();
            }

            FakePlayer fp = getFakePlayer();
            fp.setPos(getX(), getY(), getZ());
            fp.tick();
            if (isAlive()) {
                for (int i = 0; i < 4; i++) {
                    fp.gameMode.tick();
                }
            }

            if (securityUpgradeCount > 1 && getHealth() > 0F) {
                handleFluidDisplacement();
            }

            airHandler.addAir(-PneumaticValues.DRONE_USAGE_CHUNKLOAD * getUpgrades(ModUpgrades.CHUNKLOADER.get()));

            handleDebugTick();
        } else {
            oldLaserExtension = laserExtension;
            if (getActiveProgramKey().getPath().equals("dig")) {
                laserExtension = Math.min(1, laserExtension + LASER_EXTEND_SPEED);
            } else {
                laserExtension = Math.max(0, laserExtension - LASER_EXTEND_SPEED);
            }

            if (isAccelerating() && level.random.nextBoolean()) {
                int x = (int) Math.floor(getX());
                int y = (int) Math.floor(getY() - 1);
                int z = (int) Math.floor(getZ());
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = null;
                for (int i = 0; i < 3; i++, y--) {
                    state = level.getBlockState(pos);
                    if (!state.isAir()) break;
                }

                if (!state.isAir()) {
                    Vec3 vec = new Vec3(getY() - y, 0, 0);
                    vec = vec.yRot((float) (random.nextFloat() * Math.PI * 2));
                    ParticleOptions data = new BlockParticleOption(ParticleTypes.BLOCK, state);
                    level.addParticle(data, getX() + vec.x, y + 1, getZ() + vec.z, vec.x, 0, vec.z);
                }
            }
        }
        if (isAccelerating()) {
            setDeltaMovement(getDeltaMovement().scale(0.3));
            propSpeed = Math.min(1, propSpeed + 0.04F);
            if (!level.isClientSide) {
                getAirHandler().addAir(-1);
            }
        } else {
            propSpeed = Math.max(0, propSpeed - 0.04F);
        }
        oldPropRotation = propRotation;
        propRotation += propSpeed;

        super.tick();

        if (hasMinigun()) {
            getMinigun().setAttackTarget(getTarget()).tick(getX(), getY(), getZ());
        }

        if (!level.isClientSide && isAlive()) {
            if (enabled) {
                DroneAIManager prevActive = getActiveAIManager();
                aiManager.onUpdateTasks();
                if (getActiveAIManager() != prevActive) {
                    // active AI has changed (started or stopped using External Program) - resync widget list to debugging players
                    getDebugger().getDebuggingPlayers().forEach(p -> NetworkHandler.sendToPlayer(PacketSyncDroneProgWidgets.create(this), p));
                }
            }
            handleRedstoneEmission();
        }
    }

    private void handleDebugTick() {
        Collection<ServerPlayer> debuggingPlayers = getDebugger().getDebuggingPlayers();

        if (!ConfigHelper.common().drones.droneDebuggerPathParticles.get() || debuggingPlayers.isEmpty()) {
            return;
        }

        PathNavigation navi = getNavigation();
        if (level() instanceof ServerLevel && level().getGameTime() % 10 == 0) { // only generate every 0.5 seconds, to try and cut back on packet spam
            Path path = navi.getPath();
            if (path != null) {
                for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
                    //get current point
                    BlockPos pos = path.getNode(i).asBlockPos();  // asBlockPos() = copy()
                    //get next point (or current point)
                    BlockPos nextPos = (i+1) != path.getNodeCount() ? path.getNode(i+1).asBlockPos() : pos;
                    //get difference for vector
                    BlockPos endPos = nextPos.subtract(pos);
                    spawnDebugParticle(debuggingPlayers, ParticleTypes.HAPPY_VILLAGER,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0,
                            0, 0, 0, 0);
                    //send a particle between points for direction
                    spawnDebugParticle(debuggingPlayers, ParticleTypes.END_ROD,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0,
                            endPos.getX(), endPos.getY(), endPos.getZ(), 0.1);
                }
                // render end point
                BlockPos pos = navi.getTargetPos();  // yes, this *can* be null: https://github.com/TeamPneumatic/pnc-repressurized/issues/761
                //noinspection ConstantConditions
                if (pos != null && getDronePos().distanceToSqr(Vec3.atCenterOf(pos)) > 1) {
                    spawnDebugParticle(debuggingPlayers, ParticleTypes.HEART,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0,
                            0, 0, 0, 0);
                }
            }
        }
    }

    private static <T extends ParticleOptions> void spawnDebugParticle(Collection<ServerPlayer> players, T type, double posX, double posY, double posZ, int particleCount, double xOffset, double yOffset, double zOffset, double speed) {
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(type, false,
                posX, posY, posZ,
                (float)xOffset, (float)yOffset, (float)zOffset,
                (float)speed, particleCount
        );
        players.forEach(player -> player.connection.send(packet));
    }

    private void onFirstTick() {
        if (!level().isClientSide) {
            registerSemiblockEventListener();

            double newDroneSpeed = 0.15f + Math.min(10, getUpgrades(ModUpgrades.SPEED.get())) * 0.015f;
            if (getUpgrades(ModUpgrades.ARMOR.get()) > 6) {
                newDroneSpeed -= 0.01f * (getUpgrades(ModUpgrades.ARMOR.get()) - 6);
            }
            setDroneSpeed(newDroneSpeed);

            healingInterval = getUpgrades(ModUpgrades.ITEM_LIFE.get()) > 0 ? 100 / getUpgrades(ModUpgrades.ITEM_LIFE.get()) : 0;

            securityUpgradeCount = getUpgrades(ModUpgrades.SECURITY.get());
            setPathfindingMalus(PathType.WATER, securityUpgradeCount > 0 ? 0.0f : -1.0f);

            energy.setCapacity(100000 + 100000 * getUpgrades(ModUpgrades.VOLUME.get()));

            setHasMinigun(getUpgrades(ModUpgrades.MINIGUN.get()) > 0);

            if (getUpgrades(ModUpgrades.CHUNKLOADER.get()) > 0) {
                prevChunkPos = chunkPosition();
                chunkLoader = DynamicChunkLoader.forDrone(this);
                chunkLoader.updateLoadedChunks((ServerLevel) level(), prevChunkPos);
            }

            droneItemHandler.setFakePlayerReady();

            aiManager.setWidgets(progWidgets);
        }
    }

    private void handleRedstoneEmission() {
        if (level().isEmptyBlock(blockPosition())) {
            for (Direction d : DirectionUtil.VALUES) {
                if (getEmittingRedstone(d) > 0) {
                    level().setBlockAndUpdate(blockPosition(), ModBlocks.DRONE_REDSTONE_EMITTER.get().defaultBlockState());
                    PneumaticCraftUtils.getBlockEntityAt(level(), blockPosition(), DroneRedstoneEmitterBlockEntity.class)
                            .ifPresent(be -> be.setOwner(this));
                }
                break;
            }
        }
    }

    private void handleFluidDisplacement() {
        restoreFluidBlocks(true);

        for (int x = (int) getX() - 1; x <= (int) (getX() + getBbWidth()); x++) {
            for (int y = (int) getY() - 1; y <= (int) (getY() + getBbHeight() + 1); y++) {
                for (int z = (int) getZ() - 2; z <= (int) (getZ() + getBbWidth()); z++) {
                    if (PneumaticCraftUtils.isBlockLiquid(level().getBlockState(new BlockPos(x, y, z)).getBlock())) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (securityUpgradeCount == 2) displacedLiquids.put(pos, level().getBlockState(pos));
                        level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);

        if (chunkLoader != null && level() instanceof ServerLevel serverLevel && prevChunkPos != null && !chunkPosition().equals(prevChunkPos)) {
            prevChunkPos = chunkPosition();
            chunkLoader.updateLoadedChunks(serverLevel, prevChunkPos);
        }
    }

    public boolean shouldLoadChunk(ChunkPos cp) {
        return Math.abs(cp.x - chunkPosition().x) + Math.abs(cp.z - chunkPosition().z) < getUpgrades(ModUpgrades.CHUNKLOADER.get());
    }

    @Override
    public boolean isDescending() {
        // allow drones to descend through scaffolding
        return getDeltaMovement().y() < 0;
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return securityUpgradeCount == 0;
    }

    @Override
    public BlockPos getTargetedBlock() {
        BlockPos pos = entityData.get(TARGET_POS);
        return pos.equals(BlockPos.ZERO) ? null : pos;
    }

    private void setTargetedBlock(BlockPos pos) {
        entityData.set(TARGET_POS, pos == null ? BlockPos.ZERO : pos);
    }

    @Override
    public int getLaserColor() {
        Component name = hasCustomName() ? getCustomName() : ownerName;
        return LASER_COLOR_MAP.getOrDefault(name, super.getLaserColor());
    }

    @Override
    public BlockPos getDugBlock() {
        BlockPos pos = entityData.get(DUG_POS);
        return pos.equals(BlockPos.ZERO) ? null : pos;
    }

    @Override
    public ItemStack getDroneHeldItem() {
        return ConfigHelper.common().drones.dronesRenderHeldItem.get() ? entityData.get(HELD_ITEM) : ItemStack.EMPTY;
    }

    @Override
    public void setDugBlock(BlockPos pos) {
        entityData.set(DUG_POS, pos == null ? BlockPos.ZERO : pos);
    }

    // drone interface (computercraft)
    public List<WrappedGoal> getRunningTasks() {
        return aiManager.getRunningTasks();
    }

    // drone interface (computercraft)
    public Goal getRunningTargetAI() {
        return aiManager.getTargetAI();
    }

    public void setVariable(String varName, BlockPos pos) {
        aiManager.setCoordinate(varName, pos);
    }

    public Optional<BlockPos> getVariable(String varName) {
        return aiManager.getCoordinate(ownerUUID, varName);
    }

    private ResourceLocation getActiveProgramKey() {
        return ResourceLocation.parse(entityData.get(PROGRAM_KEY));
    }

    @Override
    public int getActiveWidgetIndex() {
        return entityData.get(ACTIVE_WIDGET);
    }

    @Override
    public void setActiveProgram(IProgWidget widget) {
        entityData.set(PROGRAM_KEY, widget.getTypeID().toString());
        entityData.set(ACTIVE_WIDGET, getActiveAIManager().widgets().indexOf(widget));
    }

    private void setAccelerating(boolean accelerating) {
        entityData.set(ACCELERATING, accelerating);
    }

    @Override
    public boolean isAccelerating() {
        return entityData.get(ACCELERATING);
    }

    private void setDroneColor(int color) {
        entityData.set(DRONE_COLOR, color);
    }

    @Override
    public int getDroneColor() {
        return entityData.get(DRONE_COLOR);
    }

    private void setMinigunActivated(boolean activated) {
        entityData.set(MINIGUN_ACTIVE, activated);
    }

    private boolean isMinigunActivated() {
        return entityData.get(MINIGUN_ACTIVE);
    }

    private void setHasMinigun(boolean hasMinigun) {
        entityData.set(HAS_MINIGUN, hasMinigun);
    }

    @Override
    public boolean hasMinigun() {
        return entityData.get(HAS_MINIGUN);
    }

    public int getAmmoColor() {
        return entityData.get(AMMO);
    }

    private void setAmmoColor(ItemStack ammoStack) {
        int color = ammoStack.getItem() instanceof AbstractGunAmmoItem ammo ? ammo.getAmmoColor(ammoStack) : 0xFFFF0000;
        entityData.set(AMMO, color);
    }

    @Override
    public BlockPos getDeployPos() {
        return deployPos;
    }

    public void setDeployPos(BlockPos deployPos) {
        if (this.deployPos != null) {
            throw new IllegalStateException("deployPos has already been set!");
        }
        this.deployPos = deployPos;
    }

    /**
     * Decrements the entity's air supply when underwater
     */
    @Override
    protected int decreaseAirSupply(int par1) {
        return -20; // make drones insta drown
    }

    /**
     * Moves the entity based on the specified heading.  Args: strafe, forward
     */
    @Override
    public void travel(Vec3 travelVec) {
        if (level().isClientSide) {
            LivingEntity targetEntity = getTarget();
            if (targetEntity != null && !targetEntity.isAlive()) {
                setTarget(null);
                targetEntity = null;
            }
            if (targetEntity != null) {
                if (targetLine == null) targetLine = new ProgressingLine(0, getBbHeight() / 2, 0, 0, 0, 0);
                if (oldTargetLine == null) oldTargetLine = new ProgressingLine(0, getBbHeight() / 2, 0, 0, 0, 0);

                targetLine.endX = (float) (targetEntity.getX() - getX());
                targetLine.endY = (float) (targetEntity.getY() + targetEntity.getBbHeight() / 2 - getY());
                targetLine.endZ = (float) (targetEntity.getZ() - getZ());
                oldTargetLine.endX = (float) (targetEntity.xo - xo);
                oldTargetLine.endY = (float) (targetEntity.yo + targetEntity.getBbHeight() / 2 - yo);
                oldTargetLine.endZ = (float) (targetEntity.zo - zo);

                oldTargetLine.setProgress(targetLine.getProgress());
                targetLine.incProgressByDistance(0.2f);
                noCulling = true; //don't stop rendering the drone when it goes out of the camera frustrum, as we need to render the target lines as well.
            } else {
                targetLine = oldTargetLine = null;
                noCulling = false;
            }
        }
        if (getVehicle() == null && isAccelerating()) {
            double d3 = getDeltaMovement().y;
            super.travel(travelVec);
            setDeltaMovement(getDeltaMovement().x, d3 * 0.6D, getDeltaMovement().z);
        } else {
            super.travel(travelVec);
        }
        setOnGround(true); //set onGround to true so AI pathfinding will keep updating.
    }

    public ProgressingLine getTargetLine() {
        return targetLine;
    }

    public ProgressingLine getOldTargetLine() {
        return oldTargetLine;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // mobInteract = onEntityRightClick() ?
        if (!getOwnerUUID().equals(player.getUUID())) return InteractionResult.PASS;

        ItemStack stack = player.getItemInHand(hand);
        Level level = level();

        if (stack.getItem() == ModItems.GPS_TOOL.get()) {
            if (!level.isClientSide) {
                return GPSToolItem.getGPSLocation(player.getUUID(), stack).map(gpsPos -> {
                    getNavigation().moveTo(gpsPos.getX(), gpsPos.getY(), gpsPos.getZ(), 0.1D);
                    return InteractionResult.SUCCESS;
                }).orElse(InteractionResult.PASS);
            }
            return InteractionResult.SUCCESS;
        } else if (IOHelper.getFluidHandlerForItem(stack).isPresent()) {
            if (player.level().isClientSide) return InteractionResult.SUCCESS;
            return IOHelper.getFluidHandlerForItem(stack).map(handler -> {
                if (handler.getFluidInTank(0).isEmpty()) {
                    boolean ok = player.level().isClientSide || FluidUtil.interactWithFluidHandler(player, hand, fluidTank);
                    return ok ? InteractionResult.CONSUME : InteractionResult.PASS;
                } else {
                    return InteractionResult.PASS;
                }
            }).orElseThrow(RuntimeException::new);
        } else {
            DyeColor color = DyeColor.getColor(stack);
            if (color != null) {
                if (!level.isClientSide) {
                    setDroneColor(color.getId());
                    if (ConfigHelper.common().general.useUpDyesWhenColoring.get() && !player.isCreative()) {
                        stack.shrink(1);
                        if (stack.getCount() <= 0) {
                            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        }
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    public boolean shouldDropAsItem() {
        return true;
    }

    /**
     * Called when a drone is right-clicked by a Pneumatic Wrench.
     */
    @Override
    public boolean onWrenched(Level world, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (shouldDropAsItem()) {
            wrenchedBy = player.getUUID();
            overload("wrenched");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Restore any liquids that may have been displaced by the drone (security upgrade)
     *
     * @param distCheck if true, only restore liquids in blocks > 1 block distance away from the drone
     */
    private void restoreFluidBlocks(boolean distCheck) {
        Iterator<Map.Entry<BlockPos, BlockState>> iter = displacedLiquids.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<BlockPos, BlockState> entry = iter.next();
            BlockPos pos = entry.getKey();
            if (!distCheck || pos.distToCenterSqr(getX(), getY(), getZ()) > 1) {
                if (level().isEmptyBlock(pos) || PneumaticCraftUtils.isBlockLiquid(level().getBlockState(pos).getBlock())) {
                    level().setBlock(pos, entry.getValue(), Block.UPDATE_ALL);
                }
                iter.remove();
            }
        }
    }

    @Nullable
    @Override
    public Entity changeDimension(DimensionTransition transition) {
        Entity entity = super.changeDimension(transition);
        if (entity != null) {
            restoreFluidBlocks(false);
        }
        return entity;
    }

    @Override
    protected void dropEquipment() {
        boolean wrenchedByOwner = wrenchedBy != null && wrenchedBy.equals(ownerUUID);
        for (int i = 0; i < droneItemHandler.getSlots(); i++) {
            ItemStack stack = droneItemHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (wrenchedByOwner || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    spawnAtLocation(stack, 0);
                }
                droneItemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);

        for (Entity e : getPassengers()) {
            if (e instanceof Mob mob) {
                mob.setNoAi(carriedEntityAIdisabled);
            }
        }

        restoreFluidBlocks(false);

        if (level() instanceof ServerLevel serverLevel) {
            if (chunkLoader != null) {
                chunkLoader.unloadAll(serverLevel);
            }

            if (getDugBlock() != null) {
                // stop any in-progress digging - 3rd & 4th parameters are unimportant here
                getFakePlayer().gameMode.handleBlockBreakAction(getDugBlock(), ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, Direction.UP, 0, 0);
            }
        }

        if (shouldDropAsItem()) {
            ItemStack stack = new ItemStack(getDroneItem());
            writeToItemStack(stack);
            spawnAtLocation(stack, 0);
            if (!level().isClientSide) {
                reportDroneDeath(getOwner(), damageSource);
            }
        }

        setCustomName(Component.empty());  // keep other mods (like CoFH Core) quiet about death message broadcasts
    }

    private void reportDroneDeath(Player owner, DamageSource damageSource) {
        if (owner != null) {
            int x = (int) Math.floor(getX());
            int y = (int) Math.floor(getY());
            int z = (int) Math.floor(getZ());
            String dim = level().dimension().location().toString();
            MutableComponent msg = hasCustomName() ?
                    Component.translatable("pneumaticcraft.death.drone.named", Objects.requireNonNull(getCustomName()).getString(), dim, x, y, z) :
                    Component.translatable("pneumaticcraft.death.drone", dim, x, y, z);
            owner.displayClientMessage(msg.withStyle(ChatFormatting.GOLD), false);
            if (!damageSource.is(DamageTypes.GENERIC_KILL)) {
                owner.displayClientMessage(Component.literal(" - ").append(damageSource.getLocalizedDeathMessage(this)).withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    private Item getDroneItem() {
        // return the item which has the same registry ID as our entity type
        return PneumaticCraftUtils.getRegistryName(BuiltInRegistries.ENTITY_TYPE, getType())
                .map(BuiltInRegistries.ITEM::get)
                .orElseThrow();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (level().isClientSide) {
            if (TARGET_ID.equals(key)) {
                int id = entityData.get(TARGET_ID);
                if (id > 0) {
                    Entity e = getCommandSenderWorld().getEntity(id);
                    if (e instanceof LivingEntity) {
                        setTarget((LivingEntity) e);
                    }
                }
                if (targetLine != null && oldTargetLine != null) {
                    targetLine.setProgress(0);
                    oldTargetLine.setProgress(0);
                }
            } else if (PRESSURE.equals(key)) {
                int newAir = (int) (entityData.get(PRESSURE) * getAirHandler().getVolume());
                getAirHandler().addAir(newAir - airHandler.getAir());
            }
        }
        super.onSyncedDataUpdated(key);
    }

    @Override
    public void setTarget(LivingEntity entity) {
        super.setTarget(entity);
        if (!level().isClientSide) {
            entityData.set(TARGET_ID, entity == null ? 0 : entity.getId());
        }
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        return canDroneBePickedUp() && super.startRiding(entity, force);
    }

    public BasicAirHandler getAirHandler() {
        if (airHandler == null) {
            int vol = PressureHelper.getUpgradedVolume(PneumaticValues.DRONE_VOLUME, getUpgrades(ModUpgrades.VOLUME.get()));
            ItemStack stack = new ItemStack(getDroneItem());
            EnchantmentHelper.setEnchantments(stack, stackEnchants);
            vol = ItemRegistry.getInstance().getModifiedVolume(stack, vol);
            airHandler = new BasicAirHandler(vol) {
                @Override
                public void addAir(int amount) {
                    if (amount > 0 || getUpgrades(ModUpgrades.CREATIVE.get()) == 0) {
                        super.addAir(amount);
                    }
                }
            };
        }
        return airHandler;
    }

    @Override
    public void printManometerMessage(Player player, List<Component> curInfo) {
        if (hasCustomName()) {
            curInfo.add(getDroneName().copy().withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
        } else {
            curInfo.add(getDroneName().copy().withStyle(ChatFormatting.AQUA));
        }
        curInfo.add(xlate("pneumaticcraft.entityTracker.info.tamed", getOwnerName().getString()));
        curInfo.add(xlate("pneumaticcraft.gui.tooltip.pressure", PneumaticCraftUtils.roundNumberTo(getAirHandler().getPressure(), 2)));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.put(IProgrammable.NBT_WIDGETS, ProgWidgetUtils.putWidgetsToNBT(registryAccess(), progWidgets));
        tag.put("airHandler", getAirHandler().serializeNBT());
        tag.putFloat("propSpeed", propSpeed);
        if (disabledByHacking) tag.putBoolean("disabledByHacking", true);
        if (gotoOwnerAI != null) tag.putBoolean("hackedByOwner", true);
        if (standby) tag.putBoolean("standby", true);
        if (allowStandbyPickup) tag.putBoolean("allowStandbyPickup", true);
        if (carriedEntityAIdisabled) tag.putBoolean("carriedEntityAIdisabled", true);
        tag.putInt("color", getDroneColor());
        tag.put("variables", aiManager.writeToNBT(new CompoundTag()));
        if (deployPos != null) tag.put("deployPos", NbtUtils.writeBlockPos(deployPos));

        ItemStackHandler tmpHandler = PneumaticCraftUtils.copyItemHandler(droneItemHandler, new ItemStackHandler(droneItemHandler.getSlots()));
        tag.put("Inventory", tmpHandler.serializeNBT(registryAccess()));
        tag.put("upgrades", upgradeInventory.serializeNBT(registryAccess()));

        fluidTank.writeToNBT(registryAccess(), tag);

        tag.putString("owner", ownerName.getString());
        if (ownerUUID != null) {
            tag.putLong("ownerUUID_M", ownerUUID.getMostSignificantBits());
            tag.putLong("ownerUUID_L", ownerUUID.getLeastSignificantBits());
        }

        if (!stackEnchants.isEmpty()) {
            ItemEnchantments.CODEC.encodeStart(NbtOps.INSTANCE, stackEnchants)
                    .ifSuccess(t -> tag.put("stackEnchants", t));
        }

        if (!displacedLiquids.isEmpty()) {
            DISPLACED_LIQUIDS_CODEC.encodeStart(NbtOps.INSTANCE, displacedLiquids).ifSuccess(t -> tag.put("displacedLiquids", t));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        progWidgets = ProgWidgetUtils.getWidgetsFromNBT(registryAccess(), tag.getList(IProgrammable.NBT_WIDGETS, Tag.TAG_COMPOUND));
        ProgWidgetUtils.updatePuzzleConnections(progWidgets);
        propSpeed = tag.getFloat("propSpeed");
        disabledByHacking = tag.getBoolean("disabledByHacking");
        setGoingToOwner(tag.getBoolean("hackedByOwner"));
        setDroneColor(tag.getInt("color"));
        aiManager.readFromNBT(tag.getCompound("variables"));
        standby = tag.getBoolean("standby");
        allowStandbyPickup = tag.getBoolean("allowStandbyPickup");
        upgradeInventory.deserializeNBT(registryAccess(), tag.getCompound("upgrades"));
        upgradeCache.invalidateCache();
        getAirHandler().deserializeNBT(tag.getCompound("airHandler"));
        carriedEntityAIdisabled = tag.getBoolean("carriedEntityAIdisabled");
        deployPos = NbtUtils.readBlockPos(tag, "deployPos").orElse(null);

        ItemStackHandler tmpInv = new ItemStackHandler();
        tmpInv.deserializeNBT(registryAccess(), tag.getCompound("Inventory"));
        PneumaticCraftUtils.copyItemHandler(tmpInv, droneItemHandler);
        droneItemHandler.setUseableSlots(1 + getUpgrades(ModUpgrades.INVENTORY.get()));

        fluidTank.setCapacity(PneumaticValues.DRONE_TANK_SIZE * (1 + getUpgrades(ModUpgrades.INVENTORY.get())));
        fluidTank.readFromNBT(registryAccess(), tag);

        energy.setCapacity(100000 + 100000 * getUpgrades(ModUpgrades.VOLUME.get()));

        if (tag.contains("owner")) ownerName = Component.literal(tag.getString("owner"));
        if (tag.contains("ownerUUID_M")) ownerUUID = new UUID(tag.getLong("ownerUUID_M"), tag.getLong("ownerUUID_L"));

        stackEnchants = ItemEnchantments.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("stackEnchants"))
                .result().orElse(ItemEnchantments.EMPTY);

        displacedLiquids.clear();
        DISPLACED_LIQUIDS_CODEC.parse(NbtOps.INSTANCE, tag.getCompound("displacedLiquids"))
                .ifSuccess(displacedLiquids::putAll);
    }

    // computercraft ("getOwnerName" method)
    @Override
    public Component getOwnerName() {
        return ownerName;
    }

    @Override
    public UUID getOwnerUUID() {
        if (ownerUUID == null) {
            Log.warning("Drone with owner '{}' has no UUID! Substituting the Drone's UUID ({}).", ownerName, getUUID());
            Log.warning("If you use any protection mods, the drone might not be able to operate in protected areas.");
            ownerUUID = getUUID();
        }
        return ownerUUID;
    }

    @Override
    public int getUpgrades(PNCUpgrade upgrade) {
        return upgradeCache.getUpgrades(upgrade);
    }

    @Override
    public FakePlayer getFakePlayer() {
        if (fakePlayer == null && !level().isClientSide) {
            // using the owner's UUID for the fake player should be fine in Forge 35.0.12 and up
            // see https://github.com/MinecraftForge/MinecraftForge/pull/7454
            fakePlayer = new DroneFakePlayer((ServerLevel) level(), new GameProfile(getOwnerUUID(), ownerName.getString()), this);
        }
        return fakePlayer;
    }

    public Minigun getMinigun() {
        if (minigun == null) {
            minigun = new MinigunDrone(level().isClientSide ? null : getFakePlayer())
                    .setWorld(level())
                    .setAirHandler(this.getCapability(PNCCapabilities.AIR_HANDLER_ENTITY), PneumaticValues.DRONE_USAGE_ATTACK)
                    .setInfiniteAmmo(getUpgrades(ModUpgrades.CREATIVE.get()) > 0);
        }
        return minigun;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        getFakePlayer().attack(entity);
        if (entity instanceof LivingEntity livingEntity) {
            if (livingEntity.isAlive() && livingEntity.getLastHurtByMob() == getFakePlayer()) {
                livingEntity.setLastHurtByMob(this);
            }
        }
        getAirHandler().addAir(-PneumaticValues.DRONE_USAGE_ATTACK);
        return true;
    }

    @Override
    public int getArmorValue() {
        return getUpgrades(ModUpgrades.ARMOR.get());
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (source.is(DamageTypes.IN_WALL)) {
            if (suffocationCounter > 0) suffocationCounter--;
        }
        return super.hurt(source, damage);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypes.IN_WALL)) {
            return suffocationCounter > 0 || !ConfigHelper.common().drones.enableDroneSuffocation.get();
        }
        if (RadiationSourceCheck.INSTANCE.isRadiation(level().registryAccess(), source)) {
            return true;
        }
        Entity e = source.getEntity();
        if (e != null && !level().isClientSide && e.getId() == getFakePlayer().getId()) {
            // don't allow the drone's own fake player to damage the drone
            // e.g. if the drone is wielding an infinity hammer
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public IItemHandlerModifiable getInv() {
        return droneItemHandler;
    }

    @Override
    public int getEmittingRedstone(Direction side) {
        return emittingRedstoneValues.getOrDefault(side, 0);
    }

    @Override
    public void setEmittingRedstone(Direction side, int value) {
        if (emittingRedstoneValues.getOrDefault(side, 0) != value) {
            emittingRedstoneValues.put(side, value);
            BlockPos pos = new BlockPos((int) Math.floor(getX() + getBbWidth() / 2), (int) Math.floor(getY()), (int) Math.floor(getZ() + getBbWidth() / 2));
            BlockState state = level().getBlockState(pos);
            level().sendBlockUpdated(pos, state, state, 3);
        }
    }

    @Override
    public boolean isBlockValidPathfindBlock(BlockPos pos) {
        if (level().isEmptyBlock(pos)) return true;
        BlockState state = level().getBlockState(pos);
        Block block = state.getBlock();
        if (PneumaticCraftUtils.isBlockLiquid(block)) {
            return securityUpgradeCount > 0;
        }
        if (checkMC181565kludge(block)) return false;
        if (state.isPathfindable(PathComputationType.LAND)) return true;
//        if (!state.blocksMotion() && block != Blocks.LADDER) return true;
        if (DroneRegistry.getInstance().pathfindableBlocks.containsKey(block)) {
            IPathfindHandler pathfindHandler = DroneRegistry.getInstance().pathfindableBlocks.get(block);
            return pathfindHandler == null || pathfindHandler.canPathfindThrough(level(), pos);
        } else {
            return false;
        }
    }

    // temp workaround for https://bugs.mojang.com/browse/MC-181565
    // some vanilla blocks with non-full shapes that don't override isPathfindable()
    private static final Set<Block> MC181565_BLOCKS = Set.of(
            Blocks.AMETHYST_CLUSTER,
            Blocks.CANDLE,
            Blocks.LILY_PAD,
            Blocks.BIG_DRIPLEAF,
            Blocks.POINTED_DRIPSTONE,
            Blocks.TURTLE_EGG,
            Blocks.AZALEA,
            Blocks.HONEY_BLOCK
    );
    private static boolean checkMC181565kludge(Block block) {
        return MC181565_BLOCKS.contains(block);
    }

    @Override
    public void sendWireframeToClient(BlockPos pos) {
        NetworkHandler.sendToAllTracking(PacketShowWireframe.forDrone(this, pos), this);
    }

    /**
     * IHackableEntity
     */

    @Override
    public ResourceLocation getHackableId() {
        return RL("drone");
    }

    @NotNull
    @Override
    public Class<DroneEntity> getHackableClass() {
        return DroneEntity.class;
    }

    @Override
    public boolean canHack(Entity entity, Player player) {
        if (!IHackableEntity.super.canHack(entity, player)) return false;

        CommonArmorHandler handler = CommonArmorHandler.getHandlerForPlayer(player);
        return handler.upgradeUsable(CommonUpgradeHandlers.hackHandler, false)
                && handler.getUpgradeCount(EquipmentSlot.HEAD, ModUpgrades.ENTITY_TRACKER.get()) >= 1;
    }

    @Override
    public void addHackInfo(DroneEntity entity, List<Component> curInfo, Player player) {
        if (ownerUUID.equals(player.getUUID())) {
            if (isGoingToOwner()) {
                curInfo.add(xlate("pneumaticcraft.armor.hacking.result.resumeTasks"));
            } else {
                curInfo.add(xlate("pneumaticcraft.armor.hacking.result.callBack"));
            }
        } else {
            curInfo.add(xlate("pneumaticcraft.armor.hacking.result.disable"));
        }
    }

    @Override
    public void addPostHackInfo(DroneEntity entity, List<Component> curInfo, Player player) {
        if (ownerUUID.equals(player.getUUID())) {
            if (isGoingToOwner()) {
                curInfo.add(xlate("pneumaticcraft.armor.hacking.finished.calledBack"));
            } else {
                curInfo.add(xlate("pneumaticcraft.armor.hacking.finished.resumedTasks"));
            }
        } else {
            curInfo.add(xlate("pneumaticcraft.armor.hacking.finished.disabled"));
        }
    }

    @Override
    public int getHackTime(DroneEntity entity, Player player) {
        return ownerUUID.equals(player.getUUID()) ? 20 : 100;
    }

    @Override
    public void onHackFinished(DroneEntity entity, Player player) {
        if (!level().isClientSide && player.getUUID().equals(ownerUUID)) {
            setGoingToOwner(gotoOwnerAI == null); //toggle the state
        } else {
            disabledByHacking = true;
        }
    }

    private void setGoingToOwner(boolean state) {
        if (!level().isClientSide) {
            if (state && gotoOwnerAI == null) {
                gotoOwnerAI = new DroneGoToOwner(this);
                goalSelector.addGoal(2, gotoOwnerAI);
                entityData.set(GOING_TO_OWNER, true);
                setActiveProgram(new ProgWidgetGoToLocation());
            } else if (!state && gotoOwnerAI != null) {
                goalSelector.removeGoal(gotoOwnerAI);
                gotoOwnerAI = null;
                entityData.set(GOING_TO_OWNER, false);
            }
        }
    }

    private boolean isGoingToOwner() {
        return entityData.get(GOING_TO_OWNER);
    }

    @Override
    public FluidTank getFluidTank() {
        return fluidTank;
    }

    @Override
    public IEnergyStorage getEnergyStorage() {
        return energy;
    }

    @Override
    public Player getOwner() {
        MinecraftServer server = level().getServer();
        return server != null ? server.getPlayerList().getPlayer(ownerUUID) : null;
    }

    public void setStandby(boolean standby, boolean allowPickup) {
        this.standby = standby;
        this.allowStandbyPickup = allowPickup;
    }

    @Override
    public Level getDroneLevel() {
        return level();
    }

    @Override
    public Vec3 getDronePos() {
        return position();
    }

    @Override
    public BlockPos getControllerPos() {
        return BlockPos.ZERO;
    }

    @Override
    public void dropItem(ItemStack stack) {
        spawnAtLocation(stack, 0);
    }

    @Override
    public List<IProgWidget> getProgWidgets() {
        return progWidgets;
    }

    @Override
    public GoalSelector getTargetAI() {
        return targetSelector;
    }

    @Override
    public boolean isProgramApplicable(ProgWidgetType<?> widgetType) {
        return true;
    }

    @Override
    public void setName(Component string) {
        setCustomName(string);
    }

    @Override
    public void setCarryingEntity(Entity entity) {
        if (entity == null) {
            for (Entity e : getCarryingEntities()) {
                e.stopRiding();
                if (e instanceof Mob mob) mob.setNoAi(carriedEntityAIdisabled);
                checkForMinecartKludge(e);
            }
        } else if (entity.startRiding(this) && entity instanceof Mob mob) {
            carriedEntityAIdisabled = mob.isNoAi();
            mob.setNoAi(true);
        }
    }

    private boolean canDroneBePickedUp() {
        return ConfigHelper.common().drones.dronesCanBePickedUp.get()
                || standby && allowStandbyPickup;
    }

    private void checkForMinecartKludge(Entity e) {
        double y = e.getY();
        if (canDroneBePickedUp() && (e instanceof AbstractMinecart || e instanceof Boat)) {
            // little kludge to prevent the dropped minecart/boat immediately picking up the drone
            y -= 2;
            BlockPos pos = e.blockPosition();
            if (level().getBlockState(pos).isRedstoneConductor(level(), pos)) {
                y++;
            }
            // minecarts have their own tick() which doesn't decrement rideCooldown
            if (e instanceof AbstractMinecart) ((EntityAccess) e).setBoardingCooldown(0);
        }
        if (y != e.getY()) e.setPos(e.getX(), y, e.getZ());
    }

    @Override
    public List<Entity> getCarryingEntities() {
        return getPassengers();
    }

    @Override
    public boolean isAIOverridden() {
        return chargeAI.isExecuting || gotoOwnerAI != null;
    }

    @Override
    public void onItemPickupEvent(ItemEntity curPickingUpEntity, int stackSize) {
        take(curPickingUpEntity, stackSize);
    }

    @Override
    public IPathNavigator getPathNavigator() {
        return (IPathNavigator) getNavigation();
    }

    public void tryFireMinigun(LivingEntity target) {
        int slot = getSlotForAmmo();
        if (slot >= 0) {
            ItemStack ammo = droneItemHandler.getStackInSlot(slot);
            if (getMinigun().setAmmoStack(ammo).tryFireMinigun(target)) {
                droneItemHandler.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Get the first slot which has any ammo in it.
     *
     * @return a slot number, or -1 if no ammo
     */
    public int getSlotForAmmo() {
        for (int i = 0; i < droneItemHandler.getSlots(); i++) {
            if (droneItemHandler.getStackInSlot(i).getItem() instanceof AbstractGunAmmoItem) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void overload(String msgKey, Object... params) {
        kill();
        Player owner = getOwner();
        if (owner != null) {
            owner.displayClientMessage(Component.literal(" - ")
                    .append(Component.translatable("pneumaticcraft.death.drone.overload." + msgKey, params))
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    @Override
    public DroneAIManager getAIManager() {
        return aiManager;
    }

    @Override
    public LogisticsManager getLogisticsManager() {
        return logisticsManager;
    }

    @Override
    public void setLogisticsManager(LogisticsManager logisticsManager) {
        this.logisticsManager = logisticsManager;
    }

    @Override
    public void playSound(SoundEvent soundEvent, SoundSource category, float volume, float pitch) {
        level().playSound(null, blockPosition(), soundEvent, category, volume, pitch);
    }

    @Override
    public void addAirToDrone(int air) {
        airHandler.addAir(air);
    }

    @Override
    public void updateLabel() {
        entityData.set(LABEL, getAIManager() != null ? getAIManager().getLabel() : "Main");
    }

    @Override
    public String getLabel() {
        return entityData.get(LABEL);
    }

    @Override
    public boolean isTeleportRangeLimited() {
        return true;
    }

    @Override
    public Component getDroneName() {
        return getName();
    }

    @Override
    public DroneDebugger getDebugger() {
        return debugger;
    }

    @Override
    public void storeTrackerData(ItemStack stack) {
        stack.set(ModDataComponents.DRONE_DEBUG_TARGET, DronePacket.DroneTarget.forEntityId(getId()));
    }

    @Override
    public IItemHandler getUpgradeHandler() {
        return upgradeInventory;
    }

    @Override
    public void onUpgradesChanged() {
        energy.setCapacity(100000 + 100000 * getUpgrades(ModUpgrades.VOLUME.get()));
    }

    @Override
    public boolean isDroneStillValid() {
        return isAlive();
    }

    @Override
    public boolean canMoveIntoFluid(Fluid fluid) {
        if (fluid.getFluidType().getTemperature() > 373) {
            return false;
        } else {
            return !canDrownInFluidType(fluid.getFluidType());
        }
    }

    @Override
    public DroneItemHandler getDroneItemHandler() {
        return droneItemHandler;
    }

    /**
     * Add an initial program to a drone that's about to be placed. This is called right before the entity is spawned.
     * Programmable drones don't do anything here (program is created by the programmer and stored in item NBT), but
     * subclasses will add their static program here.
     *
     * @param clickPos block the drone item is clicked against
     * @param facing side of the clicked block
     * @param placePos blockpos the drone will appear in
     * @param droneStack the drone itemstack
     * @param progWidgets add the program to this list, ideally using {@link DroneProgramBuilder}
     * @return true if a program was added, false otherwise
     */
    public boolean addProgram(BlockPos clickPos, Direction facing, BlockPos placePos, ItemStack droneStack, List<IProgWidget> progWidgets) {
        return false;
    }

    public void incAttackCount() {
        attackCount++;
    }

    public int getAttackCount() {
        return attackCount;
    }

    @Override
    public void resetAttackCount() {
        attackCount = 0;
    }

    @Override
    public float getDronePressure() {
        return getAirHandler().getPressure();
    }

    @Override
    public DronePacket.DroneTarget getPacketTarget() {
        return DronePacket.DroneTarget.forEntityId(getId());
    }

    public void setDroneSpeed(double droneSpeed) {
        this.droneSpeed = droneSpeed;
    }

    public double getDroneSpeed() {
        return droneSpeed;
    }

    @Override
    public boolean isFlying() {
        return !onGround();
    }

    public class MinigunDrone extends Minigun {
        MinigunDrone(FakePlayer fakePlayer) {
            super(fakePlayer, true);
        }

        @Override
        public MovingSoundFocus getSoundSource() {
            return MovingSoundFocus.of(DroneEntity.this);
        }

        @Override
        public boolean isMinigunActivated() {
            return DroneEntity.this.isMinigunActivated();
        }

        @Override
        public void setMinigunActivated(boolean activated) {
            if (!world.isClientSide) {
                // only set server-side; drone sync's the activation state to client
                DroneEntity.this.setMinigunActivated(activated);
            }
        }

        @Override
        public void setAmmoColorStack(@Nonnull ItemStack ammo) {
            if (!world.isClientSide) {
                // only set server-side; drone sync's the activation state to client
                setAmmoColor(ammo);
            }
        }

        @Override
        public int getAmmoColor() {
            return DroneEntity.this.getAmmoColor();
        }

        @Override
        public void playSound(SoundEvent soundName, float volume, float pitch) {
            world.playSound(null, blockPosition(), soundName, SoundSource.NEUTRAL, volume, pitch);
        }

        @Override
        public Vec3 getMuzzlePosition() {
            Vec3 centre = position();
            LivingEntity target = minigun.getAttackTarget();
            if (target == null) return null;
            Vec3 offset = target.position()
                    .add(0, target.getBbHeight() / 2, 0)
                    .subtract(centre)
                    .normalize().scale(0.6);
            return centre.add(offset);
        }

        @Override
        public Vec3 getLookAngle() {
            return Vec3.directionFromRotation(minigunPitch, minigunYaw).normalize();
        }

        @Override
        public float getParticleScale() {
            return 1f;
        }

        @Override
        public boolean isValid() {
            return DroneEntity.this.isAlive();
        }
    }

    private class EntityDroneItemHandler extends DroneItemHandler {
        EntityDroneItemHandler(IDrone holder) {
            super(holder, 1);
        }

        @Override
        public void copyItemToFakePlayer(int slot) {
            super.copyItemToFakePlayer(slot);

            if (isFakePlayerReady() && slot == getFakePlayer().getInventory().selected && ConfigHelper.common().drones.dronesRenderHeldItem.get()) {
                entityData.set(HELD_ITEM, getStackInSlot(slot));
            }
        }
    }
}