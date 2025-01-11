package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class DeleteClaimCommands extends AbstractCommand
{
    public DeleteClaimCommands(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("deleteclaim")
                .requires(playerWithPermission("griefprevention.deleteclaims"))
                .executes(ctx -> deleteClaim(ctx.getSource()))
                .build(),
                "Deletes the claim you're standing in, even if it's not your claim.");

        commands.register(literal("deleteallclaims")
                .requires(ctx -> ctx.getSender().hasPermission("griefprevention.deleteclaims"))
                .then(argument("target", ArgumentTypes.player()).executes(ctx ->
                {
                    List<Player> targetsList = ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());

                    if (targetsList.isEmpty())
                    {
                        GriefPrevention.sendMessage(ctx.getSource().getSender(), ChatColor.RED, Messages.PlayerNotFound2);
                        return 0;
                    }

                    return deleteAllClaims(ctx.getSource(), targetsList.getFirst());
                }))
                .build(),
                "Deletes all of another player's claims.");

        commands.register(literal("deleteclaimsinworld")
                .requires(playerWithPermission("griefprevention.deleteclaimsinworld"))
                .then(argument("world", ArgumentTypes.world()).executes(ctx -> {
                    World world = ctx.getArgument("world", World.class);
                    return deleteClaimsInWorld(ctx.getSource(), world, true);
                }))
                .build(),
                "Deletes all the claims in a world. Only usable at the server console.",
                List.of("deleteallclaimsinworld", "clearclaimsinworld", "clearallclaimsinworld"));

        commands.register(literal("deleteuserclaimsinworld")
                .requires(playerWithPermission("griefprevention.deleteclaimsinworld"))
                .then(argument("world", ArgumentTypes.world()).executes(ctx -> {
                    World world = ctx.getArgument("world", World.class);
                    return deleteClaimsInWorld(ctx.getSource(), world, false);
                }))
                .build(),
                "Deletes all the non-admin claims in a world. Only usable at the server console.",
                List.of("deletealluserclaimsinworld", "clearuserclaimsinworld", "clearalluserclaimsinworld"));

        commands.register(literal("deletealladminclaims")
                .requires(ctx -> ctx.getSender().hasPermission("griefprevention.adminclaims")
                    && ctx.getSender().hasPermission("griefprevention.deleteclaims"))
                .executes(ctx -> deleteAllAdminClaims(ctx.getSource()))
                .build(),
                "Deletes all administrative claims.");
    }

    private int deleteClaim(@NotNull CommandSourceStack source)
    {
        Player player = (Player) source.getSender();

        //determine which claim the player is standing in
        Claim claim = plugin().dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);

        if (claim == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.DeleteClaimMissing);
            return 0;
        }

        //deleting an admin claim additionally requires the adminclaims permission
        if (claim.isAdminClaim() && !player.hasPermission("griefprevention.adminclaims"))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.CantDeleteAdminClaim);
            return 0;
        }

        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        if (!claim.children.isEmpty() && !playerData.warnedAboutMajorDeletion)
        {
            playerData.warnedAboutMajorDeletion = true;
            GriefPrevention.sendMessage(player, ChatColor.GOLD, Messages.DeletionSubdivisionWarning);
        }
        else
        {
            claim.removeSurfaceFluids(null);
            plugin().dataStore.deleteClaim(claim, true, true);

            //if in a creative mode world, /restorenature the claim
            if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner()) || GriefPrevention.instance.config_claims_survivalAutoNatureRestoration)
            {
                GriefPrevention.instance.restoreClaim(claim, 0);
            }

            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.DeleteSuccess);
            GriefPrevention.AddLogEntry(player.getName() + " deleted " + claim.getOwnerName() + "'s claim at " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()), CustomLogEntryTypes.AdminActivity);

            //revert any current visualization
            playerData.setVisibleBoundaries(null);
            playerData.warnedAboutMajorDeletion = false;
        }

        return Command.SINGLE_SUCCESS;
    }

    private int deleteAllClaims(@NotNull CommandSourceStack source, @NotNull Player target)
    {
        //delete all that player's claims
        plugin().dataStore.deleteClaimsForPlayer(target.getUniqueId(), true);

        GriefPrevention.sendMessage(source.getSender(), ChatColor.GREEN, Messages.DeleteAllSuccess, target.getName());

        if (source.getSender() instanceof Player player)
        {
            GriefPrevention.AddLogEntry(player.getName() + " deleted all claims belonging to " + target.getName() + ".", CustomLogEntryTypes.AdminActivity);

            //revert any current visualization
            if (player.isOnline())
            {
                plugin().dataStore.getPlayerData(player.getUniqueId()).setVisibleBoundaries(null);
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private int deleteClaimsInWorld(@NotNull CommandSourceStack source, @NotNull World world, boolean includeAdmins)
    {
        Objects.requireNonNull(source);
        plugin().dataStore.deleteClaimsInWorld(world, true);

        if (includeAdmins)
        {
            GriefPrevention.AddLogEntry("Deleted all claims in world: " + world.getName() + ".", CustomLogEntryTypes.AdminActivity);
        }
        else
        {
            GriefPrevention.AddLogEntry("Deleted all user claims in world: " + world.getName() + ".", CustomLogEntryTypes.AdminActivity);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int deleteAllAdminClaims(@NotNull CommandSourceStack source)
    {
        //delete all admin claims
        plugin().dataStore.deleteClaimsForPlayer(null, true);  //null for owner id indicates an administrative claim

        if (source.getSender() instanceof Player player)
        {
            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.AllAdminDeleted);
            GriefPrevention.AddLogEntry(player.getName() + " deleted all administrative claims.", CustomLogEntryTypes.AdminActivity);

            //revert any current visualization
            plugin().dataStore.getPlayerData(player.getUniqueId()).setVisibleBoundaries(null);
        }
        else
        {
            GriefPrevention.sendMessage(null, ChatColor.GREEN, Messages.AllAdminDeleted);
        }

        return Command.SINGLE_SUCCESS;
    }
}
