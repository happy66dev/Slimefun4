package io.github.thebusybiscuit.slimefun4.core.networks.cargo;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.xzavier0722.mc.plugin.slimefuncomplib.event.cargo.CargoTickEvent;
import io.github.bakedlibs.dough.common.CommonPatterns;
import io.github.thebusybiscuit.slimefun4.api.network.Network;
import io.github.thebusybiscuit.slimefun4.api.network.NetworkComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link CargoNet} is a type of {@link Network} which deals with {@link ItemStack} transportation.
 * It is also an extension of {@link AbstractItemNetwork} which provides methods to deal
 * with the addon ChestTerminal.
 *
 * @author meiamsome
 * @author Poslovitch
 * @author John000708
 * @author BigBadE
 * @author SoSeDiK
 * @author TheBusyBiscuit
 * @author Walshy
 * @author DNx5
 *
 */
// 货运网络核心类，继承自AbstractItemNetwork并实现HologramOwner接口，负责管理整个货运网络的物品传输喵
public class CargoNet extends AbstractItemNetwork implements HologramOwner {

    // 货运网络的最大连接范围，单位：方块，超过此范围的节点不会被识别为同一网络喵
    private static final int RANGE = 5;

    // 每个频道每次tick消耗的能量，单位：J（焦耳），频道数越多消耗越高喵
    private static final long CHANNEL_COST = 6;

    // 每个输入节点每次tick消耗的能量，单位：J（焦耳），输入节点越多消耗越高喵
    private static final long INPUT_NODE_COST = 2;

    // 存储所有输入节点的坐标集合，用于记录哪些位置是货运输入节点喵
    private final Set<Location> inputNodes = new HashSet<>();
    // 存储所有输出节点的坐标集合，用于记录哪些位置是货运输出节点喵
    private final Set<Location> outputNodes = new HashSet<>();

    // 轮询分配映射表，记录每个位置上一次分配到的输出节点索引，用于实现轮流分发物品喵
    protected final Map<Location, Integer> roundRobin = new HashMap<>();
    // 当前tick延迟计数器，用于控制货运网络不必每tick都执行物品搬运，降低服务器性能压力喵
    private int tickDelayThreshold = 0;

    /**
     * 根据坐标查找已存在的货运网络，如果不存在则返回null喵~
     * 输入：一个方块坐标Location；输出：对应的CargoNet实例，找不到则返回null喵
     */
    // 静态工具方法：根据坐标从网络管理器中查询是否有货运网络，找不到时返回null喵
    public static @Nullable CargoNet getNetworkFromLocation(@Nonnull Location l) {
        // 从Slimefun网络管理器中查找给定坐标对应的CargoNet实例，找不到则返回null喵
        return Slimefun.getNetworkManager()
                .getNetworkFromLocation(l, CargoNet.class)
                .orElse(null);
    }

    /**
     * 根据坐标获取货运网络，若不存在则自动创建并注册一个新网络喵~
     * 整体思路：先尝试查找现有网络，若没有则创建新CargoNet并注册到管理器喵
     * 输入：方块坐标；输出：该坐标对应的CargoNet（保证非null）喵
     */
    // 静态工具方法：获取或创建货运网络——确保任何被调用的坐标都有对应网络实例喵
    public static @Nonnull CargoNet getNetworkFromLocationOrCreate(@Nonnull Location l) {
        // 先尝试从网络管理器中查找该坐标已有的货运网络喵
        Optional<CargoNet> cargoNetwork = Slimefun.getNetworkManager().getNetworkFromLocation(l, CargoNet.class);

        // 喵~防御：如果已存在网络则直接返回，避免重复创建导致网络冲突喵
        if (cargoNetwork.isPresent()) {
            return cargoNetwork.get();
        } else {
            // 该坐标还没有货运网络，新建一个CargoNet实例喵
            CargoNet network = new CargoNet(l);
            // 将新创建的网络注册到Slimefun网络管理器，使其开始接受tick更新喵
            Slimefun.getNetworkManager().registerNetwork(network);
            return network;
        }
    }

