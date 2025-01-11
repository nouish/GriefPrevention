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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class UntrustCommand extends AbstractClaimCommand
{
    public UntrustCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("untrust")
                        .requires(playerWithPermission("griefprevention.claims"))
                        .then(targetAsArgument()
                        .executes(ctx -> untrust(ctx.getSource(), StringArgumentType.getString(ctx, "target"))))
                        .build(),
                "Revokes a player's access to your claim(s).",
                List.of("ut"));
    }

    private int untrust(@NotNull CommandSourceStack source, @NotNull String target)
    {
        Player player = (Player) source.getSender();
        Claim claim = claim(source).orElse(null);
        //determine whether a single player or clearing permissions entirely
        boolean clearPermissions = false;
        OfflinePlayer otherPlayer = null;

        if (target.equals("all"))
        {
            if (claim == null || claim.checkPermission(player, ClaimPermission.Edit, null) == null)
            {
                clearPermissions = true;
            }
            else
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.ClearPermsOwnerOnly);
                return 0;
            }
        }
        else
        {
            //validate player argument or group argument
            if (!target.startsWith("[") || !target.endsWith("]"))
            {
                otherPlayer = plugin().resolvePlayerByName(target);
                if (!clearPermissions && otherPlayer == null && !target.equals("public"))
                {
                    //bracket any permissions - at this point it must be a permission without brackets
                    if (target.contains("."))
                    {
                        target = "[" + target + "]";
                    }
                    else
                    {
                        GriefPrevention.sendMessage(player, ChatColor.RED, Messages.PlayerNotFound2);
                        return 0;
                    }
                }

                //correct to proper casing
                if (otherPlayer != null)
                    target = otherPlayer.getName();
            }
        }

        //if no claim here, apply changes to all his claims
        if (claim == null)
        {
            PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());

            String idToDrop = target;
            if (otherPlayer != null)
            {
                idToDrop = otherPlayer.getUniqueId().toString();
            }

            //calling event
            TrustChangedEvent event = new TrustChangedEvent(player, playerData.getClaims(), null, false, idToDrop);
            Bukkit.getPluginManager().callEvent(event);

            if (event.isCancelled())
            {
                return 0;
            }

            //dropping permissions
            for (Claim targetClaim : event.getClaims())
            {
                claim = targetClaim;

                //if untrusting "all" drop all permissions
                if (clearPermissions)
                {
                    claim.clearPermissions();
                }

                //otherwise drop individual permissions
                else
                {
                    claim.dropPermission(idToDrop);
                    claim.managers.remove(idToDrop);
                }

                //save changes
                plugin().dataStore.saveClaim(claim);
            }

            //beautify for output
            if (target.equals("public"))
            {
                target = "the public";
            }

            //confirmation message
            if (!clearPermissions)
            {
                GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.UntrustIndividualAllClaims, target);
            }
            else
            {
                GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.UntrustEveryoneAllClaims);
            }
        }

        //otherwise, apply changes to only this claim
        else if (claim.checkPermission(player, ClaimPermission.Manage, null) != null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NoPermissionTrust, claim.getOwnerName());
            return 0;
        }
        else
        {
            //if clearing all
            if (clearPermissions)
            {
                //requires owner
                if (claim.checkPermission(player, ClaimPermission.Edit, null) != null)
                {
                    GriefPrevention.sendMessage(player, ChatColor.RED, Messages.UntrustAllOwnerOnly);
                    return 0;
                }

                //calling the event
                TrustChangedEvent event = new TrustChangedEvent(player, claim, null, false, target);
                Bukkit.getPluginManager().callEvent(event);

                if (event.isCancelled())
                {
                    return 0;
                }

                event.getClaims().forEach(Claim::clearPermissions);
                GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.ClearPermissionsOneClaim);
            }

            //otherwise individual permission drop
            else
            {
                String idToDrop = target;
                if (otherPlayer != null)
                {
                    idToDrop = otherPlayer.getUniqueId().toString();
                }
                boolean targetIsManager = claim.managers.contains(idToDrop);
                if (targetIsManager && claim.checkPermission(player, ClaimPermission.Edit, null) != null)  //only claim owners can untrust managers
                {
                    GriefPrevention.sendMessage(player, ChatColor.RED, Messages.ManagersDontUntrustManagers, claim.getOwnerName());
                    return 0;
                }
                else
                {
                    //calling the event
                    TrustChangedEvent event = new TrustChangedEvent(player, claim, null, false, idToDrop);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled())
                    {
                        return 0;
                    }

                    event.getClaims().forEach(targetClaim -> targetClaim.dropPermission(event.getIdentifier()));

                    //beautify for output
                    if (target.equals("public"))
                    {
                        target = "the public";
                    }

                    GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.UntrustIndividualSingleClaim, target);
                }
            }

            //save changes
            plugin().dataStore.saveClaim(claim);
        }

        return Command.SINGLE_SUCCESS;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> targetAsArgument()
    {
        return argument("target", StringArgumentType.string()).suggests((ctx, builder) ->
        {
            List<String> suggestions = new ArrayList<>();
            List<String> trustees = getTrustees(claim(ctx.getSource()).orElse(null));

            for (String option : trustees)
            {
                if (option.length() == 36)
                {
                    try
                    {
                        UUID uuid = UUID.fromString(option);
                        suggestions.add(GriefPrevention.lookupPlayerName(uuid));
                    }
                    catch (IllegalArgumentException ignored)
                    {
                        // Not a valid UUID
                        suggestions.add(option);
                    }
                }
                else
                {
                    suggestions.add(option);
                }
            }

            suggestions.add("all");
            return CommandUtil.suggest(suggestions, builder);
        });
    }

    private List<String> getTrustees(Claim claim)
    {
        if (claim == null)
        {
            return List.of();
        }
        ArrayList<String> list = new ArrayList<>();
        claim.getPermissions(list, list, list, list);
        return list.stream().distinct().toList();
    }
}
