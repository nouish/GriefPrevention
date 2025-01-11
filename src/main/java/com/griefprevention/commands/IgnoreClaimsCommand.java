package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class IgnoreClaimsCommand extends AbstractCommand
{
    public IgnoreClaimsCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("ignoreclaims")
                .requires(playerWithPermission("griefprevention.ignoreclaims"))
                .executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
                    playerData.ignoreClaims = !playerData.ignoreClaims;
                    if (!playerData.ignoreClaims)
                    {
                        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.RespectingClaims);
                    }
                    else
                    {
                        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.IgnoringClaims);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
                "Toggles ignore claims mode.",
                List.of("ic"));
    }
}
