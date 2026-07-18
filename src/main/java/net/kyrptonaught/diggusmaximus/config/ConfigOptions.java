package net.kyrptonaught.diggusmaximus.config;

import java.util.HashSet;

public class ConfigOptions {

    public boolean enabled = true;

    public KeyBindingConfig keybinding = new KeyBindingConfig(true, true, "key.keyboard.grave.accent");

    public boolean invertActivation = false;

    public boolean sneakToExcavate = false;

    public boolean mineDiag = true;

    public int maxMinedBlocks = 40;

    public int maxMineDistance = 10;

    public boolean autoPickup = true;

    public boolean requiresTool = false;

    public boolean dontBreakTool = true;

    public boolean stopOnToolBreak = true;

    public boolean toolDurability = true;

    public boolean playerExhaustion = true;

    public float exhaustionMultiplier = 1.0f;

    public HashSet<String> tools = new HashSet<>();
}
