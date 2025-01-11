package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class SiegeCommand extends AbstractCommand
{
    public SiegeCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("siege")
                .requires(playerWithPermission("griefprevention.siege"))
                .then(argument("target", ArgumentTypes.player()).suggests((context, builder) ->
                {
                    List<String> suggestions = new ArrayList<>();

                    for (Player player : Bukkit.getOnlinePlayers())
                    {
                        if (!Objects.equals(player, context.getSource().getSender()))
                        {
                            suggestions.add(player.getName());
                        }
                    }

                    return CommandUtil.suggest(suggestions, builder);
                })
                .executes(ctx -> siege(ctx.getSource(), ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()))))
                .build(),
                "Initiates a siege versus another player.");
    }

    private int siege(@NotNull CommandSourceStack source, @NotNull List<Player> targetsList)
    {
        Player player = (Player) source.getSender();

        //error message for when siege mode is disabled
        if (!plugin().siegeEnabledForWorld(player.getWorld()))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NonSiegeWorld);
            return 0;
        }

        //requires one argument
        if (targetsList.isEmpty())
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.PlayerNotFound2);
            return 0;
        }

        //can't start a siege when you're already involved in one
        PlayerData attackerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        if (attackerData.siegeData != null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.AlreadySieging);
            return 0;
        }

        //can't start a siege when you're protected from pvp combat
        if (attackerData.pvpImmune)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.CantFightWhileImmune);
            return 0;
        }

        //if a player name was specified, use that
        Player defender = targetsList.getFirst();

        // First off, you cannot siege yourself, that's just silly:
        if (player.getName().equals(defender.getName()))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NoSiegeYourself);
            return 0;
        }

        //victim must not have the permission which makes him immune to siege
        if (defender.hasPermission("griefprevention.siegeimmune"))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.SiegeImmune);
            return 0;
        }

        //victim must not be under siege already
        PlayerData defenderData = plugin().dataStore.getPlayerData(defender.getUniqueId());
        if (defenderData.siegeData != null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.AlreadyUnderSiegePlayer);
            return 0;
        }

        //victim must not be pvp immune
        if (defenderData.pvpImmune)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NoSiegeDefenseless);
            return 0;
        }

        Claim defenderClaim = plugin().dataStore.getClaimAt(defender.getLocation(), false, null);

        //defender must have some level of permission there to be protected
        if (defenderClaim == null || defenderClaim.checkPermission(defender, ClaimPermission.Access, null) != null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NotSiegableThere);
            return 0;
        }

        //attacker must be close to the claim he wants to siege
        if (!defenderClaim.isNear(player.getLocation(), 25))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.SiegeTooFarAway);
            return 0;
        }

        //claim can't be under siege already
        if (defenderClaim.siegeData != null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.AlreadyUnderSiegeArea);
            return 0;
        }

        //can't siege admin claims
        if (defenderClaim.isAdminClaim())
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NoSiegeAdminClaim);
            return 0;
        }

        //can't be on cooldown
        if (plugin().dataStore.onCooldown(player, defender, defenderClaim))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.SiegeOnCooldown);
            return 0;
        }

        //start the siege
        plugin().dataStore.startSiege(player, defender, defenderClaim);

        //confirmation message for attacker, warning message for defender
        GriefPrevention.sendMessage(defender, ChatColor.GOLD, Messages.SiegeAlert, player.getName());
        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.SiegeConfirmed, defender.getName());
        return Command.SINGLE_SUCCESS;
    }
}
