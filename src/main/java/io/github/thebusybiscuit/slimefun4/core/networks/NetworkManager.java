// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy <k666kkk666k@163.com>
// Based on Slimefun4 by TheBusyBiscuit and contributors, licensed under GPL-3.0
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
package io.github.thebusybiscuit.slimefun4.core.networks;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.api.network.Network;
import io.github.thebusybiscuit.slimefun4.core.debug.Debug;
import io.github.thebusybiscuit.slimefun4.core.debug.TestCase;
import io.github.thebusybiscuit.slimefun4.core.networks.cargo.CargoNet;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.NetworkListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.Location;
import org.bukkit.Server;

/**
 * The {@link NetworkManager} is responsible for holding all instances of {@link Network}
 * and providing some utility methods that would have probably been static otherwise.
 *
 * @author TheBusyBiscuit
 * @author meiamsome
 *
 * @see Network
 * @see NetworkListener
 *
 */
// 网络管理器类，负责持有全服所有 Network 实例并提供统一工具方法喵~
public class NetworkManager {

    // 单个网络允许的最大节点数，从配置文件中读取喵~
    private final int maxNodes;
    // 是否启用网络可视化显示功能喵~
    private final boolean enableVisualizer;
    // 是否在货运网络物品溢出时自动删除多余物品，防止物品掉落一地喵~
    private final boolean deleteExcessItems;

    /**
     * Fixes #3041
     *
     * We use a {@link CopyOnWriteArrayList} here to ensure thread-safety.
     * This {@link List} is also much more frequently read than being written to.
     * Therefore a {@link CopyOnWriteArrayList} should be perfect for this, even
     * if insertions come at a slight cost.
     */
    // 使用 CopyOnWriteArrayList 保证读多写少场景下的线程安全，存放全服所有网络实例喵~
    private final List<Network> networks = new CopyOnWriteArrayList<>();

    /**
     * This creates a new {@link NetworkManager} with the given capacity.
     *
     * @param maxStepSize
     *            The maximum amount of nodes a {@link Network} can have
     * @param enableVisualizer
     *            Whether the {@link Network} visualizer is enabled
     * @param deleteExcessItems
     *            Whether excess items from a {@link CargoNet} should be voided
     */
    /*
     * 完整参数构造方法——初始化网络管理器喵~
     * 输入：maxStepSize(最大节点数)、enableVisualizer(是否开可视化)、deleteExcessItems(是否删溢出物品)
     * 输出：一个完整配置的 NetworkManager 实例喵~
     * 边界条件：maxStepSize 必须大于 0，否则抛出 IllegalArgumentException喵~
     */
    public NetworkManager(int maxStepSize, boolean enableVisualizer, boolean deleteExcessItems) {
        // 喵~防御：maxStepSize必须大于0，小于等于0的网络大小没有意义会导致逻辑错误喵~
        Validate.isTrue(maxStepSize > 0, "The maximal Network size must be above zero!");

        // 保存网络可视化开关状态喵~
        this.enableVisualizer = enableVisualizer;
        // 保存物品溢出自动删除开关状态喵~
        this.deleteExcessItems = deleteExcessItems;
        // 将传入的最大节点数赋值给成员变量喵~
        maxNodes = maxStepSize;
    }

    /**
     * This creates a new {@link NetworkManager} with the given capacity.
     *
     * @param maxStepSize
     *            The maximum amount of nodes a {@link Network} can have
     */
    /*
     * 简化构造方法——只传最大节点数，其余参数使用默认值喵~
     * 默认：可视化开启(true)、不删除溢出物品(false)喵~
     */
    public NetworkManager(int maxStepSize) {
        // 调用完整参数构造器，可视化默认开启，物品删除默认关闭喵~
        this(maxStepSize, true, false);
    }

    /**
     * This method returns the limit of nodes a {@link Network} can have.
     * This value is read from the {@link Config} file.
     *
     * @return the maximum amount of nodes a {@link Network} can have
     */
    public int getMaxSize() {
        // 返回从配置文件读取到的单个网络最大节点数上限喵~
        return maxNodes;
    }

    /**
     * This returns whether the {@link Network} visualizer is enabled.
     *
     * @return Whether the {@link Network} visualizer is enabled
     */
    public boolean isVisualizerEnabled() {
        // 返回网络可视化功能是否被启用喵~
        return enableVisualizer;
    }

    /**
     * This returns whether excess items from a {@link CargoNet} should be voided
     * instead of being dropped to the ground.
     *
     * @return Whether to delete excess items
     */
    public boolean isItemDeletionEnabled() {
        // 返回货运网络物品溢出时是否自动删除，true表示虚空销毁而非掉落到地面喵~
        return deleteExcessItems;
    }

    /**
     * This returns a {@link List} of every {@link Network} on the {@link Server}.
     * The returned {@link List} is not modifiable.
     *
     * @return A {@link List} containing every {@link Network} on the {@link Server}
     */
    @Nonnull
    public List<Network> getNetworkList() {
        // 返回全服所有网络的不可修改视图，防止外部代码意外增删网络实例喵~
        return Collections.unmodifiableList(networks);
    }

