package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.PlayerRescueTask;
import me.ryanhamshire.GriefPrevention.events.SaveTrappedPlayerEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class TrappedCommand extends AbstractClaimCommand
{
    public TrappedCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("trapped")
                .requires(playerWithPermission("griefprevention.trapped"))
                .executes(ctx -> handleTrapped(ctx.getSource()))
                .build(),
                "Ejects you to nearby unclaimed land. Has a substantial cooldown period.");
    }

    private int handleTrapped(@NotNull CommandSourceStack source)
    {
        //FEATURE: empower players who get "stuck" in an area where they don't have permission to build to save themselves

        Player player = (Player) source.getSender();
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        Claim claim = claim(source).orElse(null);

        //if another /trapped is pending, ignore this slash command
        if (playerData.pendingTrapped)
        {
            return 0;
        }

        //if the player isn't in a claim or has permission to build, tell him to man up
        if (claim == null || claim.checkPermission(player, ClaimPermission.Build, null) == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NotTrappedHere);
            return 0;
        }

        //rescue destination may be set by GPFlags or other plugin, ask to find out
        SaveTrappedPlayerEvent event = new SaveTrappedPlayerEvent(claim);
        Bukkit.getPluginManager().callEvent(event);

        //if the player is in the nether or end, he's screwed (there's no way to programmatically find a safe place for him)
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL && event.getDestination() == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.TrappedWontWorkHere);
            return 0;
        }

        //if the player is in an administrative claim and AllowTrappedInAdminClaims is false, he should contact an admin
        if (!GriefPrevention.instance.config_claims_allowTrappedInAdminClaims && claim.isAdminClaim() && event.getDestination() == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.TrappedWontWorkHere);
            return 0;
        }
        //send instructions
        GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.RescuePending);

        //create a task to rescue this player in a little while
        PlayerRescueTask task = new PlayerRescueTask(player, player.getLocation(), event.getDestination());
        plugin().getServer().getScheduler().scheduleSyncDelayedTask(plugin(), task, 200L);  //20L ~ 1 second
        return Command.SINGLE_SUCCESS;
    }
}
