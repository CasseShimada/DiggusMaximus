package net.kyrptonaught.diggusmaximus;

public interface DiggingPlayerEntity {
    boolean diggus$isExcavating();

    void diggus$setExcavating(boolean excavating);

    PendingExcavation diggus$getPendingExcavation();

    void diggus$setPendingExcavation(PendingExcavation pending);

    boolean diggus$tryConsumeRequestToken(long gameTime);
}