    /**
     * This constructs a new {@link CargoNet} at the given {@link Location}.
     *
     * @param l
     *            The {@link Location} marking the manager of this {@link Network}.
     */
    // 构造方法：以给定坐标（货运调节机的位置）初始化一个新的货运网络喵
    protected CargoNet(@Nonnull Location l) {
        // 调用父类AbstractItemNetwork的构造方法，设置网络的调节器坐标喵
        super(l);
    }

    // 返回此网络的唯一ID字符串，用于Slimefun网络系统识别网络类型喵
    @Override
    public String getId() {
        return "CARGO_NETWORK";
    }

    // 返回货运网络的最大连接范围（5格），超出范围的节点无法加入此网络喵
    @Override
    public int getRange() {
        return RANGE;
    }

    /**
     * 判断给定坐标上的方块属于哪种网络节点角色（调节器/连接器/终端）喵~
     * 整体思路：读取该坐标的Slimefun方块数据，根据物品ID用switch匹配节点类型喵
     * 输入：Location坐标；输出：NetworkComponent枚举值，不属于货运网络则返回null喵
     */
    @Override
    // 对给定坐标进行网络节点分类，决定它是调节器、连接器还是终端节点喵
    public NetworkComponent classifyLocation(@Nonnull Location l) {
        // 从存储缓存中读取该坐标的Slimefun方块数据喵
        var data = StorageCacheUtils.getBlock(l);

        // 喵~防御：如果该坐标没有Slimefun方块数据，说明不属于货运网络，返回null喵
        if (data == null) {
            return null;
        }

        // 根据方块的Slimefun ID判断节点类型：调节机=调节器，节点=连接器，输入/输出节点=终端喵
        return switch (data.getSfId()) {
            case "CARGO_MANAGER" -> NetworkComponent.REGULATOR; // 货运调节机是网络的核心调节器喵
            case "CARGO_NODE" -> NetworkComponent.CONNECTOR; // 普通货运节点作为网络连接器负责传导信号喵
            case "CARGO_NODE_INPUT", "CARGO_NODE_OUTPUT", "CARGO_NODE_OUTPUT_ADVANCED" ->
                NetworkComponent.TERMINUS; // 输入/输出节点是网络终端，负责实际搬运物品喵
            default -> null; // 其他方块不属于货运网络喵
        };
    }

    /**
     * 当某个坐标的网络节点分类发生变化时调用此方法，同步更新输入/输出节点集合喵~
     * 整体思路：先清除旧分类缓存，根据from和to的变化更新inputNodes和outputNodes集合喵
     * 输入：坐标、原分类、新分类；无返回值喵
     */
    @Override
    // 节点分类变化回调：当某位置的节点角色改变时，同步刷新货运输入/输出节点记录喵
    public void onClassificationChange(Location l, NetworkComponent from, NetworkComponent to) {
        // 清除该坐标的连接器缓存，确保下次重新计算连接关系喵
        connectorCache.remove(l);

        // 喵~防御：如果该坐标之前是终端节点，从输入和输出列表中移除，避免残留脏数据喵
        if (from == NetworkComponent.TERMINUS) {
            inputNodes.remove(l);
            outputNodes.remove(l);
        }

        // 如果新分类是终端节点，需要根据具体物品ID决定加入输入集合还是输出集合喵
        if (to == NetworkComponent.TERMINUS) {
            // 读取该位置的方块数据以判断是输入节点还是输出节点喵
            var data = StorageCacheUtils.getBlock(l);
            switch (data.getSfId()) {
                case "CARGO_NODE_INPUT" -> inputNodes.add(l); // 输入节点：负责从容器抽取物品喵
                case "CARGO_NODE_OUTPUT", "CARGO_NODE_OUTPUT_ADVANCED" -> outputNodes.add(l); // 输出节点：负责将物品放入目标容器喵
                default -> {}
            }
        }

        // 输入节点数量变化时，同步更新货运调节机上记录的输入节点数喵
        updateCargoManagerInputCount();
    }

