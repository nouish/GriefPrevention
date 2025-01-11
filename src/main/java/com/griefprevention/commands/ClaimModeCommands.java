package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.ShovelMode;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ClaimModeCommands extends AbstractCommand
{
    public ClaimModeCommands(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("adminclaims")
                .requires(playerWithPermission("griefprevention.adminclaims"))
                .executes(ctx ->
                {
                    Player player = (Player) ctx.getSource().getSender();
                    PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
                    playerData.shovelMode = ShovelMode.Admin;
                    GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.AdminClaimsMode);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
                "Switches the shovel tool to administrative claims mode.",
                List.of("ac"));

        commands.register(literal("basicclaims")
                .requires(playerWithPermission("griefprevention.claims"))
                .executes(ctx ->
                {
                    Player player = (Player) ctx.getSource().getSender();
                    PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
                    playerData.shovelMode = ShovelMode.Basic;
                    playerData.claimSubdividing = null;
                    GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.BasicClaimsMode);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
                "Switches the shovel tool back to basic claims mode.",
                List.of("bc"));

        commands.register(literal("subdivideclaims")
                .requires(playerWithPermission("griefprevention.claims"))
                .executes(ctx ->
                {
                    Player player = (Player) ctx.getSource().getSender();
                    PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
                    playerData.shovelMode = ShovelMode.Subdivide;
                    playerData.claimSubdividing = null;
                    GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.SubdivisionMode);
                    GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.SubdivisionVideo2, DataStore.SUBDIVISION_VIDEO_URL);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
                "Switches the shovel tool to subdivision mode, used to subdivide your claims.",
                List.of("subdivideclaim", "sc"));
    }
}
