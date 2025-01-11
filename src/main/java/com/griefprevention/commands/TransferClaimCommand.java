package com.griefprevention.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class TransferClaimCommand extends AbstractClaimCommand
{
    public TransferClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("transferclaim")
                .requires(playerWithPermission("griefprevention.transferclaim"))
                .then(literal("admin").executes(ctx -> transferClaim(ctx.getSource(), /* Make admin claim */ null)))
                .then(targetAsArgument().executes(ctx -> transferClaim(ctx.getSource(), StringArgumentType.getString(ctx, "target"))))
                .build(),
                "Converts an administrative claim to a private claim.",
                List.of("giveclaim"));
    }

    private int transferClaim(@NotNull CommandSourceStack source, @Nullable String target)
    {
        Player player = (Player) source.getSender();
        Claim claim = claim(source).orElse(null);

        if (claim == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.TransferClaimMissing);
            return 0;
        }

        //check additional permission for admin claims
        if (claim.isAdminClaim() && !player.hasPermission("griefprevention.adminclaims"))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.TransferClaimPermission);
            return 0;
        }

        UUID newOwnerID = null;  //no argument = make an admin claim
        String ownerName = "admin";

        if (target != null)
        {
            OfflinePlayer targetPlayer = plugin().resolvePlayerByName(target);
            if (targetPlayer == null)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.PlayerNotFound2);
                return 0;
            }
            newOwnerID = targetPlayer.getUniqueId();
            ownerName = targetPlayer.getName();
        }

        //change ownerhsip
        try
        {
            plugin().dataStore.changeClaimOwner(claim, newOwnerID);
        }
        catch (DataStore.NoTransferException e)
        {
            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.TransferTopLevel);
            return 0;
        }

        //confirm
        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.TransferSuccess);
        GriefPrevention.AddLogEntry(player.getName() + " transferred a claim at " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + " to " + ownerName + ".", CustomLogEntryTypes.AdminActivity);
        return 1;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> targetAsArgument()
    {
        return argument("target", StringArgumentType.string()).suggests((ctx, builder) ->
        {
            Claim claim = claim(ctx.getSource()).orElse(null);
            String ownerName = claim != null ? claim.getOwnerName() : null;
            List<String> suggestions = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers())
            {
                if (!Objects.equals(ownerName, player.getName()))
                {
                    suggestions.add(player.getName());
                }
            }

            return CommandUtil.suggest(suggestions, builder);
        });
    }
}