    /**
     * 货运网络每游戏tick执行的主逻辑方法喵~
     * 整体思路：
     *   1. 验证触发tick的方块是否是本网络的调节机，不是则提示多调节机冲突喵
     *   2. 调用父类tick刷新节点拓扑喵
     *   3. 若网络无节点则显示提示全息图喵
     *   4. 收集输入/输出节点映射，判断电力是否充足喵
     *   5. 满足条件时触发CargoTickEvent事件，并执行CargoNetworkTask传输物品喵
     * 边界条件：电力不足/无活跃输入/tick延迟未到时提前返回不执行传输喵
     */
    // 货运网络主tick方法：每游戏tick调用一次，协调物品传输的完整流程喵
    public void tick(@Nonnull Block b, SlimefunBlockData blockData) {
        // 喵~防御：检查触发tick的方块是否是本网络的调节机，不匹配说明附近有多个调节机，显示错误提示喵
        if (!regulator.equals(b.getLocation())) {
            updateHologram(b, "&4发现附近有多个货运网络调节机", blockData::isPendingRemove);
            return;
        }

        // 调用父类Network.tick()，刷新网络拓扑（重新扫描和分类附近的节点）喵
        super.tick();

        // 喵~防御：如果连接器节点和终端节点都为空，说明网络没有任何有效节点，显示提示并退出喵
        if (connectorNodes.isEmpty() && terminusNodes.isEmpty()) {
            updateHologram(b, "&c找不到附近的货运网络节点", blockData::isPendingRemove);
        } else {
            // 将所有输入节点映射为"位置→频道"的Map，用于后续物品抽取喵
            Map<Location, Integer> inputs = mapInputNodes();
            // 将所有输出节点映射为"频道→位置列表"的Map，用于后续物品投放喵
            Map<Integer, List<Location>> outputs = mapOutputNodes();

            // 如果调节机没有启用可视化显示器，则自动开启网络节点的可视化展示喵
            if (StorageCacheUtils.getData(b.getLocation(), "visualizer") == null) {
                display();
            }

            // 检查当前tick延迟计数是否已达到配置的延迟阈值，决定本次是否真正执行物品传输喵
            boolean canRun = tickDelayThreshold >= Slimefun.getCfg().getInt("networks.cargo-ticker-delay");
            if (!canRun) {
                // 延迟未到，计数器自增，本次tick不执行物品传输喵
                tickDelayThreshold++;
            } else {
                // 延迟已到，重置计数器，本次tick将执行物品传输喵
                tickDelayThreshold = 0;
            }

            // 将后续逻辑放到同步任务中执行，确保物品操作在主线程进行（Bukkit物品操作非线程安全）喵
            Slimefun.runSync(() -> {
                // 喵~防御：如果方块数据已被标记为待移除（调节机被破坏），则终止本次tick喵
                if (blockData.isPendingRemove()) {
                    return;
                }

                // 过滤出真正有物品可搬运的活跃输入节点，排除连接的容器为空的节点喵
                Map<Location, Integer> activeInputs = filterActiveInputNodes(inputs);
                // 根据活跃输入节点数和频道数计算本次tick需要消耗的电力喵
                long powerNeeded = calculatePowerNeeded(activeInputs);

                // 如果需要消耗电力（有活跃输入），则检查调节机当前储能是否足够喵
                if (powerNeeded > 0) {
                    // 读取货运调节机当前的储能量，单位：J喵
                    long charge = readCharge();
                    // 喵~防御：储能不足时更新全息图提示并终止本次物品传输，避免无效运行喵
                    if (charge < powerNeeded) {
                        String msg = "&c电力不足: 需要 " + powerNeeded + " J, 当前 " + charge + " J";
                        updateHologram(b, msg, blockData::isPendingRemove);
                        return;
                    }
                    // 只有在本次tick允许执行传输时才扣除电力，防止延迟tick重复扣费喵
                    if (canRun) {
                        deductCharge(powerNeeded);
                    }
                }

                // 喵~防御：没有任何活跃输入节点时，显示空闲状态全息图，不执行传输喵
                if (activeInputs.isEmpty()) {
                    updateHologram(b, "&7状态: &a&l已连接 &7(空闲)", blockData::isPendingRemove);
                    return;
                }

                // 喵~防御：如果本次tick因延迟配置不允许运行，直接跳过物品传输逻辑喵
                if (!canRun) {
                    return;
                }

                // 构造并触发CargoTickEvent事件，允许其他插件监听/拦截/修改货运行为喵
                var event = new CargoTickEvent(activeInputs, outputs);
                Bukkit.getPluginManager().callEvent(event);
                // 如果事件提供了自定义全息图消息，更新全息图显示喵
                event.getHologramMsg().ifPresent(msg -> updateHologram(b, msg));
                // 喵~防御：如果事件被取消（其他插件拦截），终止本次物品传输喵
                if (event.isCancelled()) {
                    return;
                }

                // 通知Slimefun性能分析器本次tick需要处理的条目数（活跃输入数+1个调度条目）喵
                Slimefun.getProfiler().scheduleEntries(activeInputs.size() + 1);
                // 创建并执行货运网络任务，真正执行物品从输入节点到输出节点的传输喵
                new CargoNetworkTask(this, activeInputs, outputs).run();
            });
        }
    }

