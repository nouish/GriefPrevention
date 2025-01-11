package com.griefprevention.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Predicate;

@SuppressWarnings("UnstableApiUsage")
public abstract class AbstractCommand
{
    private final @NotNull GriefPrevention plugin;

    AbstractCommand(@NotNull GriefPrevention plugin)
    {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    protected @NotNull GriefPrevention plugin()
    {
        return plugin;
    }

    protected @NotNull Logger logger()
    {
        return plugin.getSLF4JLogger();
    }

    public abstract void register(@NotNull Commands commands);

    protected final Predicate<CommandSourceStack> playerWithPermission(@NotNull String name)
    {
        Objects.requireNonNull(name, "name");
        return ctx -> ctx.getSender() instanceof Player && ctx.getSender().hasPermission(name);
    }
}
