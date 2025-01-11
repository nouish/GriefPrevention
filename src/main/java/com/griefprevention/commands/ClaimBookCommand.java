package com.griefprevention.commands;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.WelcomeTask;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

@SuppressWarnings("UnstableApiUsage")
public final class ClaimBookCommand extends AbstractCommand
{
    public ClaimBookCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(
                literal("claimbook")
                        .requires(playerWithPermission("griefprevention.claimbook"))
                        .then(argument("targets", ArgumentTypes.players()).executes(ctx ->
                        {
                            Player player = (Player) ctx.getSource().getSender();
                            List<Player> targetList = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());

                            if (targetList.isEmpty())
                            {
                                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.PlayerNotFound2);
                                return 0;
                            }

                            int count = 0;

                            for (Player target : targetList)
                            {
                                count++;
                                new WelcomeTask(target).run();
                                logger().info("{} gave claimbook to {}.", player.getName(), target.getName());
                            }

                            return count;
                        }))
                        .build(),
                "Gives a player a manual about claiming land.");
    }
}
