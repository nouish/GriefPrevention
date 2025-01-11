package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static net.kyori.adventure.text.Component.text;

@SuppressWarnings("UnstableApiUsage")
public final class NameClaimCommand extends AbstractClaimCommand
{
    private static final Pattern CLAIM_NAME_PATTERN = Pattern.compile("[0-9a-zA-Z-_ ']{2,40}");

    public NameClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("nameclaim")
                .requires(playerWithPermission("griefprevention.name"))
                .executes(ctx -> renameTo(ctx.getSource(), null))
                .then(argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> renameTo(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))
                .build(),
                "Set the name for the current claim.");
    }

    private int renameTo(@NotNull CommandSourceStack context, @Nullable String newName) {
        Player player = (Player) context.getSender();

        // Validate new name
        if (newName != null && !CLAIM_NAME_PATTERN.matcher(newName).matches())
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.BadNameInput);
            return 0;
        }

        //which claim is being abandoned?
        Claim claim = claim(context).orElse(null);
        if (claim == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.DeleteClaimMissing);
            return 0;
        }

        //verify ownership
        if ((claim.isAdminClaim() && !player.hasPermission("griefprevention.adminclaims"))
                || (!claim.isAdminClaim() && !player.getUniqueId().equals(claim.getOwnerID())))
        {
            PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
            if (!playerData.ignoreClaims)
            {
                String who = claim.isAdminClaim() ? "the administrators" : claim.getOwnerName();
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.OnlyOwnersModifyClaims, who);
                return 0;
            }
        }

        String oldName = claim.getName().orElse(null);
        claim.setName(newName);

        player.sendMessage(text()
                .append(text(plugin().dataStore.getMessage(Messages.RenameSuccess), NamedTextColor.YELLOW))
                .append(text(Objects.requireNonNullElse(newName, "<unnamed>"), NamedTextColor.AQUA))
                .build());

        if (Objects.equals(oldName, newName))
        {
            logger().info("{} tried changing the name for claim #{}. Nothing changed.", player.getName(), claim.getID());
        }
        else if (newName == null)
        {
            logger().info("{} cleared the name for claim #{}.", player.getName(), claim.getID());
        }
        else
        {
            logger().info("{} set name for claim #{} to: {}.", player.getName(), claim.getID(), newName);
        }

        // IO write on main thread.
        // This is not ideal, but it is consistent with prexisting claim-commands.
        plugin().dataStore.saveClaim(claim);
        return Command.SINGLE_SUCCESS;
    }
}
