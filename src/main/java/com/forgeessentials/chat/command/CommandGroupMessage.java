package com.forgeessentials.chat.command;

import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.command.arguments.MessageArgument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.server.permission.DefaultPermissionLevel;

import com.forgeessentials.api.APIRegistry;
import com.forgeessentials.api.UserIdent;
import com.forgeessentials.chat.ModuleChat;
import com.forgeessentials.core.commands.BaseCommand;
import com.forgeessentials.core.misc.TranslatedCommandException;
import com.forgeessentials.util.CommandParserArgs;
import com.forgeessentials.util.CommandUtils;
import com.forgeessentials.util.DoAsCommandSender;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class CommandGroupMessage extends BaseCommand
{

    public CommandGroupMessage(String name, int permissionLevel, boolean enabled)
    {
        super(name, permissionLevel, enabled);
    }

    public static final String PERM = "fe.chat.groupmessage";

    @Override
    public String getPrimaryAlias()
    {
        return "gmsg";
    }

    @Override
    public String getPermissionNode()
    {
        return PERM;
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
        return builder
                .then(Commands.argument("group", GroupsArgument.groups())//APIRegistry.perms.getServerZone().getGroups()
                        .then(Commands.argument("message", MessageArgument.message())
                                .executes(CommandContext -> execute(CommandContext)
                                        )
                                )
                        );
    }

    @Override
    public int execute(CommandContext<CommandSource> ctx, Object... params) throws CommandSyntaxException
    {
        String group = GroupsArgument.get(ctx, "group").toLowerCase();

        UserIdent ident = getUserIdent(ctx.getSource(), getServerPlayer(ctx.getSource()));
        ITextComponent msgComponent = MessageArgument.getMessage(ctx, "message");
        ModuleChat.tellGroup(ctx.getSource(), msgComponent.getString(), group, ident.checkPermission(ModuleChat.PERM_COLOR));
    }
}
