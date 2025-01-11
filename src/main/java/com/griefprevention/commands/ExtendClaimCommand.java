package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ExtendClaimCommand extends AbstractCommand
{
    public ExtendClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("extendclaim")
                .requires(playerWithPermission("griefprevention.claims"))
                .then(argument("amount", IntegerArgumentType.integer(0)).suggests((context, builder) ->
                {
                    Stream<String> suggestions = Stream.of(10).map(String::valueOf);
                    return CommandUtil.suggest(suggestions, builder);
                }).executes(ctx ->
                {
                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                    return extendClaim(ctx.getSource(), amount);
                }))
                .build(),
                "Resizes the land claim you're standing in by pushing or pulling its boundary in the direction you're facing.",
                List.of("expandclaim", "resizeclaim"));
    }

    private int extendClaim(@NotNull CommandSourceStack source, final int amount)
    {
        Player player = (Player) source.getSender();

//        if (args.length < 1)
//        {
//            //link to a video demo of land claiming, based on world type
//            if (GriefPrevention.instance.creativeRulesApply(player.getLocation()))
//            {
//                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
//            }
//            else if (GriefPrevention.instance.claimsEnabledForWorld(player.getLocation().getWorld()))
//            {
//                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
//            }
//            return false;
//        }

        //requires claim modification tool in hand
        if (player.getGameMode() != GameMode.CREATIVE
                && player.getItemInHand().getType() != GriefPrevention.instance.config_claims_modificationTool)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.MustHoldModificationToolForThat);
            return 0;
        }

        //must be standing in a land claim
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        Claim claim = plugin().dataStore.getClaimAt(player.getLocation(), true, playerData.lastClaim);
        if (claim == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.StandInClaimToResize);
            return 0;
        }

        //must have permission to edit the land claim you're in
        Supplier<String> errorMessage = claim.checkPermission(player, ClaimPermission.Edit, null);
        if (errorMessage != null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NotYourClaim);
            return 0;
        }

        //determine new corner coordinates
        org.bukkit.util.Vector direction = player.getLocation().getDirection();
        if (direction.getY() > .75)
        {
            GriefPrevention.sendMessage(player, ChatColor.AQUA, Messages.ClaimsExtendToSky);
            return 0;
        }

        if (direction.getY() < -.75)
        {
            GriefPrevention.sendMessage(player, ChatColor.AQUA, Messages.ClaimsAutoExtendDownward);
            return 0;
        }

        Location minCorner = claim.getLesserBoundaryCorner();
        Location maxCorner = claim.getGreaterBoundaryCorner();
        int newx1 = minCorner.getBlockX();
        int newx2 = maxCorner.getBlockX();
        int newy1 = minCorner.getBlockY();
        int newy2 = maxCorner.getBlockY();
        int newz1 = minCorner.getBlockZ();
        int newz2 = maxCorner.getBlockZ();

        //if changing Z only
        if (Math.abs(direction.getX()) < .3)
        {
            if (direction.getZ() > 0)
            {
                newz2 += amount;  //north
            }
            else
            {
                newz1 -= amount;  //south
            }
        }

        //if changing X only
        else if (Math.abs(direction.getZ()) < .3)
        {
            if (direction.getX() > 0)
            {
                newx2 += amount;  //east
            }
            else
            {
                newx1 -= amount;  //west
            }
        }

        //diagonals
        else
        {
            if (direction.getX() > 0)
            {
                newx2 += amount;
            }
            else
            {
                newx1 -= amount;
            }

            if (direction.getZ() > 0)
            {
                newz2 += amount;
            }
            else
            {
                newz1 -= amount;
            }
        }

        //attempt resize
        playerData.claimResizing = claim;
        plugin().dataStore.resizeClaimWithChecks(player, playerData, newx1, newx2, newy1, newy2, newz1, newz2);
        playerData.claimResizing = null;
        return Command.SINGLE_SUCCESS;
    }
}