    /*
     * 根据坐标和网络类型查找第一个匹配的网络实例喵~
     * 输入：Location坐标(可为null)、目标网络类型Class
     * 输出：Optional包装的匹配网络，找不到返回 Optional.empty()喵~
     * 边界条件：坐标为null时直接返回空Optional，type不能为null喵~
     * 主人注意：遍历全服networks列表，网络数量极多时有线性查找开销，建议关注服务器网络总数喵~
     */
    @Nonnull
    public <T extends Network> Optional<T> getNetworkFromLocation(@Nullable Location l, @Nonnull Class<T> type) {
        // 喵~防御：坐标为null说明位置不存在，直接返回空Optional避免后续空指针异常喵~
        if (l == null) {
            return Optional.empty();
        }

        // 喵~防御：网络类型不能为null，否则isInstance无法进行类型判断喵~
        Validate.notNull(type, "Type must not be null");

        // 遍历全服所有网络，找到第一个类型匹配且该坐标接入的网络喵~
        for (Network network : networks) {
            // 同时满足：网络类型匹配 AND 该坐标位置连接到了此网络喵~
            if (type.isInstance(network) && network.connectsTo(l)) {
                // 找到第一个匹配就立刻返回，用Optional包装类型转换结果喵~
                return Optional.of(type.cast(network));
            }
        }

        // 遍历完所有网络都没找到匹配的，返回空Optional喵~
        return Optional.empty();
    }

    /*
     * 根据坐标和网络类型查找该位置上所有匹配的网络实例（一个坐标可同时接入多个网络）喵~
     * 输入：Location坐标(可为null)、目标网络类型Class
     * 输出：包含所有匹配网络的List，找不到返回空列表喵~
     * 边界条件：坐标为null时返回空列表，type不能为null喵~
     * 主人注意：遍历全服networks列表，网络数量极多时有线性查找开销喵~
     */
    @Nonnull
    public <T extends Network> List<T> getNetworksFromLocation(@Nullable Location l, @Nonnull Class<T> type) {
        // 喵~防御：坐标为null表示位置不存在，该位置没有任何网络直接返回空列表喵~
        if (l == null) {
            // No networks here, if the location does not even exist
            return new ArrayList<>();
        }

        // 喵~防御：网络类型不能为null，否则isInstance判断会抛出异常喵~
        Validate.notNull(type, "Type must not be null");
        // 创建结果列表，用于收集所有匹配到的网络实例喵~
        List<T> list = new ArrayList<>();

        // 遍历全服所有网络，把类型匹配且坐标连接的都收集进结果列表喵~
        for (Network network : networks) {
            // 同时满足类型匹配和坐标连接才算有效，执行类型转换后加入结果喵~
            if (type.isInstance(network) && network.connectsTo(l)) {
                list.add(type.cast(network));
            }
        }

        // 返回收集到的所有匹配网络列表喵~
        return list;
    }

    /**
     * This registers a given {@link Network}.
     *
     * @param network
     *            The {@link Network} to register
     */
    public void registerNetwork(@Nonnull Network network) {
        // 喵~防御：不允许注册null网络，否则列表中会存入空引用导致后续tick崩溃喵~
        Validate.notNull(network, "Cannot register a null Network");

        // 输出调试日志，记录正在注册的网络及其调节器坐标，便于排查问题喵~
        Debug.log(
                TestCase.ENERGYNET, "Registering network @ " + LocationUtils.locationToString(network.getRegulator()));

        // 将新网络加入全服网络列表，CopyOnWriteArrayList保证线程安全喵~
        networks.add(network);
    }

    /**
     * This removes a {@link Network} from the network system.
     *
     * @param network
     *            The {@link Network} to remove
     */
    public void unregisterNetwork(@Nonnull Network network) {
        // 喵~防御：不允许注销null网络，传null说明调用方存在逻辑问题喵~
        Validate.notNull(network, "Cannot unregister a null Network");

        // 输出调试日志，记录正在注销的网络及其调节器坐标喵~
        Debug.log(
                TestCase.ENERGYNET,
                "Unregistering network @ " + LocationUtils.locationToString(network.getRegulator()));

        // 从全服网络列表中移除该网络实例，使其不再参与后续tick更新喵~
        networks.remove(network);
    }

    /**
     * This method updates every {@link Network} found at the given {@link Location}.
     * More precisely, {@link Network#markDirty(Location)} will be called.
     *
     * @param l
     *            The {@link Location} to update
     */
    /*
     * 更新指定坐标上所有网络——当某坐标的方块状态发生变化时(放置/破坏)调用此方法喵~
     * 输入：需要更新的Location坐标(不能为null)喵~
     * 输出：无返回值，副作用是把该坐标上所有相关网络标记为脏(dirty)，触发下次tick重新分类节点喵~
     * 边界条件：networks为空时提前返回节省遍历开销；捕获所有异常防止服务器崩溃喵~
     */
    public void updateAllNetworks(@Nonnull Location l) {
        // 喵~防御：坐标不能为null，否则无法定位需要更新的网络节点喵~
        Validate.notNull(l, "The Location cannot be null");

        // 输出调试日志，标记当前触发了全网络更新操作喵~
        Debug.log(TestCase.ENERGYNET, "Updating all networks now.");

        try {
            /*
             * No need to create a sublist and loop through it if
             * there aren't even any networks on the server.
             */
            // 喵~防御：全服没有任何网络时直接提前返回，避免空列表查找的无效性能开销喵~
            if (networks.isEmpty()) {
                return;
            }

            // 获取该坐标上所有网络并逐一标记为需要重新计算(dirty)，下次tick会重新分类节点喵~
            for (Network network : getNetworksFromLocation(l, Network.class)) {
                // 将该网络的指定坐标节点标记为脏，触发下次tick对该位置重新进行节点分类喵~
                network.markDirty(l);
            }
        } catch (Exception x) {
            // 捕获所有意外异常，打印SEVERE级别日志并附上出错坐标，保证服务器不因网络更新而崩溃喵~
            Slimefun.logger()
                    .log(
                            Level.SEVERE,
                            x,
                            () -> "An Exception was thrown while causing a networks update @ " + new BlockPosition(l));
        }
    }
}
