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

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /sf grid 子命令：查询玩家准星所指方块的能量网络组件信息喵~
 *
 * 整体思路：
 *   1. 验证执行者是否为玩家（仅玩家可以使用准星瞄准方块）喵~
 *   2. 验证执行者是否有 slimefun.command.grid 权限喵~
 *   3. 获取玩家准星10格内的目标方块喵~
 *   4. 查询该方块在能量网络中的组件信息并发送给执行者喵~
 *
 * 输入：CommandSender（命令发送者）、args（命令参数，此命令不需要额外参数）喵~
 * 输出：向玩家发送目标方块的能量网络组件信息文本喵~
 * 边界条件：发送者非玩家、无权限、准星10格内无方块均会提前返回并发送提示消息喵~
 */
// 能量网格查询子命令类，继承 SubCommand 基类喵~
class GridCommand extends SubCommand {

    /**
     * 构造方法：向父类注册命令名称 "grid"，不需要OP权限喵~
     *
     * @param plugin 当前 Slimefun 插件实例喵~
     * @param cmd    所属的 SlimefunCommand 父命令喵~
     */
    // 调用父类构造器，将命令名 "grid" 和 false(非OP专属) 传入进行注册喵~
    protected GridCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "grid", false);
    }

    @Override
    // 返回该命令在本地化配置文件中的描述键，用于帮助信息展示喵~
    protected String getDescription() {
        return "commands.grid.description"; // 本地化键：指向配置文件里 commands.grid.description 的翻译文本喵~
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        // 喵~防御：若发送者不是玩家（如控制台），则无法使用准星瞄准方块，发送仅玩家提示后提前返回喵~
        if (!(sender instanceof Player p)) {
            Slimefun.getLocalization()
                    .sendMessage(sender, "commands.grid.player-only", true); // 向发送者发送"仅玩家可用"的本地化提示消息喵~
            return;
        }

        // 喵~防御：检查玩家是否拥有 slimefun.command.grid 权限，无权限则发送提示后提前返回，防止越权操作喵~
        if (!sender.hasPermission("slimefun.command.grid")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true); // 向玩家发送"无权限"的本地化提示消息喵~
            return;
        }

        // 获取玩家准星10格范围内精确瞄准的目标方块，用于后续查询能量网络信息喵~
        Block target = p.getTargetBlockExact(10);
        // 喵~防御：若准星10格内没有瞄准任何方块（target为null），则发送无目标提示后提前返回，避免后续空指针崩溃喵~
        if (target == null) {
            Slimefun.getLocalization().sendMessage(sender, "commands.grid.no-target", true); // 向玩家发送"没有目标方块"的本地化提示消息喵~
            return;
        }

        // 通过 EnergyNet 静态方法查询目标方块所在位置的能量网络组件信息（如：组件类型、所属网络、能量状态等）喵~
        String info = EnergyNet.getComponentInfo(target.getLocation());
        // 将能量网络组件信息直接发送给命令执行者喵~
        sender.sendMessage(info);
    }
}
