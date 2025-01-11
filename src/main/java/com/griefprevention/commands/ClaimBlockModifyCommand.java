package com.griefprevention.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.ryanhamshire.GriefPrevention.CustomLogEntryTypes;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ClaimBlockModifyCommand extends AbstractCommand
{
    private static final List<String> SUGGESTIONS = List.of("100", "1000", "10000");

    public ClaimBlockModifyCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("adjustbonusclaimblocksall")
                .requires(ctx -> ctx.getSender().hasPermission("griefprevention.adjustclaimblocks"))
                .then(argument("targets", ArgumentTypes.players())
                .then(argument("amount", IntegerArgumentType.integer(1)).suggests((context, builder) -> {
                    return CommandUtil.suggest(SUGGESTIONS, builder);
                }).executes(ctx ->
                {
                    List<Player> targetsList = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                    return adjustBonusClaimBlocks(ctx.getSource(), targetsList, amount);
                })))
                .build(),
                "Adds or subtracts bonus claim blocks for all online players.",
                List.of("acball"));

        commands.register(literal("adjustbonusclaimblocks")
                .requires(ctx -> ctx.getSender().hasPermission("griefprevention.adjustclaimblocks"))
                .then(argument("target", ArgumentTypes.player())
                .then(argument("amount", IntegerArgumentType.integer(1)).suggests((context, builder) -> {
                    return CommandUtil.suggest(SUGGESTIONS, builder);
                }).executes(ctx -> {
                    List<Player> targetsList = ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                    return adjustBonusClaimBlocks(ctx.getSource(), targetsList, amount);
                })))
                .build(),
                "Adds or subtracts bonus claim blocks for a player.",
                List.of("acb"));

        commands.register(literal("setaccruedclaimblocks")
                .requires(playerWithPermission("griefprevention.adjustclaimblocks"))
                .then(argument("targets", ArgumentTypes.players())
                .then(argument("value", IntegerArgumentType.integer(0)).executes(ctx ->
                {
                    List<Player> targetsList = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    return setAccruedClaimBlocks(ctx.getSource(), targetsList, value);
                })))
                .build(),
                "Updates a player's accrued claim block total.",
                List.of("scb"));
    }

    private int adjustBonusClaimBlocks(@NotNull CommandSourceStack source, @NotNull List<Player> targetsList, final int amount)
    {
        Player player = (Player) source.getSender();

        int count = 0;
        StringBuilder builder = new StringBuilder();

        for (Player onlinePlayer : targetsList)
        {
            count++;
            UUID playerID = onlinePlayer.getUniqueId();
            PlayerData playerData = plugin().dataStore.getPlayerData(playerID);

            int newAmount;
            try
            {
                newAmount = Math.addExact(playerData.getBonusClaimBlocks(), amount);
            }
            catch (ArithmeticException ignored)
            {
                newAmount = Integer.MAX_VALUE;
                logger().warn("{} reached maximum bonus claim blocks!", onlinePlayer.getName());
            }

            playerData.setBonusClaimBlocks(newAmount);
            plugin().dataStore.savePlayerData(playerID, playerData);
            builder.append(onlinePlayer.getName()).append(' ');
        }

        if (count >= 1)
        {
            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.AdjustBlocksAllSuccess, Integer.toString(amount));
            GriefPrevention.AddLogEntry("Adjusted all " + count + "players' bonus claim blocks by " + amount + ". " + builder, CustomLogEntryTypes.AdminActivity);
        }

        return count;
    }

    private int setAccruedClaimBlocks(@NotNull CommandSourceStack source, @NotNull List<Player> targetsList, final int value)
    {
        Player player = (Player) source.getSender();

        if (targetsList.isEmpty())
        {
            return 0;
        }

        int count = 0;

        for (Player target : targetsList)
        {
            count++;
            //set player's blocks
            PlayerData playerData = plugin().dataStore.getPlayerData(target.getUniqueId());
            playerData.setAccruedClaimBlocks(value);
            plugin().dataStore.savePlayerData(target.getUniqueId(), playerData);
            logger().info("{} set {} accrued claim blocks to {}.", source.getSender().getName(), target.getName(),
                    String.format(Locale.ROOT, "%,d", value));
        }

        if (count >= 1)
        {
            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.SetClaimBlocksSuccess);
        }

        return count;
    }
}
