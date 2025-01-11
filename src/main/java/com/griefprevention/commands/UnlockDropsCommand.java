package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
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

public final class UnlockDropsCommand extends AbstractCommand
{
    public UnlockDropsCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("unlockdrops")
                .requires(playerWithPermission("griefprevention.unlockdrops"))
                .executes(ctx -> {
                    return unlockDrops(ctx.getSource(), List.of());
                })
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
                }))
                .executes(ctx -> {
                    List<Player> targetList = ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
                    return unlockDrops(ctx.getSource(), targetList);
                })
                .build(),
                "Allows other players to pick up the items you dropped when you died.");
    }

    private int unlockDrops(@NotNull CommandSourceStack source, @NotNull List<Player> targetList)
    {
        Player player = (Player) source.getSender();
        PlayerData playerData;

        if (!targetList.isEmpty() && player.hasPermission("griefprevention.unlockothersdrops"))
        {
            Player otherPlayer = targetList.getFirst();
            if (otherPlayer == null)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.PlayerNotFound2);
                return 0;
            }

            playerData = plugin().dataStore.getPlayerData(otherPlayer.getUniqueId());
            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.DropUnlockOthersConfirmation, otherPlayer.getName());
        }
        else
        {
            playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.DropUnlockConfirmation);
        }

        playerData.dropsAreUnlocked = true;
        return Command.SINGLE_SUCCESS;
    }
}
