package com.griefprevention.commands;

import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.AutoExtendClaimTask;
import me.ryanhamshire.GriefPrevention.CreateClaimResult;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.ShovelMode;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

@SuppressWarnings("deprecation")
public final class CreateClaimCommand extends AbstractCommand
{
    private static final Collection<String> SUGGESTIONS = List.of("4", "8", "16", "32", "64");

    public CreateClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("claim")
                .requires(playerWithPermission("griefprevention.claims"))
                .executes(ctx -> createClaim(ctx.getSource(), -1))
                .then(argument("radius", IntegerArgumentType.integer(1))
                        .suggests((ctx, builder) -> CommandUtil.suggest(SUGGESTIONS, builder))
                .executes(ctx -> createClaim(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))
                .build(),
                "Creates a land claim centered at your current location.",
                List.of("createclaim", "makeclaim", "newclaim"));
    }

    private int createClaim(@NotNull CommandSourceStack source, final int value)
    {
        Player player = (Player) source.getSender();

        if (!GriefPrevention.instance.claimsEnabledForWorld(player.getWorld()))
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.ClaimsDisabledWorld);
            return 0;
        }

        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());

        //if he's at the claim count per player limit already and doesn't have permission to bypass, display an error message
        if (GriefPrevention.instance.config_claims_maxClaimsPerPlayer > 0 &&
                !player.hasPermission("griefprevention.overrideclaimcountlimit") &&
                playerData.getClaims().size() >= GriefPrevention.instance.config_claims_maxClaimsPerPlayer)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.ClaimCreationFailedOverClaimCountLimit);
            return 0;
        }

        //default is chest claim radius, unless -1
        int radius = GriefPrevention.instance.config_claims_automaticClaimsForNewPlayersRadius;
        if (radius < 0) radius = (int) Math.ceil(Math.sqrt(GriefPrevention.instance.config_claims_minArea) / 2);

        //if player has any claims, respect claim minimum size setting
        if (!playerData.getClaims().isEmpty())
        {
            //if player has exactly one land claim, this requires the claim modification tool to be in hand (or creative mode player)
            if (playerData.getClaims().size() == 1 && player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != GriefPrevention.instance.config_claims_modificationTool)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.MustHoldModificationToolForThat);
                return 0;
            }

            radius = (int) Math.ceil(Math.sqrt(GriefPrevention.instance.config_claims_minArea) / 2);
        }

        //allow for specifying the radius
        if (value > 0)
        {
            if (playerData.getClaims().size() < 2 && player.getGameMode() != GameMode.CREATIVE && player.getItemInHand().getType() != GriefPrevention.instance.config_claims_modificationTool)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.RadiusRequiresGoldenShovel);
                return 0;
            }

            if (value < radius)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.MinimumRadius, String.valueOf(radius));
                return 0;
            }
            else
            {
                radius = value;
            }
        }

        if (radius < 0) radius = 0;

        Location playerLoc = player.getLocation();
        int lesserX;
        int lesserZ;
        int greaterX;
        int greaterZ;
        try
        {
            lesserX = Math.subtractExact(playerLoc.getBlockX(), radius);
            lesserZ = Math.subtractExact(playerLoc.getBlockZ(), radius);
            greaterX = Math.addExact(playerLoc.getBlockX(), radius);
            greaterZ = Math.addExact(playerLoc.getBlockZ(), radius);
        }
        catch (ArithmeticException e)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.CreateClaimInsufficientBlocks, String.valueOf(Integer.MAX_VALUE));
            return 0;
        }

        World world = player.getWorld();

        int lesserY;
        try
        {
            lesserY = Math.subtractExact(Math.subtractExact(playerLoc.getBlockY(), plugin().config_claims_claimsExtendIntoGroundDistance), 1);
        }
        catch (ArithmeticException e)
        {
            lesserY = world.getMinHeight();
        }

        UUID ownerId;
        if (playerData.shovelMode == ShovelMode.Admin)
        {
            ownerId = null;
        }
        else
        {
            //player must have sufficient unused claim blocks
            int area;
            try
            {
                int dX = Math.addExact(Math.subtractExact(greaterX, lesserX), 1);
                int dZ = Math.addExact(Math.subtractExact(greaterZ, lesserZ), 1);
                area = Math.abs(Math.multiplyExact(dX, dZ));
            }
            catch (ArithmeticException e)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.CreateClaimInsufficientBlocks, String.valueOf(Integer.MAX_VALUE));
                return 0;
            }
            int remaining = playerData.getRemainingClaimBlocks();
            if (remaining < area)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.CreateClaimInsufficientBlocks, String.valueOf(area - remaining));
                plugin().dataStore.tryAdvertiseAdminAlternatives(player);
                return 0;
            }
            ownerId = player.getUniqueId();
        }

        CreateClaimResult result = plugin().dataStore.createClaim(world,
                lesserX, greaterX,
                lesserY,
                world.getMaxHeight(),
                lesserZ, greaterZ,
                ownerId, null, null, player);
        if (!result.succeeded || result.claim == null)
        {
            if (result.claim != null)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.CreateClaimFailOverlapShort);

                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE);
            }
            else
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.CreateClaimFailOverlapRegion);
            }
        }
        else
        {
            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.CreateClaimSuccess);

            //link to a video demo of land claiming, based on world type
            if (GriefPrevention.instance.creativeRulesApply(player.getLocation()))
            {
                GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.CreativeBasicsVideo2, DataStore.CREATIVE_VIDEO_URL);
            }
            else if (GriefPrevention.instance.claimsEnabledForWorld(player.getWorld()))
            {
                GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.SurvivalBasicsVideo2, DataStore.SURVIVAL_VIDEO_URL);
            }
            BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM);
            playerData.claimResizing = null;
            playerData.lastShovelLocation = null;
            AutoExtendClaimTask.scheduleAsync(result.claim);
        }

        return Command.SINGLE_SUCCESS;
    }
}
