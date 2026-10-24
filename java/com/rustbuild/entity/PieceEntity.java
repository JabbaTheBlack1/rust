package com.rustbuild.entity;

import com.rustbuild.build.Piece;
import com.rustbuild.build.PieceType;
import com.rustbuild.build.Tier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Визуальная оболочка элемента постройки. Позиция - опорная точка элемента, угол хранится в данных (float, без округления).
 * Физику даёт CollisionBlock, истину о здоровье и связях хранит BuildingData.
 * Клиент по этой сущности восстанавливает Piece для контура-предпросмотра и привязки к сокетам.
 */
public class PieceEntity extends Entity {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(PieceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(PieceEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DEPTH = SynchedEntityData.defineId(PieceEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(PieceEntity.class, EntityDataSerializers.FLOAT);

    public PieceEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TYPE, 0);
        builder.define(TIER, 0);
        builder.define(DEPTH, 0.5f);
        builder.define(YAW, 0f);
    }

    public void setup(Piece p) {
        entityData.set(TYPE, p.type().ordinal());
        entityData.set(TIER, p.tier().ordinal());
        entityData.set(DEPTH, p.depth());
        entityData.set(YAW, (float) p.yaw());
    }

    public PieceType pieceType() { return PieceType.byIndex(entityData.get(TYPE)); }

    public Tier tier() {
        Tier[] v = Tier.values();
        return v[Math.floorMod(entityData.get(TIER), v.length)];
    }

    public float depth() { return entityData.get(DEPTH); }
    public float pieceYaw() { return entityData.get(YAW); }

    /** Лёгкое представление для клиентской привязки (без серверных данных). */
    public Piece toView() {
        return Piece.blank(pieceType(), getX(), getY(), getZ(), pieceYaw(), depth()).withId(getUUID()).withTier(tier());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(TYPE, t.getInt("type"));
        entityData.set(TIER, t.getInt("tier"));
        entityData.set(DEPTH, t.getFloat("depth"));
        entityData.set(YAW, t.getFloat("yaw"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("type", entityData.get(TYPE));
        t.putString("typeId", pieceType().id);          // стабильный идентификатор для внешних модов (rustdoors)
        t.putInt("tier", entityData.get(TIER));
        t.putFloat("depth", entityData.get(DEPTH));
        t.putFloat("yaw", entityData.get(YAW));
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isNoGravity() { return true; }
}
