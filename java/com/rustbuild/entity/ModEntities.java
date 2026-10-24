package com.rustbuild.entity;

import com.rustbuild.RustBuild;
import com.rustbuild.deploy.DeployableEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RustBuild.MOD_ID);

    public static final RegistryObject<EntityType<PieceEntity>> PIECE = ENTITY_TYPES.register("piece", () ->
            EntityType.Builder.<PieceEntity>of(PieceEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune()
                    .build("piece"));

    public static final RegistryObject<EntityType<DeployableEntity>> DEPLOYABLE = ENTITY_TYPES.register("deployable", () ->
            EntityType.Builder.<DeployableEntity>of(DeployableEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .fireImmune()
                    .build("deployable"));

    private ModEntities() {}
}
