package com.griefprevention.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public abstract class AbstractClaimCommand extends AbstractCommand
{
    AbstractClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    protected Optional<Claim> claim(@NotNull CommandSourceStack context)
    {
        if (context.getSender() instanceof Player player)
        {
            Claim claim = plugin().dataStore.getClaimAt(player.getLocation(), /* ignore height = */ true, null);
            return Optional.ofNullable(claim);
        }
        else
        {
            return Optional.empty();
        }
    }
}
