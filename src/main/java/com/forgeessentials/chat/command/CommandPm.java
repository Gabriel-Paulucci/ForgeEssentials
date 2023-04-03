package com.forgeessentials.chat.command;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextComponent;
import net.minecraftforge.server.permission.DefaultPermissionLevel;

import com.forgeessentials.chat.ModuleChat;
import com.forgeessentials.core.commands.ForgeEssentialsCommandBuilder;
import com.forgeessentials.core.misc.Translator;
import com.forgeessentials.core.misc.TranslatedCommandException.PlayerNotFoundException;
import com.forgeessentials.util.output.ChatOutputHandler;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class CommandPm extends ForgeEssentialsCommandBuilder
{

    public CommandPm(boolean enabled)
    {
        super(enabled);
    }

    public static Map<CommandSource, WeakReference<CommandSource>> targetMap = new WeakHashMap<>();

    public static void setTarget(CommandSource sender, CommandSource target)
    {
        targetMap.put(sender, new WeakReference<CommandSource>(target));
    }

    public static void clearTarget(CommandSource sender)
    {
        targetMap.remove(sender);
    }

    public static CommandSource getTarget(CommandSource sender)
    {
        WeakReference<CommandSource> target = targetMap.get(sender);
        if (target == null)
            return null;
        return target.get();
    }

    /* ------------------------------------------------------------ */

    @Override
    public String getPrimaryAlias()
    {
        return "pm";
    }

    @Override
    public String getPermissionNode()
    {
        return "fe.chat.pm";
    }

    @Override
    public DefaultPermissionLevel getPermissionLevel()
    {
        return DefaultPermissionLevel.ALL;
    }

    @Override
    public boolean canConsoleUseCommand()
    {
        return true;
    }

    @Override
    public LiteralArgumentBuilder<CommandSource> setExecution()
    {
        return baseBuilder
                .then(Commands.literal("setTarget")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(CommandContext -> execute(CommandContext, "setTarget")
                                        )
                                )
                        )
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(CommandContext -> execute(CommandContext, "message")
                                )
                        )
                .executes(CommandContext -> execute(CommandContext, "clear")
                        );
    }
    
    @Override
    public int execute(CommandContext<CommandSource> ctx, Object... params) throws CommandSyntaxException
    {
        CommandSource target = EntityArgument.getPlayer(ctx, "player").createCommandSourceStack();
        if (params.toString() == "setTarget")
        {
            if (ctx.getSource() == target)
                throw new PlayerNotFoundException("commands.message.sameTarget");
            setTarget(ctx.getSource(), target);
            ChatOutputHandler.chatConfirmation(ctx.getSource(), Translator.format("Set PM target to %s", target.getTextName()));
            return Command.SINGLE_SUCCESS;
        }
        if (params.toString() == "clear")
        {
        	clearTarget(ctx.getSource());
            ChatOutputHandler.chatConfirmation(ctx.getSource(),("Cleared PM target"));
            return Command.SINGLE_SUCCESS;
        }
        TextComponent message = new StringTextComponent(StringArgumentType.getString(ctx, "message"));
        ModuleChat.tell(ctx.getSource(), message, target);
        return Command.SINGLE_SUCCESS;
    }
}
