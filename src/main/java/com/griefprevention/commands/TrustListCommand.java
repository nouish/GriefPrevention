package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static io.papermc.paper.command.brigadier.Commands.literal;

@SuppressWarnings("deprecation")
public final class TrustListCommand extends AbstractClaimCommand
{
    public TrustListCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("trustlist")
                .requires(playerWithPermission("griefprevention.claims"))
                .executes(ctx -> showTrustList(ctx.getSource()))
                .build(),
                "Lists permissions for the claim you're standing in.");
    }

    private int showTrustList(@NotNull CommandSourceStack source)
    {
        Player player = (Player) source.getSender();
        Claim claim = claim(source).orElse(null);

        //if no claim here, error message
        if (claim == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.TrustListNoClaim);
            return 0;
        }

        //if no permission to manage permissions, error message
        Supplier<String> errorMessage = claim.checkPermission(player, ClaimPermission.Manage, null);
        if (errorMessage != null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, errorMessage.get());
            return 0;
        }

        //otherwise build a list of explicit permissions by permission level
        //and send that to the player
        ArrayList<String> builders = new ArrayList<>();
        ArrayList<String> containers = new ArrayList<>();
        ArrayList<String> accessors = new ArrayList<>();
        ArrayList<String> managers = new ArrayList<>();
        claim.getPermissions(builders, containers, accessors, managers);

        player.spigot().sendMessage(new ComponentBuilder()
                .append("Explicit permissions here:")
                .color(net.md_5.bungee.api.ChatColor.YELLOW)
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new ComponentBuilder()
                        .append("Claim Information:").color(net.md_5.bungee.api.ChatColor.GREEN)
                        .append("\n")
                        .append("ID: ")
                        .color(net.md_5.bungee.api.ChatColor.GRAY)
                        .append(String.valueOf(claim.getID()))
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .append("\n")
                        .append("Name: ")
                        .color(net.md_5.bungee.api.ChatColor.GRAY)
                        .append(claim.getName().orElse("<unnamed>"))
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .append("\n")
                        .append("Owner: ")
                        .color(net.md_5.bungee.api.ChatColor.GRAY)
                        .append(plugin().trustEntryToPlayerName(claim.ownerID == null ? "public" : claim.ownerID.toString()))
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .append("\n")
                        .append("Size: ")
                        .color(net.md_5.bungee.api.ChatColor.GRAY)
                        .append(String.valueOf(claim.getWidth()))
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .append("x")
                        .color(net.md_5.bungee.api.ChatColor.GRAY)
                        .append(String.valueOf(claim.getHeight()))
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .create())))
                .create());

        // Consider accessor for Claim.playerIDToClaimPermissionMap, which is private now.
        Map<String, ClaimPermission> trustList = new HashMap<>();
        builders.forEach(v -> trustList.put(v, ClaimPermission.Build));
        containers.forEach(v -> trustList.put(v, ClaimPermission.Inventory));
        accessors.forEach(v -> trustList.put(v, ClaimPermission.Access));
        managers.forEach(v -> trustList.put(v, ClaimPermission.Manage));

        if (trustList.isEmpty())
        {
            GriefPrevention.sendMessage(player, ChatColor.GOLD, "There are no explicit permissions in this claim.");
            return 1;
        }

        ComponentBuilder builder = new ComponentBuilder();
        for (var entry : trustList.entrySet())
        {
            String formattedName = plugin().trustEntryToPlayerName(entry.getKey());

            if (builder.getCursor() >= 0)
            {
                builder.append(", ", ComponentBuilder.FormatRetention.NONE);
            }

            net.md_5.bungee.api.ChatColor textColor = switch (entry.getValue())
            {
                case Build -> net.md_5.bungee.api.ChatColor.YELLOW;
                case Inventory -> net.md_5.bungee.api.ChatColor.GREEN;
                case Access -> net.md_5.bungee.api.ChatColor.BLUE;
                case Manage -> net.md_5.bungee.api.ChatColor.GOLD;
                default -> net.md_5.bungee.api.ChatColor.WHITE;
            };

            builder
                    .append(formattedName)
                    .color(textColor)
                    .event(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entry.getKey()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(new ComponentBuilder()
                            .append("User Information:")
                            .color(net.md_5.bungee.api.ChatColor.GREEN)
                            .append("\n")
                            .append("Name: ")
                            .color(net.md_5.bungee.api.ChatColor.GRAY)
                            .append(formattedName)
                            .color(net.md_5.bungee.api.ChatColor.YELLOW)
                            .append("\n")
                            .append("ID: ")
                            .color(net.md_5.bungee.api.ChatColor.GRAY)
                            .append(entry.getKey())
                            .color(net.md_5.bungee.api.ChatColor.YELLOW)
                            .append("\n")
                            .append("Permission: ")
                            .color(net.md_5.bungee.api.ChatColor.GRAY)
                            .append(entry.getValue().name())
                            .color(textColor)
                            .create())));
        }

        player.spigot().sendMessage(builder.create());

        if (claim.getSubclaimRestrictions())
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.HasSubclaimRestriction);
        }

        return Command.SINGLE_SUCCESS;
    }
}
