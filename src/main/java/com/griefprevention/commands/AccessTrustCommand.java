package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.events.TrustChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

@SuppressWarnings("UnstableApiUsage")
public final class AccessTrustCommand extends AbstractCommand
{
    public AccessTrustCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(
                literal("accesstrust")
                        .requires(playerWithPermission("griefprevention.claims"))
                        .then(targetAsArgument().executes(doTrustTarget(ClaimPermission.Access)))
                        .build(),
                "Grants a player entry to your claim(s) and use of your bed, buttons, and levers.",
                List.of("at"));

        commands.register(
                literal("trust")
                        .requires(playerWithPermission("griefprevention.claims"))
                        .then(targetAsArgument().executes(doTrustTarget(ClaimPermission.Build)))
                        .build(),
                "Grants a player full access to your claim(s).",
                List.of("tr"));

        commands.register(
                literal("containertrust")
                        .requires(playerWithPermission("griefprevention.claims"))
                        .then(targetAsArgument().executes(doTrustTarget(ClaimPermission.Inventory)))
                        .build(),
                "Grants a player access to your claim's containers, crops, animals, bed, buttons, and levers.",
                List.of("ct"));

        commands.register(
                literal("permissiontrust")
                        .requires(playerWithPermission("griefprevention.claims"))
                        .then(targetAsArgument().executes(doTrustTarget(null)))
                        .build(),
                "Grants a player permission to grant his level of permission to others.",
                List.of("pt"));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> targetAsArgument()
    {
        return argument("target", StringArgumentType.string()).suggests((ctx, builder) ->
        {
            List<String> suggestions = new ArrayList<>();
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(suggestions::add);
            suggestions.add("public");
            return CommandUtil.suggest(suggestions, builder);
        });
    }

    private Command<CommandSourceStack> doTrustTarget(@Nullable ClaimPermission permissionLevel)
    {
        return ctx -> {
            Player player = (Player) ctx.getSource().getSender();
            String target = ctx.getArgument("target", String.class);
            handleTrustCommand(player, permissionLevel, target);
            return Command.SINGLE_SUCCESS;
        };
    }

    //helper method keeps the trust commands consistent and eliminates duplicate code
    private void handleTrustCommand(Player player, ClaimPermission permissionLevel, String recipientName)
    {
        //determine which claim the player is standing in
        Claim claim = plugin().dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);

        //validate player or group argument
        String permission = null;
        OfflinePlayer otherPlayer = null;
        UUID recipientID = null;
        if (recipientName.startsWith("[") && recipientName.endsWith("]"))
        {
            permission = recipientName.substring(1, recipientName.length() - 1);
            if (permission == null || permission.isEmpty())
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.InvalidPermissionID);
                return;
            }
        }
        else
        {
            otherPlayer = plugin().resolvePlayerByName(recipientName);
            boolean isPermissionFormat = recipientName.contains(".");
            if (otherPlayer == null && !recipientName.equals("public") && !recipientName.equals("all") && !isPermissionFormat)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.PlayerNotFound2);
                return;
            }

            if (otherPlayer == null && isPermissionFormat)
            {
                //player does not exist and argument has a period so this is a permission instead
                permission = recipientName;
            }
            else if (otherPlayer != null)
            {
                recipientName = otherPlayer.getName();
                recipientID = otherPlayer.getUniqueId();
            }
            else
            {
                recipientName = "public";
            }
        }

        //determine which claims should be modified
        ArrayList<Claim> targetClaims = new ArrayList<>();
        if (claim == null)
        {
            PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
            targetClaims.addAll(playerData.getClaims());
        }
        else
        {
            //check permission here
            if (claim.checkPermission(player, ClaimPermission.Manage, null) != null)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NoPermissionTrust, claim.getOwnerName());
                return;
            }

            //see if the player has the level of permission he's trying to grant
            Supplier<String> errorMessage;

            //permission level null indicates granting permission trust
            if (permissionLevel == null)
            {
                errorMessage = claim.checkPermission(player, ClaimPermission.Edit, null);
                if (errorMessage != null)
                {
                    errorMessage = () -> "Only " + claim.getOwnerName() + " can grant /PermissionTrust here.";
                }
            }

            //otherwise just use the ClaimPermission enum values
            else
            {
                errorMessage = claim.checkPermission(player, permissionLevel, null);
            }

            //error message for trying to grant a permission the player doesn't have
            if (errorMessage != null)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.CantGrantThatPermission);
                return;
            }

            targetClaims.add(claim);
        }

        //if we didn't determine which claims to modify, tell the player to be specific
        if (targetClaims.isEmpty())
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.GrantPermissionNoClaim);
            return;
        }

        String identifierToAdd = recipientName;
        if (permission != null)
        {
            identifierToAdd = "[" + permission + "]";
            //replace recipientName as well so the success message clearly signals a permission
            recipientName = identifierToAdd;
        }
        else if (recipientID != null)
        {
            identifierToAdd = recipientID.toString();
        }

        //calling the event
        TrustChangedEvent event = new TrustChangedEvent(player, targetClaims, permissionLevel, true, identifierToAdd);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled())
        {
            return;
        }

        //apply changes
        for (Claim currentClaim : event.getClaims())
        {
            if (permissionLevel == null)
            {
                if (!currentClaim.managers.contains(identifierToAdd))
                {
                    currentClaim.managers.add(identifierToAdd);
                }
            }
            else
            {
                currentClaim.setPermission(identifierToAdd, permissionLevel);
            }
            plugin().dataStore.saveClaim(currentClaim);
        }

        //notify player
        if (recipientName.equals("public")) recipientName = plugin().dataStore.getMessage(Messages.CollectivePublic);
        String permissionDescription;
        if (permissionLevel == null)
        {
            permissionDescription = plugin().dataStore.getMessage(Messages.PermissionsPermission);
        }
        else if (permissionLevel == ClaimPermission.Build)
        {
            permissionDescription = plugin().dataStore.getMessage(Messages.BuildPermission);
        }
        else if (permissionLevel == ClaimPermission.Access)
        {
            permissionDescription = plugin().dataStore.getMessage(Messages.AccessPermission);
        }
        else //ClaimPermission.Inventory
        {
            permissionDescription = plugin().dataStore.getMessage(Messages.ContainersPermission);
        }

        String location;
        if (claim == null)
        {
            location = plugin().dataStore.getMessage(Messages.LocationAllClaims);
        }
        else
        {
            location = plugin().dataStore.getMessage(Messages.LocationCurrentClaim);
        }

        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.GrantPermissionConfirmation, recipientName, permissionDescription, location);
    }
}