    /**
     * 过滤出真正有物品可搬运的活跃输入节点喵~
     * 整体思路：遍历所有输入节点，检查其连接的容器是否有可取出的物品，只保留有物品的节点喵
     * 输入：inputs（所有输入节点的坐标→频道映射）；输出：只包含有物品节点的子集Map喵
     * 边界条件：节点连接的方块不存在或容器为空时，该节点会被过滤掉喵
     */
    // 私有辅助方法：从全部输入节点中筛选出连接了非空容器的活跃节点喵
    private @Nonnull Map<Location, Integer> filterActiveInputNodes(@Nonnull Map<Location, Integer> inputs) {
        // 创建存放活跃输入节点的结果Map喵
        Map<Location, Integer> active = new HashMap<>();
        // 遍历每个输入节点及其对应的频道编号喵
        for (Map.Entry<Location, Integer> entry : inputs.entrySet()) {
            // 取出当前输入节点的坐标喵
            Location inputLoc = entry.getKey();
            // 获取该输入节点旁边附着的容器方块（如箱子）喵
            Optional<Block> attached = getAttachedBlock(inputLoc);
            // 喵~防御：如果输入节点没有附着方块，则跳过此节点喵
            if (attached.isPresent()) {
                // 获取附着的目标方块（即物品来源容器）喵
                Block target = attached.get();
                // 检查目标容器中是否有可以抽取的物品喵
                if (hasItemsToTransfer(target)) {
                    // 容器非空，将该节点加入活跃列表喵
                    active.put(inputLoc, entry.getValue());
                }
            }
        }
        return active;
    }

