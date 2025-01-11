package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.EconomyHandler;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class BlockEconomyCommands extends AbstractCommand
{
    public BlockEconomyCommands(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("buyclaimblocks")
                .requires(playerWithPermission("griefprevention.buysellclaimblocks"))
                .executes(ctx -> blockPurchaseCost(ctx.getSource()))
                .then(argument("amount", IntegerArgumentType.integer(1)).executes(ctx ->
                {
                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                    return buyClaimBlocks(ctx.getSource(), amount);
                }))
                .build(),
                "Purchases additional claim blocks with server money. Doesn't work on servers without a Vault-compatible economy plugin.",
                List.of("buyclaim"));

        commands.register(literal("sellclaimblocks")
                .requires(playerWithPermission("griefprevention.buysellclaimblocks"))
                .executes(ctx -> blockSaleValue(ctx.getSource()))
                .then(argument("amount", IntegerArgumentType.integer(1)).executes(ctx ->
                {
                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                    return sellClaimBlocks(ctx.getSource(), amount);
                }))
                .build(),
                "Sells your claim blocks for server money. Doesn't work on servers without a Vault-compatible economy plugin.",
                List.of("sellclaim"));
    }

    private boolean isEnabled(@NotNull Player player)
    {
        if (!player.hasPermission("griefprevention.buysellclaimblocks"))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NoPermissionForCommand);
            return true;
        }

        EconomyHandler.EconomyWrapper wrapper = plugin().economyHandler.getWrapper();

        if (wrapper == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.BuySellNotConfigured);
            return false;
        }

        return true;
    }

    private int blockPurchaseCost(@NotNull CommandSourceStack source)
    {
        Player player = (Player) source.getSender();

        if (!isEnabled(player))
        {
            return 0;
        }

        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.BlockPurchaseCost,
            String.valueOf(GriefPrevention.instance.config_economy_claimBlocksPurchaseCost),
            String.valueOf(plugin().economyHandler.getWrapper().getEconomy().getBalance(player)));
        return Command.SINGLE_SUCCESS;
    }

    private int buyClaimBlocks(@NotNull CommandSourceStack source, final int amount)
    {
        Player player = (Player) source.getSender();

        if (!isEnabled(player))
        {
            return 0;
        }

        //if purchase disabled, send error message
        if (GriefPrevention.instance.config_economy_claimBlocksPurchaseCost == 0)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.OnlySellBlocks);
            return 0;
        }

        Economy economy = plugin().economyHandler.getWrapper().getEconomy();
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());

        //if the player can't afford his purchase, send error message
        double balance = economy.getBalance(player);
        double totalCost = amount * GriefPrevention.instance.config_economy_claimBlocksPurchaseCost;

        if (totalCost > balance)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.InsufficientFunds,
                    economy.format(totalCost),
                    economy.format(balance));
            return 0;
        }

        int newBonusClaimBlocks = Math.addExact(playerData.getBonusClaimBlocks(), amount);

        //if the player is going to reach max bonus limit, send error message
        int bonusBlocksLimit = GriefPrevention.instance.config_economy_claimBlocksMaxBonus;
        if (bonusBlocksLimit != 0 && newBonusClaimBlocks > bonusBlocksLimit)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.MaxBonusReached,
                    String.format(Locale.ROOT, "%,d", amount),
                    String.format(Locale.ROOT, "%,d", bonusBlocksLimit));
            return 0;
        }

        //withdraw cost
        economy.withdrawPlayer(player, totalCost);

        //add blocks
        playerData.setBonusClaimBlocks(Math.addExact(playerData.getBonusClaimBlocks(), amount));
        plugin().dataStore.savePlayerData(player.getUniqueId(), playerData);

        //inform player
        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.PurchaseConfirmation,
                economy.format(totalCost),
                String.format(Locale.ROOT, "%,d", playerData.getRemainingClaimBlocks()));
        return Command.SINGLE_SUCCESS;
    }

    private int blockSaleValue(@NotNull CommandSourceStack source)
    {
        Player player = (Player) source.getSender();
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        GriefPrevention.sendMessage(player, ChatColor.AQUA, Messages.BlockSaleValue,
            String.format(Locale.ROOT, "%,d", GriefPrevention.instance.config_economy_claimBlocksSellValue),
            String.format(Locale.ROOT, "%,d", playerData.getRemainingClaimBlocks()));
        return Command.SINGLE_SUCCESS;
    }

    private int sellClaimBlocks(@NotNull CommandSourceStack source, final int amount)
    {
        Player player = (Player) source.getSender();

        if (!isEnabled(player))
        {
            return 0;
        }

        //if disabled, error message
        if (GriefPrevention.instance.config_economy_claimBlocksSellValue == 0)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.OnlyPurchaseBlocks);
            return 0;
        }

        //load player data
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        int availableBlocks = playerData.getRemainingClaimBlocks();

        //if he doesn't have enough blocks, tell him so
        if (amount > availableBlocks)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NotEnoughBlocksForSale);
            return 0;
        }

        Economy economy = plugin().economyHandler.getWrapper().getEconomy();

        //compute value and deposit it
        double totalValue = amount * GriefPrevention.instance.config_economy_claimBlocksSellValue;
        economy.depositPlayer(player, totalValue);

        //subtract blocks
        playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() - amount);
        plugin().dataStore.savePlayerData(player.getUniqueId(), playerData);

        //inform player
        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.BlockSaleConfirmation,
            economy.format(totalValue),
            String.format(Locale.ROOT, "%,d", playerData.getRemainingClaimBlocks()));
        return Command.SINGLE_SUCCESS;
    }
}
