// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy <k666kkk666k@163.com>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * GetResearchCommand — /sf getresearch 子命令实现类喵~
 *
 * 功能：查询指定 Slimefun 物品ID所绑定的研究 NamespacedKey，
 * 方便管理员快速确认某物品需要哪个研究才能解锁使用喵~
 *
 * 执行流程：
 *   1. 验证执行者是否为在线玩家（控制台不允许执行）
 *   2. 检查执行者是否持有 slimefun.command.cheat 权限
 *   3. 根据 args[1] 查找对应的 SlimefunItem 对象
 *   4. 若物品存在且绑定了研究，则向发送者输出研究的 NamespacedKey 字符串（格式 "namespace.key"）
 *   5. 若物品不存在或未绑定研究，则发送对应错误提示消息
 *
 * 边界条件：
 *   args[1] 不存在时（未传ID参数）会抛出 ArrayIndexOutOfBoundsException，
 *   调用方应在路由层做参数长度校验后再分发到本命令喵~
 */
// GetResearchCommand 继承自 SubCommand，是 /sf getresearch 命令的具体实现类喵~
class GetResearchCommand extends SubCommand {

    /**
     * 构造方法：将本子命令注册到 Slimefun 命令体系中喵~
     *
     * @param plugin Slimefun 插件主实例，用于获取本地化等系统服务
     * @param cmd    父命令 /sf 的 SlimefunCommand 对象，用于注册路由
     */
    @ParametersAreNonnullByDefault
    GetResearchCommand(Slimefun plugin, SlimefunCommand cmd) {
        // 调用父类构造方法，注册子命令名称 "getresearch"，false 表示此命令不强制要求玩家身份（方法体内部会再次校验）喵~
        super(plugin, cmd, "getresearch", false);
    }

    /**
     * 命令执行入口：当玩家输入 /sf getresearch <物品ID> 时由框架调用此方法喵~
     *
     * 整体思路：
     *   先确认发送者身份和权限，再用物品ID查找 SlimefunItem，
     *   最终输出该物品绑定的研究 NamespacedKey（格式为 "namespace.key"）喵~
     *
     * @param sender 命令发送者，可能是玩家、控制台或命令方块
     * @param args   命令参数数组，args[0] = "getresearch"，args[1] = 目标物品ID
     */
    @Override
    @ParametersAreNonnullByDefault
    public void onExecute(CommandSender sender, String[] args) {
        // 喵~防御：只允许在线玩家执行此命令，控制台或命令方块调用时走 else 分支发送仅限玩家提示喵~
        if (sender instanceof Player) {
            // 检查发送者是否拥有作弊权限 slimefun.command.cheat，没有则拒绝执行喵~
            if (sender.hasPermission("slimefun.command.cheat")) {
                // 根据玩家传入的物品ID（args[1]）从 Slimefun 注册表中查找对应的 SlimefunItem 对象喵~
                SlimefunItem item = SlimefunItem.getById(args[1]);
                // 喵~防御：item 为 null 说明传入的物品ID在注册表中找不到，进入 else 分支提示输入错误喵~
                if (item != null) {
                    // 判断该物品是否绑定了研究（有些物品无需研究即可直接使用）喵~
                    if (item.hasResearch()) {
                        // 将研究的 NamespacedKey 拼成 "namespace.key" 格式并发送给执行者，方便复制到配置中使用喵~
                        sender.sendMessage(item.getResearch().getKey().getNamespace() + "."
                                + item.getResearch().getKey().getKey());
                    } else {
                        // 物品存在但未绑定任何研究，告知玩家该物品不需要解锁研究喵~
                        sender.sendMessage("这个物品没有研究" + args[1]);
                    }
                } else {
                    // 传入的物品ID在系统中不存在，告知玩家检查ID是否拼写正确喵~
                    sender.sendMessage("你输入的物品id错误" + args[1]);
                }
            } else {
                // 发送者权限不足，使用本地化消息系统发送无权限提示，true 表示附带消息前缀喵~
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            }
        } else {
            // 发送者不是玩家（如控制台），使用本地化消息告知此命令仅限玩家执行喵~
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players", true);
        }
    }
}