    /**
     * 检查给定方块（容器）是否有可以被货运网络抽取的物品喵~
     * 整体思路：先尝试当作DirtyChestMenu（Slimefun自定义容器）处理，再尝试当作原版InventoryHolder处理喵
     * 输入：target方块；输出：true表示有可传输物品，false表示容器为空或不可访问喵
     * 边界条件：既不是DirtyChestMenu也不是InventoryHolder时返回false喵
     */
    // 私有辅助方法：判断目标方块（容器）中是否存在货运网络可以抽取的物品喵
    private boolean hasItemsToTransfer(@Nonnull Block target) {
        // 尝试将目标方块作为Slimefun的DirtyChestMenu（自定义菜单容器）处理喵
        DirtyChestMenu menu = CargoUtils.getChestMenu(target);
        // 如果目标方块是Slimefun自定义容器喵
        if (menu != null) {
            // 遍历允许被货运网络抽取（WITHDRAW）的所有槽位喵
            // 主人注意：当菜单槽位数量很多时此处遍历次数较多，但通常槽位数有限影响不大喵
            for (int slot : menu.getPreset().getSlotsAccessedByItemTransport(menu, ItemTransportFlow.WITHDRAW, null)) {
                // 喵~防御：只要找到一个非空槽位就立即返回true，避免不必要的全量遍历喵
                if (menu.getItemInSlot(slot) != null) {
                    return true;
                }
            }
            return false;
        }
        // 如果目标方块是原版InventoryHolder（如普通箱子、桶等）喵
        if (target.getState() instanceof InventoryHolder holder) {
            // 遍历原版库存中的所有格子喵
            for (ItemStack item : holder.getInventory().getContents()) {
                // 喵~防御：找到一个非null且非空气的物品时立即返回true喵
                if (item != null && !item.getType().isAir()) {
                    return true;
                }
            }
            return false;
        }
        // 目标方块既不是自定义容器也不是原版容器，无法传输物品喵
        return false;
    }

    /**
     * 将inputNodes集合转换为"坐标→频道编号"的映射表，只包含频道合法（0~15）的节点喵~
     * 整体思路：遍历所有已注册的输入节点，读取各节点的频道配置，过滤掉频道无效的节点喵
     * 输入：无（使用成员变量inputNodes）；输出：Map<Location, Integer>，坐标→频道喵
     * 边界条件：频道值不在[0,15]范围内的节点会被过滤掉（包括数据未加载时返回-1的情况）喵
     */
    // 私有辅助方法：将输入节点集合转换为坐标→频道映射，过滤掉非法频道节点喵
    private @Nonnull Map<Location, Integer> mapInputNodes() {
        // 创建存放有效输入节点的Map，键=坐标，值=频道编号喵
        Map<Location, Integer> inputs = new HashMap<>();

        // 遍历所有已注册的输入节点喵
        for (Location node : inputNodes) {
            // 读取该输入节点配置的频道编号（0~15有效，-1表示无效或未加载）喵
            int frequency = getFrequency(node);

            // 喵~防御：频道值不在有效范围[0,15]内时跳过，避免将非法节点加入传输流程喵
            if (frequency >= 0 && frequency < 16) {
                inputs.put(node, frequency);
            }
        }

        return inputs;
    }

