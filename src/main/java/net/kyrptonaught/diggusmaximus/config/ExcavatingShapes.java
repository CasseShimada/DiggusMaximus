package net.kyrptonaught.diggusmaximus.config;

import net.kyrptonaught.diggusmaximus.ExcavateTypes;

public class ExcavatingShapes {
    public boolean enableShapes = false;

    public boolean includeDifBlocks = false;

    public KeyBindingConfig shapeKey = new KeyBindingConfig(false, false, "key.keyboard.unknown");

    public KeyBindingConfig cycleKey = new KeyBindingConfig(false, false, "key.keyboard.unknown");

    public ExcavateTypes.shape selectedShape = ExcavateTypes.shape.LAYER;
}
