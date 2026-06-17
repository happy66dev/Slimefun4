package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import io.github.thebusybiscuit.slimefun4.api.geo.ResourceManager;
import io.github.thebusybiscuit.slimefun4.implementation.items.geo.GEOScanner;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * This {@link Event} is fired whenever a {@link GEOResource} is being freshly generated.
 * This only occurs when a {@link GEOScanner} queries the {@link Chunk} for a {@link GEOResource}
 * but cannot find it.
 *
 * You can modify this {@link Event} by listening to it.
 *
 * @author TheBusyBiscuit
 *
 * @see ResourceManager
 * @see GEOResource
 * @see GEOScanner
 *
 */
public class GEOResourceGenerationEvent extends Event {

    // 静态的Bukkit事件处理器列表，所有该事件类型实例共享同一个列表喵~
    private static final HandlerList handlers = new HandlerList();

    // 事件发生时所在的世界喵~
    private final World world;
    // 事件发生时所在位置的生物群系喵~
    private final Biome biome;
    // 正在生成的GEO资源类型喵~
    private final GEOResource resource;
    // 事件发生的区块X坐标（区块坐标，不是方块坐标哦）喵~
    private final int x;
    // 事件发生的区块Z坐标（区块坐标，不是方块坐标哦）喵~
    private final int z;

    // GEO资源的生成量，代表该资源的供给值喵~
    private int value;

    @ParametersAreNonnullByDefault
    public GEOResourceGenerationEvent(World world, Biome biome, int x, int z, GEOResource resource, int value) {
        // 记录事件发生的世界喵~
        this.world = world;
        // 记录事件发生位置的生物群系喵~
        this.biome = biome;
        // 记录正在生成的GEO资源类型喵~
        this.resource = resource;
        // 记录区块X坐标（区块坐标，不是方块坐标哦）喵~
        this.x = x;
        // 记录区块Z坐标（区块坐标，不是方块坐标哦）喵~
        this.z = z;

        // 记录资源的初始生成供给量喵~
        this.value = value;
    }

    /**
     * This returns the amount that will be generated of this {@link GEOResource}.
     *
     * @return The value aka the supply of this {@link GEOResource} to generate
     */
    public int getValue() {
        // 返回GEO资源的生成供给量喵~
        return value;
    }

    /**
     * This modifies the amount that will be generated.
     *
     * @param value The new supply for this {@link GEOResource}
     */
    public void setValue(int value) {
        // 喵~防御：GEO资源供给量不允许为负数，负数会导致资源系统内部状态混乱喵
        if (value < 0) {
            // 抛出非法参数异常，阻止设置负数的供给值喵~
            throw new IllegalArgumentException("You cannot set a GEO-Resource supply to a negative value.");
        }

        // 更新GEO资源的生成供给量为传入的新值喵~
        this.value = value;
    }

    /**
     * This returns the {@link World} in which this event takes place.
     *
     * @return The affected {@link World}
     */
    @Nonnull
    public World getWorld() {
        // 返回事件所在的世界实例喵~
        return world;
    }

    /**
     * This method returns the {@link GEOResource} that is being generated
     *
     * @return The generated {@link GEOResource}
     */
    @Nonnull
    public GEOResource getResource() {
        // 返回正在生成的GEO资源喵~
        return resource;
    }

    /**
     * This returns the X coordinate of the {@link Chunk} in which the {@link GEOResource}
     * is generated.
     *
     * @return The x value of this {@link Chunk}
     */
    public int getChunkX() {
        // 返回区块X坐标喵~
        return x;
    }

    /**
     * This returns the Z coordinate of the {@link Chunk} in which the {@link GEOResource}
     * is generated.
     *
     * @return The z value of this {@link Chunk}
     */
    public int getChunkZ() {
        // 返回区块Z坐标喵~
        return z;
    }

    /**
     * This method returns the {@link Environment} in which the resource is generated.
     * It is equivalent to {@link World#getEnvironment()}.
     *
     * @return The {@link Environment} of this generation
     */
    @Nonnull
    public Environment getEnvironment() {
        // 从世界实例获取维度环境类型（主世界/下界/末地）喵~
        return world.getEnvironment();
    }

    /**
     * This returns the {@link Biome} at the {@link Location} at which the {@link GEOResource} is
     * generated.
     *
     * @return The {@link Biome} of this generation
     */
    @Nonnull
    public Biome getBiome() {
        // 返回资源生成位置的生物群系类型喵~
        return biome;
    }

    // 返回静态的事件处理器列表，供Bukkit事件系统注册时使用喵~
    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    // 覆写父类方法，返回本事件类型的处理器列表，Bukkit事件系统会调用此方法获取监听器喵~
    public HandlerList getHandlers() {
        // 委托静态方法获取处理器列表喵~
        return getHandlerList();
    }
}
