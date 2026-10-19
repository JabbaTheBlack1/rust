package com.rustbuild.client;

import net.minecraftforge.common.MinecraftForge;

public final class ClientInit {
    private ClientInit() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(PreviewRenderer::onRenderLevel);
    }
}