    /**
     * 将outputNodes集合转换为"频道编号→位置列表"的映射表，用于确定每个频道有哪些输出目标喵~
     * 整体思路：遍历输出节点，按频道分组收集坐标，使用merge合并同频道的多个输出位置喵
     * 输入：无（使用成员变量outputNodes）；输出：Map<Integer, List<Location>>，频道→输出位置列表喵
     * 边界条件：频道为-1（无效/未加载）的节点直接跳过；同频道多节点会合并到同一List喵
     * 主人注意：此方法使用LinkedList存储，若输出节点极多（超过500）遍历性能会下降，建议监控喵
     */
    // 私有辅助方法：将输出节点集合按频道分组，构建频道→输出位置列表的映射喵
    private @Nonnull Map<Integer, List<Location>> mapOutputNodes() {
        // 创建存放频道→输出位置列表的结果Map喵
        Map<Integer, List<Location>> output = new HashMap<>();

        // 用于临时收集同一频道的输出节点坐标列表喵
        List<Location> list = new LinkedList<>();
        // 记录上一个处理的频道编号，初始为-1（无效值）喵
        int lastFrequency = -1;

        // 遍历所有已注册的输出节点喵
        for (Location node : outputNodes) {
            // 读取当前输出节点的频道编号喵
            int frequency = getFrequency(node);
            // 喵~防御：频道无效（-1）时跳过该节点，不加入任何频道的输出列表喵
            if (frequency == -1) {
                continue;
            }

            // 当频道切换到新频道时，将上一个频道已收集的节点列表合并到结果Map中喵
            if (frequency != lastFrequency && lastFrequency != -1) {
                // 使用merge方法：若该频道已有列表则追加，否则直接放入喵
                output.merge(lastFrequency, list, (prev, next) -> {
                    prev.addAll(next); // 将新收集的节点追加到已有列表喵
                    return prev;
                });

                // 重置临时列表，开始收集新频道的节点喵
                list = new LinkedList<>();
            }

            // 将当前节点坐标加入临时列表喵
            list.add(node);
            // 更新"上一个频道"记录喵
            lastFrequency = frequency;
        }

        // 喵~防御：遍历结束后若临时列表非空，将最后一个频道的节点也合并到结果Map喵
        if (!list.isEmpty()) {
            output.merge(lastFrequency, list, (prev, next) -> {
                prev.addAll(next); // 追加最后一批节点喵
                return prev;
            });
        }

        return output;
    }

    /**
     * 根据活跃输入节点计算本次tick需要消耗的总电力喵~
     * 计算公式：活跃频道数 × CHANNEL_COST + 活跃输入节点数 × INPUT_NODE_COST喵
     * 输入：activeInputs（活跃输入节点的坐标→频道Map）；输出：需要消耗的电力量（long，单位：J）喵
     */
    // 静态辅助方法：计算本次tick传输所需消耗的总电力，由频道数和输入节点数共同决定喵
    private static long calculatePowerNeeded(@Nonnull Map<Location, Integer> activeInputs) {
        // 提取所有活跃输入节点使用的频道编号集合（自动去重），用于统计活跃频道数喵
        Set<Integer> channels = new HashSet<>(activeInputs.values());
        // 总消耗 = 频道数×单频道费用 + 输入节点数×单节点费用喵
        return (long) channels.size() * CHANNEL_COST + (long) activeInputs.size() * INPUT_NODE_COST;
    }

    /**
     * This method returns the frequency a given node is set to.
     * Should there be invalid data this method it will fall back to zero in
     * order to preserve the integrity of the {@link CargoNet}.
     *
     * @param node
     *            The {@link Location} of our cargo node
     *
     * @return The frequency of the given node
     */
    /**
     * 读取货运节点上配置的频道编号喵~
     * 整体思路：从存储缓存中取出方块数据，读取"frequency"字段并解析为整数喵
     * 输入：节点坐标；输出：频道编号（0~15），数据不存在/未加载/格式错误时返回-1喵
     * 边界条件：数据未加载时触发异步加载并返回-1；频道值非数字时记录错误日志并返回-1喵
     */
    // 静态私有方法：读取指定坐标货运节点的频道编号，任何异常情况均返回-1喵
    private static int getFrequency(@Nonnull Location node) {
        // 从存储缓存中读取该坐标的方块数据喵
        var data = StorageCacheUtils.getBlock(node);
        // 喵~防御：方块数据为null说明该位置没有Slimefun方块，返回-1表示无效频道喵
        if (data == null) {
            return -1;
        }

        // 喵~防御：方块数据尚未从数据库加载完毕，触发异步加载并返回-1，等下次tick再读取喵
        if (!data.isDataLoaded()) {
            StorageCacheUtils.requestLoad(data);
            return -1;
        }

        // 读取该节点存储的"frequency"配置字符串喵
        String frequency = data.getData("frequency");

        // 喵~防御：频道数据为null说明从未配置过，返回-1表示无效频道喵
        if (frequency == null) {
            return -1;
        } else if (!CommonPatterns.NUMERIC.matcher(frequency).matches()) {
            // 喵~防御：频道数据不是纯数字（可能被损坏），记录严重错误日志并返回-1喵
            Slimefun.logger()
                    .log(
                            Level.SEVERE,
                            () -> "Failed to parse a Cargo Node Frequency ("
                                    + node.getWorld().getName()
                                    + " - "
                                    + node.getBlockX()
                                    + ','
                                    + node.getBlockY()
                                    + ','
                                    + node.getBlockZ()
                                    + "): "
                                    + frequency);
            return -1;
        } else {
            // 数据合法，将字符串解析为整数返回喵
            return Integer.parseInt(frequency);
        }
    }

