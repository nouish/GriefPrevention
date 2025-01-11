package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class RestrictSubClaimCommand extends AbstractClaimCommand
{
    public RestrictSubClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("restrictsubclaim")
                .requires(playerWithPermission("griefprevention.claims"))
                .executes(ctx -> restrictSubClaim(ctx.getSource()))
                .build(),
                "Restricts a subclaim, so that it inherits no permissions from the parent claim",
                List.of("rsc"));
    }

    private int restrictSubClaim(@NotNull CommandSourceStack source)
    {
        Player player = (Player) source.getSender();
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        Claim claim = plugin().dataStore.getClaimAt(player.getLocation(), true, playerData.lastClaim);

        if (claim == null || claim.parent == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.StandInSubclaim);
            return 0;
        }

        // If player has /ignoreclaims on, continue
        // If admin claim, fail if this user is not an admin
        // If not an admin claim, fail if this user is not the owner
        if (!playerData.ignoreClaims && (claim.isAdminClaim() ? !player.hasPermission("griefprevention.adminclaims") : !player.getUniqueId().equals(claim.parent.ownerID)))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.OnlyOwnersModifyClaims, claim.getOwnerName());
            return 0;
        }

        if (claim.getSubclaimRestrictions())
        {
            claim.setSubclaimRestrictions(false);
            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.SubclaimUnrestricted);
        }
        else
        {
            claim.setSubclaimRestrictions(true);
            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.SubclaimRestricted);
        }

        plugin().dataStore.saveClaim(claim);
        return Command.SINGLE_SUCCESS;
    }
}