    /**
     * 读取货运调节机当前的储能量喵~
     * 整体思路：从调节机的存储数据容器中读取"energy-charge"字段并解析为long喵
     * 输入：无（使用成员变量regulator定位调节机）；输出：当前储能量（long，单位：J），异常时返回0喵
     */
    // 私有方法：读取货运调节机的当前电量，用于判断是否有足够电力执行传输喵
    private long readCharge() {
        // 从存储缓存中获取调节机坐标的数据容器喵
        var data = StorageCacheUtils.getDataContainer(regulator);
        // 喵~防御：数据容器不存在、待移除或数据未加载时，视为电量为0，避免空指针喵
        if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
            return 0;
        }
        // 读取存储的电量字符串值喵
        String charge = data.getData("energy-charge");
        if (charge != null) {
            try {
                // 将字符串电量解析为long返回喵
                return Long.parseLong(charge);
            } catch (NumberFormatException e) {
                // 喵~防御：电量数据格式损坏无法解析时，返回0避免崩溃喵
                return 0;
            }
        }
        // 电量字段不存在时返回0喵
        return 0;
    }

    /**
     * 从货运调节机中扣除指定数量的电量喵~
     * 整体思路：读取当前电量，减去消耗量，结果不低于0，写回存储喵
     * 输入：要扣除的电量amount（long，单位：J）；无返回值喵
     * 边界条件：扣除后电量最小为0，不会出现负电量喵
     */
    // 私有方法：从调节机储能中扣除指定电量，执行物品传输后调用喵
    private void deductCharge(long amount) {
        // 获取调节机的数据容器喵
        var data = StorageCacheUtils.getDataContainer(regulator);
        // 喵~防御：数据容器不可用时直接返回，不执行任何操作喵
        if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
            return;
        }
        // 读取当前电量字符串喵
        String chargeStr = data.getData("energy-charge");
        // 当前电量值，默认为0喵
        long charge = 0;
        if (chargeStr != null) {
            try {
                // 解析当前电量喵
                charge = Long.parseLong(chargeStr);
            } catch (NumberFormatException e) {
                // 喵~防御：电量数据损坏无法解析时，将当前电量设为0再扣除喵
                charge = 0;
            }
        }
        // 计算扣除后的剩余电量，最低不低于0（避免出现负电量）喵
        long newCharge = Math.max(0, charge - amount);
        // 将新电量写回存储喵
        data.setData("energy-charge", String.valueOf(newCharge));
    }

    /**
     * 更新货运调节机存储的输入节点数量记录喵~
     * 每当节点分类变化时调用，确保调节机数据与实际inputNodes集合同步喵
     */
    // 私有方法：将当前输入节点数量写入调节机的存储数据，供外部查询使用喵
    private void updateCargoManagerInputCount() {
        // 获取调节机的方块数据喵
        var data = StorageCacheUtils.getBlock(regulator);
        // 喵~防御：调节机数据不存在时直接返回，避免空指针喵
        if (data == null) {
            return;
        }
        // 将当前inputNodes集合的大小（输入节点总数）写入"cargo-input-count"字段喵
        data.setData("cargo-input-count", String.valueOf(inputNodes.size()));
    }
}
